# Indexing and Search: Current State

A read-through of how Grove indexes notes and answers searches, plus where the
current design can be improved. Written 2026-07-27 against the code on `main`.

> **Status:** items 1-4 were implemented the same day; see
> `docs/fts5-migration-plan.md` for what shipped and how it differs from the
> plan. The "Where it can be improved" section below describes the state
> *before* that work; items 5 and 6 are still open.

## Summary

- The index is a **persistent Room (SQLite) table**, one row per headline, holding
  all headline metadata (TODO keyword, priority, tags, inherited tags,
  SCHEDULED/DEADLINE/CLOSED, IDs, CREATED, capped body). It is a rebuildable cache,
  never the source of truth. The `.org` files on disk are authoritative.
- Indexing runs **during sync**, file-level incremental: only files whose
  `"mtime:size"` revision changed (or new/stub files) are re-parsed.
- Text search and every facet filter run **entirely in memory** in Kotlin, as a
  linear scan over the whole vault loaded into a `List<NoteMeta>`. There is **no
  FTS5** despite the PRD and code comments referring to it. No SQL query runs at
  search time beyond the initial full-table load.

## The persistent index

### Schema

`app/src/main/java/com/rrajath/grove/data/GroveDatabase.kt`

Two tables back the index:

- **`notebooks`** (`GroveDatabase.kt:20`): one row per `.org` file: `revision`
  (`"mtime:size"`), `noteCount`, `lastModified`, `conflictFileName`, cached
  `#+TITLE:`, and `isIndexed` (stub vs fully parsed).
- **`notes`** (`GroveDatabase.kt:40`): **one row per headline**, primary key
  `(fileName, lineIndex)`. Columns:

| Column | Meaning |
|---|---|
| `level`, `title` | heading depth and text |
| `keyword` | TODO/DONE state (nullable) |
| `isDone` | done-type flag, resolved against configured keywords at index time |
| `priority` | `[#A]` value as string |
| `tags` | own tags, `:`-joined |
| `inheritedTags` | own + ancestor + `#+FILETAGS:` tags, `:`-joined |
| `scheduled`, `deadline`, `closed` | raw org timestamp strings |
| `orgId`, `customId`, `createdAt` | `ID`, `CUSTOM_ID`, `CREATED` properties |
| `body` | heading body text, **capped at 4000 chars** (`RoomNoteIndex.kt:96`) |
| `lastModified` | mirror of the notebook mtime, for recency ranking |

There is no FTS5 virtual table. The `notes` table is a plain Room entity table
with no secondary indices.

Two other tables share the database but are not part of search: `sync_log` and
`reminders` (SCHEDULED/DEADLINE alarm state, rebuildable but carrying live
scheduling flags).

The whole database is disposable: `GroveDatabase.build` uses
`fallbackToDestructiveMigration(dropAllTables = true)` (`GroveDatabase.kt:248`),
so a schema bump drops the index and the next sync rebuilds it from disk.

### What gets extracted

`RoomNoteIndex.indexNotebook` (`RoomNoteIndex.kt:42`) parses a file with
`OrgParser.parse`, computes inherited tags for all headlines in one pass
(`doc.inheritedTagsAll()`), and maps each headline to a `NoteEntity`. `isDone` is
resolved here against the configured keyword set, so search never has to re-derive
it. The `#+TITLE:` preamble is cached on the notebook row so the notebook list
does not re-parse files just to show a title.

So TODO keywords, properties, tags (own and inherited), and SCHEDULED/DEADLINE are
all extracted once at index time and **persisted to disk**. They are not
recomputed from the raw file on every action.

### When indexing runs

Indexing is triggered by **sync**, not by user actions. `SyncEngine.diff`
(`SyncEngine.kt:90`):

1. Lists the vault, applies `.groveignore` and conflict-file filtering.
2. Computes each file's `"mtime:size"` revision.
3. Selects files to re-parse: new files, leftover stubs, or files whose revision
   changed (`SyncEngine.kt:107`).
4. Inserts stub rows for newly discovered files in one batch so the notebook list
   appears instantly, then parses each changed file, doing a transactional
   delete-then-insert of that file's note rows (`GroveDatabase.kt:153`).
5. Removes rows for deleted files.

Unchanged files are skipped entirely. Indexing is therefore **file-level
incremental**: edit one note and only that file is re-parsed.

## The in-memory search layer

### Loading

`SearchViewModel` (`SearchViewModel.kt:111`) subscribes to
`indexDao().allNotes()`, a Room `Flow` running `SELECT * FROM notes`. It maps
**the entire table** into a `List<NoteMeta>` held in a `MutableStateFlow`
(`SearchViewModel.kt:124`). This remap happens once per index change (Room
invalidation), not per keystroke. Each `NoteMeta` lazily caches its parsed
timestamps (`QueryMatcher.kt:39`) so repeated searches reuse them.

`AgendaViewModel` keeps its own cached `List<NoteMeta>` the same way
(`AgendaViewModel.kt:45`).

### Text search flow

`app/src/main/java/com/rrajath/grove/ui/search/SearchViewModel.kt`

1. Query text is debounced 300ms (`SearchViewModel.kt:141`).
2. `runSearch` runs on `Dispatchers.Default`, parses the raw string with
   `QueryParser.parse` into a `SearchQuery`, then calls
   `QueryMatcher.filter(notes, query, today)` (`SearchViewModel.kt:244`).
3. `QueryMatcher.filter` (`QueryMatcher.kt:51`) does a **linear scan over every
   note in the vault**, keeping those where `QueryMatcher.matches` is true, then
   sorts.
4. Plain text terms match via `note.searchText.contains(term, ignoreCase = true)`
   (`QueryMatcher.kt:57`), a case-insensitive substring scan over
   `title + "\n" + body`. No tokenization, stemming, or prefix index.
5. Ranking is done in Kotlin (`QueryMatcher.kt:102`): exact-title > title-contains
   > body match, tiebroken by `lastModified` descending. `o.PROP` tokens override
   with an explicit comparator chain.
6. Results are grouped by file into `SearchFileGroup`s for display.

### Filter flow

Filters come from two paths, both operating on the same in-memory
`List<NoteMeta>`:

- **Structured query tokens** (`i.TODO`, `t.work`, `s.3d`, `p.A`, etc.) parsed by
  `QueryParser` (`SearchQuery.kt:77`) into typed `Condition`s and matched by
  `QueryMatcher.matchesTerm` (`QueryMatcher.kt:54`): field equality for
  keyword/priority/tag, date-window math for scheduled/deadline/closed/created.
- **Faceted chips** (the Filters panel) applied as an extra predicate pass in
  `matchesFilters` (`SearchViewModel.kt:273`).

So "fetch all TODO headings" iterates the full note list and keeps rows where the
keyword matches. It never touches SQLite beyond the initial full-table load. The
Agenda screen is the same pattern via `QueryMatcher.agenda` (`QueryMatcher.kt:143`).

### What is cached

| Cached thing | Where | Invalidated when |
|---|---|---|
| Per-headline metadata | Room `notes` table | that file's revision changes |
| `isDone`, `#+TITLE:`, inherited tags | precomputed at index time | re-parse |
| Full `List<NoteMeta>` | Search/Agenda ViewModels | Room `notes` invalidation |
| Per-note parsed timestamps | lazy field in `NoteMeta` | new `NoteMeta` instance |
| Facet catalog + quick counts | recomputed per index change | Room invalidation |

## Where it can be improved

### 1. No FTS5 (the biggest gap)

The architecture docs and comments (`SearchQuery.kt:18` "for FTS narrowing")
promise FTS5, but text search is an in-memory substring scan over the whole vault,
re-run on each keystroke. For a large Emacs vault (thousands of headings) this
holds every note body in RAM and rescans all of them per query. An FTS5 virtual
table over `title + body` would give tokenized, ranked, prefix-capable search in
SQLite with far less memory. See `docs/fts5-migration-plan.md`.

### 2. Facet filters could be SQL

"All TODOs", tag, and priority filters are exact-match predicates that indexed SQL
columns handle natively. Even without FTS, letting Room filter
`WHERE keyword = 'TODO'` avoids loading and scanning the whole table for common
structured queries. Add indices on `keyword`, `isDone`, `scheduled`, `deadline`.

### 3. The 4000-char body cap silently truncates search

`MAX_BODY_CHARS = 4000` (`RoomNoteIndex.kt:96`) means body text past ~4000 chars
in a long note is unsearchable and absent from snippets, with no indication to the
user. FTS5 removes the reason for the cap. Short of that, surface or document it.

### 4. Whole-vault in memory does not scale

Two ViewModels (Search and Agenda) each hold a full `List<NoteMeta>` copy. Paging
or query-scoped loading would cut memory. Coupled to items 1 and 2: if search
moves to SQL, the in-memory list can shrink to result pages.

### 5. Tag matching is substring, not equality

`Condition.Tag` uses `pool.any { it.contains(c.tag) }` (`QueryMatcher.kt:68`), so
`t.work` also matches a `network` tag. Facet chips use `contains` too
(`SearchViewModel.kt:274`). This is documented behavior in `search-syntax.md`, but
worth reconsidering: exact (or exact-with-hierarchy) matching is usually what a
user expects.

### 6. Cached "today" does not refresh at midnight

Date-relative queries capture `LocalDate.now()` at query time (correct), but the
cached agenda and quick counts will not refresh across a midnight boundary while
the app stays open. Minor. A date-change trigger would fix it.

## Related documents

- `docs/fts5-migration-plan.md`: implementation plan for items 1-3.
- `docs/performance-improvements-plan.md`: the earlier decision to cache
  `NoteMeta` and precompute dates instead of migrating to FTS (now landed). This
  report and the FTS plan revisit that decision for large vaults.
- `docs/search-syntax.md`: user-facing query syntax.
- `docs/architecture.md`: overall layering.
