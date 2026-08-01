# FTS5 Migration Plan

Scopes the move from the current in-memory substring scan to a SQLite FTS5 full-text
index. Companion to `docs/indexing-and-search-report.md` (items 1-3). Written
2026-07-27.

## Goal

Replace the per-keystroke, whole-vault substring scan with an FTS5-backed
candidate lookup, so free-text search cost scales with the number of matching
notes rather than the size of the vault, and long-note bodies are no longer
truncated at 4000 chars for search.

## Non-goals

- Rewriting the structured facet engine (`i.` / `t.` / `p.` / date windows) into
  SQL. Facets stay as they are in Phase 1-2; SQL pushdown is an optional Phase 3.
- Changing user-facing query syntax (`docs/search-syntax.md`). Substring semantics
  must be preserved (see Decision 2).
- Touching the reminder or sync-log tables.

## Current baseline (what we are replacing)

- `notes` table: plain Room entity, `body` capped at 4000 chars
  (`RoomNoteIndex.kt:96`), no secondary indices.
- `SearchViewModel` loads the whole table into `List<NoteMeta>` and runs
  `QueryMatcher.filter` linearly (`SearchViewModel.kt:244`, `QueryMatcher.kt:51`).
- Free-text match is `searchText.contains(term, ignoreCase = true)` over
  `title + "\n" + body` (`QueryMatcher.kt:57`).

## Key decisions

### Decision 1: FTS4 (Room-native) vs FTS5 (manual)

Room's annotations support `@Fts3`/`@Fts4` but **not FTS5**. FTS5 must be created
with raw `CREATE VIRTUAL TABLE ... USING fts5(...)` and queried via `@RawQuery`
(Room will not compile-time-validate `@Query` against a table it does not own as
an entity).

| Option | Pros | Cons |
|---|---|---|
| **A. `@Fts4` external-content entity** | Room-native: auto-generated sync triggers, compile-time query validation, less hand-written SQL | FTS4 has no `trigram` tokenizer, so no substring/infix matching (behavior change); no built-in `bm25()` |
| **B. Manual FTS5 + `@RawQuery`** | `trigram` tokenizer preserves substring semantics; `bm25()` available; matches the PRD's stated "FTS5"; full control | Hand-maintained table (create in a `RoomDatabase.Callback`, write rows in `indexNotebook`); queries not compile-validated by Room |

**Recommendation: Option B.** Preserving substring matching (Decision 2) rules out
FTS4, and the maintenance burden is small because indexing already rewrites all of
a file's rows in one transaction, so the FTS write slots into the same code path.

### Decision 2: tokenizer (`trigram` vs `unicode61`/`porter`)

The current engine matches arbitrary substrings case-insensitively (`meet` matches
`committee`; `t.bee` matches `beeblebrox`). FTS default tokenizers match whole
tokens or prefixes only.

| Option | Pros | Cons |
|---|---|---|
| **A. `trigram` (`tokenize='trigram' case_sensitive 0`)** | Substring/infix matching, case-insensitive, works for CJK; near-parity with today's `contains` | Requires query fragments of >= 3 chars for infix (1-2 char terms need a fallback path); larger index |
| **B. `unicode61` + prefix indexes** | Smaller, faster index; natural word/prefix search; `bm25` ranking is meaningful | Behavior change: `meet` no longer matches `committee`; would need a migration note and syntax-doc update |

**Recommendation: Option A (`trigram`).** It keeps current behavior, which is
documented and which users rely on. minSdk 34 (Android 14) bundles SQLite new
enough for the trigram tokenizer. Handle 1-2 char text terms by skipping FTS
narrowing for that term and letting the in-memory matcher cover it (see
Decision 3).

### Decision 3: architecture (candidate narrowing vs full SQL rewrite)

| Option | Pros | Cons |
|---|---|---|
| **A. FTS narrows candidates, `QueryMatcher` still decides** | Lowest risk: matching semantics stay byte-for-byte identical; FTS only shrinks the set fed to the existing matcher; incremental | Still loads `NoteMeta` for the candidate set; full memory win deferred |
| **B. Full SQL rewrite of matching** | Smallest memory footprint; ranking/paging in SQL | Org date-window logic (raw timestamp strings) is painful in SQL; large rewrite of `QueryMatcher`; high regression risk |

**Recommendation: Option A.** FTS returns a candidate set of `(fileName, lineIndex)`
keys for the query's positive text terms; the ViewModel filters the in-memory metas
to those keys and runs the unchanged `QueryMatcher` over the reduced set. Structured
and negated terms keep their exact current semantics. Full SQL pushdown becomes an
optional later step once this is proven.

**Grouping caveat.** The query model is OR of AND-groups (`SearchQuery.groups`).
FTS narrowing is only valid when **every** AND-group contributes at least one
positive text term of >= 3 chars. If any group is facet-only (or has only
short/negated text terms), that group can match rows with no text hit, so narrowing
would drop valid results. Rule: narrow via FTS only when every group qualifies;
otherwise fall back to the current full-scan path for that query. This keeps
correctness unconditional and still wins on the common case (typing words).

### Decision 4: ranking (keep Kotlin ranking vs `bm25`)

**Recommendation: keep the existing Kotlin ranking** (`QueryMatcher.sort`,
exact-title > title-contains > body, recency tiebreak) in Phase 1-2. FTS is used
only to find candidates, not to order them, so ranking stays identical and
regression-free. Revisit `bm25()` as a later enhancement if desired.

### Decision 5: snippets

**Recommendation: keep `Snippets.build`** (`SearchViewModel.kt:313`). It already
produces highlight-term snippets from `searchText`; no need for FTS `snippet()` in
Phase 1. Removing the 4000-char body cap (below) means snippets can cover the full
body.

## Schema and migration

- Add a manually-created FTS5 table alongside the Room entities:
  ```sql
  CREATE VIRTUAL TABLE notes_fts USING fts5(
      fileName UNINDEXED,
      lineIndex UNINDEXED,
      title,
      body,
      tokenize = 'trigram case_sensitive 0'
  );
  ```
  `fileName`/`lineIndex` are stored `UNINDEXED` so a match can be mapped back to the
  `notes` row key. (Alternatively use FTS `rowid` = a stable encoding, but carrying
  the key columns is simpler given the composite primary key.)
- Create the table in a `RoomDatabase.Callback.onCreate` (and defensively verify in
  `onOpen`). Because the DB uses `fallbackToDestructiveMigration(dropAllTables =
  true)` (`GroveDatabase.kt:248`), **bump `version` to 7** and let the destructive
  path drop and rebuild everything on next sync. No hand-written `Migration` needed.
- Drop `MAX_BODY_CHARS` truncation (`RoomNoteIndex.kt:96`) so full bodies are
  indexed and searchable. Confirm memory/index-size impact during benchmarking; if
  a cap is still wanted, raise it substantially and document it.

## Index maintenance

Keep FTS in lockstep with the `notes` table inside the existing per-file
transaction (`IndexDao.replaceNotebook`, `GroveDatabase.kt:153`):

- On `deleteNotes(fileName)` also `DELETE FROM notes_fts WHERE fileName = :fileName`.
- On `insertNotes(...)` also insert the corresponding `notes_fts` rows (title +
  full body).
- Wire both into the same `@Transaction` so the FTS table can never drift from
  `notes`. Add the FTS deletes/inserts to `removeNotebook` and `clearAll` as well.

Since `replaceNotebook` already does delete-then-insert per file, this is additive:
two extra statements in the same transaction, no trigger machinery required.

## Query construction

New `IndexDao` `@RawQuery` (e.g. `noteKeysMatching(query: SupportSQLiteQuery):
List<NoteKey>` returning `(fileName, lineIndex)`), built from the parsed
`SearchQuery`:

- Take only positive `Condition.Text` terms of length >= 3.
- Escape each term for FTS (wrap in double quotes, double any embedded quotes) to
  neutralize FTS operators; trigram treats the quoted string as a substring probe.
- Combine terms within a group with `AND`, groups with `OR`, matching
  `SearchQuery`'s structure. Emit the MATCH string, e.g.
  `("meet" AND "notes") OR ("standup")`.
- Return early to the full-scan path when the narrowing precondition (Decision 3)
  is not met.

## Code changes (file by file)

1. `data/GroveDatabase.kt`
   - Bump `version` to 7.
   - Add a `RoomDatabase.Callback` in `build()` that runs the `CREATE VIRTUAL TABLE`
     (and an index-existence check on open).
   - Add `NoteKey` DTO and the `@RawQuery` `noteKeysMatching(...)`.
   - Add FTS delete/insert helpers and fold them into `replaceNotebook`,
     `removeNotebook`, `clearAll`.
2. `data/RoomNoteIndex.kt`
   - Remove `MAX_BODY_CHARS` truncation (or raise + document).
   - Pass full title/body through to the FTS write (via the DAO transaction).
3. `ui/search/SearchViewModel.kt`
   - In `runSearch`, when the parsed query qualifies for narrowing, call
     `noteKeysMatching(...)`, build a `Set<NoteKey>`, filter `notes` to it, then run
     the existing `QueryMatcher.filter`. Otherwise keep the current full path.
   - Leave catalog/quick-count computation unchanged (still needs the full list;
     memory reduction is out of scope here).
4. `ui/agenda/AgendaViewModel.kt`
   - No change (agenda is facet/date-driven, not free-text; it keeps the in-memory
     path).
5. `docs/search-syntax.md`
   - Only if any observable behavior changes (it should not, under Decision 2A). Add
     a line noting text search now covers full bodies, not the first 4000 chars.

## Phasing

- **Phase 1 (foundation):** FTS5 table, callback creation, maintenance in
  `replaceNotebook`/`removeNotebook`/`clearAll`, drop body cap, version bump. No
  read-path change yet. Verify the table populates correctly on a full sync.
- **Phase 2 (read path):** `noteKeysMatching` `@RawQuery`, MATCH builder with the
  grouping precondition, wire into `SearchViewModel.runSearch`. This is where the
  perf win lands.
- **Phase 3 (optional):** push facet-only queries to SQL `WHERE` with indices on
  `keyword`/`isDone`/`priority`, and consider paging to shrink the in-memory list.
  Addresses report item 4.

## Testing

- Unit: MATCH-builder tests (escaping, grouping precondition, >= 3-char rule,
  negation exclusion). Parity tests asserting FTS-narrowed results equal
  full-scan `QueryMatcher.filter` results across a fixture vault, including edge
  cases: 1-2 char terms, facet-only groups, mixed OR groups, negated text, CJK,
  and bodies longer than 4000 chars.
- Instrumented: index a fixture vault, confirm `notes_fts` row count matches
  `notes`, and that re-indexing a changed file leaves no stale FTS rows.
- Benchmark against a large realistic vault (thousands of headings) per
  `docs/benchmarking.md`: per-keystroke search latency and peak memory, before vs
  after. This is the go/no-go signal, since the current in-memory scan is adequate
  for small vaults.

## Risks and rollback

- **Trigram availability / behavior drift.** Guard with the parity tests above; if
  trigram proves unsuitable, Decision 2B (unicode61) is the fallback, with a
  syntax-doc update.
- **FTS/notes drift.** Prevented by co-transacting all writes; the parity and
  stale-row instrumented tests catch regressions.
- **Rollback is cheap.** The index is a disposable cache. Reverting the read-path
  change (Phase 2) restores the full-scan behavior immediately; the FTS table can
  be left in place or dropped via another destructive version bump.

---

## Outcome (implemented 2026-07-27)

All three phases landed. Where the implementation departed from the plan above,
it was for a reason worth recording.

### FTS5 is not in Android's platform SQLite

The plan assumed "minSdk 34 (Android 14) bundles SQLite new enough for the
trigram tokenizer". The SQLite *version* was never the problem: Android's
platform SQLite is compiled **without the FTS5 module at all**
(`no such module: fts5` on every API level). That is exactly why Room ships
`@Fts3`/`@Fts4` annotations and no `@Fts5`. Decision 1's framing ("FTS5 must be
created with raw SQL") was true but incomplete; raw SQL cannot conjure a module
that isn't compiled in.

The only way to get FTS5 on Android is to ship a SQLite that has it, so the
database now runs on `BundledSQLiteDriver` (`androidx.sqlite:sqlite-bundled`).
Cost: `libsqliteJni.so`, ~1.3 MB per ABI (~5 MB across the four ABIs in a
universal APK). Consider ABI splits or an App Bundle if that matters.

This was found by the instrumented tests, not by the compiler or the JVM suite:
the JVM tests have no SQLite at all, and `CREATE VIRTUAL TABLE` fails only at
runtime.

### Consequences of the driver switch

- `androidx.room.withTransaction` (the room-ktx extension) is Support-only and
  throws with a driver configured. Co-transacting the `notes` and `notes_fts`
  writes therefore happens where the plan originally wanted it: inside
  `IndexDao`'s own `@Transaction` methods. `IndexDao` became an abstract class
  so it can carry the `ftsAvailable` flag those methods branch on.
- `@RawQuery` takes `RoomRawQuery`, not `SupportSQLiteQuery`.
- FTS statements are ordinary `@Query` methods annotated
  `@SkipQueryVerification`, which is cleaner than the planned reach-around to
  `openHelper.writableDatabase`.

### Graceful degradation

`NotesFts.create` is guarded and returns whether the table is usable. If it ever
fails, `IndexDao.ftsAvailable` stays false, no FTS rows are written, no MATCH
expression is built, and search falls back to the full scan: correct, just
slower. The read path additionally catches `SQLException` and retries as a full
scan, so a malformed candidate query can never fail a search.

### Phase 3 went further than "facet-only queries"

Rather than a separate SQL path for facet-only queries, facets and text share
one candidate query, and the always-resident `List<NoteMeta>` is gone:
`SearchViewModel` keeps only a body-free `NoteFacetRow` projection for the
catalog and quick counts, and Agenda reads only rows that have a SCHEDULED or
DEADLINE. Report item 4 is addressed for both screens.

### Measured result

`SearchLatencyBenchmark`, 4000 notes / 40 files, emulator (directional only):

| query | full scan | narrowed | rows read | hits |
|---|---|---|---|---|
| `zebrafish` (rare term) | 1437 ms | **4 ms** | 16 | 16 |
| `i.TODO zebrafish` | 798 ms | **2 ms** | 6 | 6 |
| `t.alpha` (facet only) | 513 ms | **269 ms** | 2000 | 2000 |
| `meeting` (matches every note) | 694 ms | 789 ms | 4000 | 4000 |
| `meeting quarterly` (matches every note) | 577 ms | 694 ms | 4000 | 4000 |

Selective queries (what users actually type) are two to three orders of
magnitude cheaper and materialise a handful of rows instead of the whole vault.
The degenerate case, a term present in every single note, is ~15% slower: FTS
returns everything and the Kotlin matcher still has to examine it all, so the
index lookup is pure overhead. That trade is clearly worth taking.
