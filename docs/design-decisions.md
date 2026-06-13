# Design decisions

The reasoning behind Grove's load-bearing choices, including the options that were rejected. PRD §15 records the original decisions; this file extends them with what was learned during implementation.

## Files first, database never

**Decision:** `.org` files in the user's folder are the only durable state. Room is a rebuildable index with destructive migrations.

**Why:** This is the product's core promise — org users already own their files and their sync pipeline; an app-private database would make Grove a silo with an export feature. It also radically simplifies correctness: there is no two-way reconciliation between a database and files, no migration risk (the schema can change freely; the index just rebuilds), and "force reload" is a complete repair tool. The cost is that every screen-load re-derives state from text, which the parse cache and index keep cheap at v1 scale.

## A custom lossless parser instead of org-java

**Decision:** The PRD said to evaluate existing parsers first. Grove ended up with its own (`org/`), built around a *slice model*: `OrgDocument` keeps the verbatim text and a flat list of headline views indexing into it; `serialize()` returns the original text.

**Why:** Round-trip fidelity is non-negotiable for a file-first app — users will diff these files. Most parser libraries normalize on write (whitespace, drawer formatting, tag alignment), which shows up as noise in every Syncthing diff and git history. Making losslessness *structural* (edits are line splices into the original text; untouched lines can't change because they're never re-rendered) is simpler to guarantee than auditing a third-party serializer. The flat headline list with computed tree structure also keeps the model trivially consistent — there is no tree to desynchronize from the text.

**Trade-off:** Grove's parser covers the subset of org it needs (headlines, planning, drawers, timestamps, inline markup, lists) rather than all of org syntax. Unrecognized constructs pass through untouched, which the lossless model makes safe.

## SAF tree URI instead of raw file paths

**Decision:** The vault is accessed through the Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE` + persisted permission), abstracted behind `FileStore`.

**Why:** Scoped storage makes raw paths a dead end on modern Android, and SAF lets the user choose anything a document provider exposes. The cost is a quirky API surface — providers mangle names on create (`test.org` → `test.org.txt` unless created as octet-stream), `"w"` doesn't truncate everywhere (`"wt"` does), and rename/delete can throw rather than return null. All of these are absorbed inside `SafFileStore` so the rest of the app sees a sane filesystem. The `FileStore` interface keeps the core JVM-testable (`JvmFileStore`) and is the seam where v2 remote backends (WebDAV/Dropbox) plug in.

**Corollary — flat vault:** v1 treats the folder as flat (no subdirectories). It keeps listing to a single cursor query and sidesteps cross-directory identity questions.

## Sync = re-index; Syncthing moves the bytes

**Decision:** Grove does not implement file transport in v1. "Sync" detects changed files by revision (`mtime:size`) and re-indexes them; Syncthing (or any tool) replicates the folder between devices.

**Why (PRD §15):** Syncthing + local folder is peer-to-peer, has no account or quota, and org users commonly run it already. Building transport later is additive — `SyncEngine` talks only to `FileStore`/`NoteIndex` interfaces, and the `Merging`/`Pushing` states are reserved for remote backends. The revision heuristic (`mtime:size`) is deliberately cheap: it comes free with the directory listing, and the failure mode (same mtime *and* size after an external edit) is vanishingly rare for text files.

## Conflict picker, no auto-merge (v1)

**Decision:** When Syncthing leaves a `.sync-conflict-*` copy, Grove badges the notebook and offers keep-current / keep-copy / keep-both ("both" demotes the copy under a `* CONFLICT` heading). No structural merge.

**Why (PRD §15):** A wrong auto-merge silently loses or duplicates the user's words — worse than making them choose. Keep-both is the safety valve: nothing is ever destroyed, and the conflict is visible in the file itself (greppable from Emacs too). Structural merge is v2 backlog.

## Soft delete via `.trash` rename

**Decision:** Deleting a notebook renames `name.org` → `name.org.trash` (suffixed `-N` if taken), falling back to hard delete only if the provider refuses to rename.

**Why:** The vault is flat (no trash subdirectory) and synced — a rename keeps the bytes in the Syncthing folder, so a deletion made on the phone is recoverable from any device. The `-N` suffixing exists because the simple version failed in practice: deleting a recreated notebook hit the old trash file's name and the rename silently no-oped (the "delete doesn't work" bug).

## In-memory search over the index, not SQLite FTS

**Decision:** Queries parse to predicate trees and run in memory over indexed rows (bodies are cached, capped, in the `notes` table).

**Why:** At v1 scale (hundreds to low thousands of notes) linear matching is fast, fully unit-testable, and avoids FTS table/tokenizer maintenance and migration coupling. The matcher's candidate-generation step is isolated so FTS can replace it later without changing the query API. The query *syntax* is Orgzly's, deliberately — org users migrating from Orgzly keep their muscle memory and their saved searches.

## Subtree editing with highlight-only transformation

**Decision:** The editor edits one note's subtree (not the whole file) in a `BasicTextField`, with `OrgVisualTransformation` applying colored spans over unmodified text (`OffsetMapping.Identity`). Saving splices the subtree back with a revision guard.

**Why:** Editing the subtree bounds both the blast radius of a bad edit and the highlighting cost. The identity transformation means the visual text *is* the buffer text — no offset mapping bugs, trivial cursor math, and the styling layer can't corrupt content by construction. The revision guard (stale-file banner → Overwrite / Reload) exists because invariant #3 says Syncthing may rewrite the file mid-edit; silently last-write-wins would eat edits from the other device.

**Related:** read mode is a native `AnnotatedString` renderer, not a WebView — keeps theming/typography native and avoids shipping an HTML pipeline. Org tables render as monospace plain text in v1.

## Capture WYSIWYG: date-granularity expansion and star prefill

**Decision:** Two refinements over plain org-capture semantics. (1) When a template targets a date-granularity datetree, `%U`/`%u` expand date-only — the location setting, not the template text, decides whether a time appears. (2) The capture editor prefixes the entry with the heading stars it will receive on insert (4 under a datetree day, 1 at top/bottom of file), with the insertion re-leveling the first line on save regardless.

**Why:** Both fix the same dissonance: the editor showed something different from what landed in the file. The star prefill is cosmetic-but-honest — because `normalizeEntry` re-levels the first line anyway, the displayed stars can never produce a wrong file, but they let the user type a heading and sub-structure exactly as it will appear. Under-heading targets are *not* prefilled because the depth depends on the target heading, which isn't known until insert.

**Also (PRD §15):** under-heading targets prefer `CUSTOM_ID` over exact title match — titles get renamed; IDs survive.

## Notes = top-level headlines

**Decision:** A "note" is a top-level headline with its subtree; notebook counts only count level-1 headlines.

**Why:** Matches how org users think — subheadings are structure *within* a note, not more notes. (Originally every headline was counted, which made a 3-note journal with deep datetrees claim hundreds of notes.) Changing the semantics required a DB version bump so stale cached counts rebuild.

## Manual DI in `GroveApplication`

**Decision:** No Hilt/Koin. `GroveApplication` lazily constructs the singletons and wires them with flows (vault folder change → new `FileStore` → sync engine re-attach; keyword config change → index wipe + resync).

**Why:** The object graph is a dozen singletons with simple lifetimes; a DI framework would add build complexity and indirection without earning it at this size. The flow-based wiring keeps "settings changes propagate live" as ordinary, readable code.

## Pure-Kotlin core, Android at the edges

**Decision:** `org/`, `capture/`, `search/`, `SyncEngine`, and the editing helpers (`LineEditing`) have no Android imports. Android-specific behavior (SAF, WorkManager, notifications, Compose) lives at the boundaries.

**Why:** It's what makes the fast JVM test suite possible, and it has repeatedly paid off (e.g. list-continuation and heading-demotion logic are tested as plain `(text, cursor) → (text, cursor)` functions, free of Compose types). One caution this *doesn't* cover: Android's ICU regex engine is stricter than the JVM's, so a regex can pass every JVM test and still crash on device — escape `}` and `]` (see `PlaceholderExpander`).

## Design tokens as a parallel palette

**Decision:** The full design-spec palette lives in an immutable `GroveColors` (light + dark) exposed via `MaterialTheme.grove`, alongside — not inside — Material's `colorScheme`.

**Why:** The design language (earth tones, `syn-*` org syntax colors, soft chip backgrounds, the heading-star cycle) needs many more named roles than Material 3 defines. Stuffing them into `colorScheme` slots would scramble their meaning; a parallel composition-local keeps every usage site self-describing (`c.synTag`, `c.amberSoft`).
