# Grove Performance Improvements

## Context

A three-agent audit of the codebase (UI/Compose layer, data/sync/parsing layer, dead-code sweep) surfaced a set of performance issues: per-keystroke full-vault search scans, whole-buffer editor re-highlighting, eager composition of entire notes in read mode, background flow collection, O(n²) algorithms in indexing/sync, per-call regex compilation, and six confirmed-dead symbols. The goal is a leaner, smoother app — especially typing latency in the editor, search responsiveness, first-frame time on long notes, and battery/CPU while backgrounded. User chose: **all tiers**, search fix via **cache + precomputed dates** (no FTS migration), editor fix via **per-line memoization**.

## High-impact fixes

### 1. Search: cache NoteMeta list + precompute dates
`app/src/main/java/com/rrajath/grove/ui/search/SearchViewModel.kt:98-142`, `app/src/main/java/com/rrajath/grove/search/QueryMatcher.kt:86-134`
- Currently every debounced query calls `indexDao().allNotes()` (`SELECT * FROM notes`), maps every row to `NoteMeta` (tag-string splits, `title + "\n" + body` concat), then linearly filters.
- Fix: cache the mapped `List<NoteMeta>` in the ViewModel, invalidated when the index changes (observe `notebooksFlow()` or a notes-table version signal); rebuild once per index change, not per keystroke.
- Add precomputed `LocalDate?` fields (`scheduledDate`, `deadlineDate`, `closedDate`, `createdDate`) to `NoteMeta`, parsed once during mapping. Update `QueryMatcher.filter`/`sort`/`agenda` to use them instead of re-running `OrgTimestamp.parse` per comparison / per day.
- Effect: search cost per keystroke drops from O(vault rows × mapping + regex parses) to a pure in-memory filter over prebuilt objects.

### 2. `collectAsState()` → `collectAsStateWithLifecycle()` (systemic, ~17 sites)
Add `androidx.lifecycle:lifecycle-runtime-compose` if not present; replace at:
`ui/GroveApp.kt:45,78,99,100` · `ui/screens/NotebooksScreen.kt:81` · `OutlineScreen.kt:82` · `ReadNoteScreen.kt:88` · `SettingsScreen.kt:77` · `SyncLogScreen.kt:46,47` · `ConflictScreen.kt:53` · `ui/search/SearchScreen.kt:71` · `ui/editor/EditNoteScreen.kt:88` · `ui/editor/MetadataSheet.kt:55` · `ui/capture/CaptureEditorScreen.kt:91,92,97` · `CapturePickerScreen.kt:44` · `TemplateEditScreen.kt:63`
- Effect: flow collection (including the eagerly-shared vault+Room+sync combines) pauses when the app is STOPPED — no background map/sort work or recompositions.

### 3. Editor: per-line memoized highlighting
`app/src/main/java/com/rrajath/grove/ui/editor/OrgVisualTransformation.kt:34-50`
- Currently any keystroke re-runs `styleLine` + `InlineTokenizer.tokenize` over every line of the buffer on the UI thread.
- Fix: cache per-line style results in a `HashMap<String, List<SpanStyleRange>>` (or line-content-keyed LRU) inside the transformation instance; on each `filter()`, split into lines and reuse cached spans for unchanged lines, re-tokenizing only changed ones. Keep the existing whole-result cache as the first short-circuit.
- Effect: keystroke cost drops from O(document) to O(changed line); eliminates typing jank on long notes. Benefits both `EditNoteScreen` and `CaptureEditorScreen` (they share this class).

### 4. Read view: LazyColumn + memoized traversals
`app/src/main/java/com/rrajath/grove/ui/screens/ReadNoteScreen.kt` (~lines 144-278, 400)
- Replace `Column + verticalScroll` in `NoteContent` with `LazyColumn` with stable `key` and `contentType` per block/child (keep `SelectionContainer` scoped per-item or accept per-block selection).
- Wrap O(document) computations in `remember(doc, headline)`: `inheritedTags` (line 205), `doc.bodyOf(...)` (line 250), `doc.subtree(headline)` + per-child `bodyOf` (lines 253-278).
- Hoist unstable lambdas into `remember`: `openLink` (line 187), `onLinkLongPress` (line 190), `openTarget` (BodyBlocks, line 401) — also fixes the latent stale-capture in the `annotateOrgInline` memo at line 316 (include the callback in behavior correctly).
- Effect: first-frame and memory scale with viewport, not note size; recompositions triggered by link-menu state no longer re-traverse the document.

### 5. Indexing: O(n) inherited tags
`app/src/main/java/com/rrajath/grove/data/RoomNoteIndex.kt:24-33`, `app/src/main/java/com/rrajath/grove/org/OrgParser.kt:84-101`
- `indexNotebook` calls `doc.inheritedTags(h)` per headline; each `parent()` scans backward → O(n²) per file.
- Fix: in `indexNotebook`, do one forward pass over `doc.headlines` maintaining an ancestor stack keyed by level; compute each headline's inherited tags from the stack in O(n) total. (Leave `OrgDocument.inheritedTags` for one-off UI use, or add an `inheritedTagsAll(): List<List<String>>` helper on `OrgDocument`.)
- Effect: indexing large files goes from quadratic to linear — faster sync and lower CPU.

### 6. Sync log: stop trimming per insert
`app/src/main/java/com/rrajath/grove/sync/SyncManager.kt:171-178`, `GroveDatabase.kt:138-139`
- Fix: trim once per sync run (e.g. after `Done`/`Error`), or every N inserts via a counter — not after every `insert`.
- Effect: removes a full-table `DELETE … NOT IN (SELECT …)` per log line during sync.

## Medium-impact fixes

### 7. SyncEngine: set-based diff + skip no-op conflict writes
`app/src/main/java/com/rrajath/grove/sync/SyncEngine.kt:78-101`
- Build `val changedNames = changed.mapTo(HashSet()) { it.name }` for the `it !in changed` check; only call `index.setConflict(name, value)` when the value differs from the stored `conflictFileName` (available from the already-fetched `known` data).
- Also compute `revision(entry)` once per entry into a map (currently rebuilt 2-3×, lines 78-88).

### 8. Conflict lookups: projection query
`app/src/main/java/com/rrajath/grove/sync/SyncManager.kt:90-99`
- Add `@Query("SELECT conflictFileName FROM notebooks WHERE fileName = :fileName") suspend fun conflictFileNameFor(fileName: String): String?` to `IndexDao`; use it in `conflictTexts` and `resolveConflict` instead of fetching all notebooks.
- Similarly make `knownRevisions()` a projection (`SELECT fileName, revision`) with a small DTO (`RoomNoteIndex.kt:13-14`, `GroveDatabase.kt:66-67`).

### 9. Hoist per-call regexes to constants
- `search/SearchQuery.kt:53` (`Period.pivot`), `org/OrgTimestamp.kt:93` (`parseWithRange`), `org/OrgMutations.kt:198,201` (`pasteUnder` map loop) → move each `Regex(...)` to a `private val` companion/top-level constant, matching the existing pattern elsewhere in the parsers.

### 10. Notebook list flow: isolate fast-changing sync state
`app/src/main/java/com/rrajath/grove/ui/vault/VaultViewModels.kt:51-82`
- `syncManager.state` emits per-file `Pulling(...)` ticks; each re-runs the full notebook map + sort. Split the combine: build the sorted list from vault/Room/settings only (with `distinctUntilChanged`), then combine the result with `syncManager.state`/`lastResult` downstream for the banner.

### 11. InlineTokenizer overlap check
`app/src/main/java/com/rrajath/grove/org/InlineTokenizer.kt:52-78`
- Replace `found.none { it.range.overlaps(m.range) }` (linear per match × 9 passes) with a coverage structure — e.g. keep `found` sorted by start and binary-search, or a claimed-ranges IntArray/BitSet over the line. Keep output order identical (verify against existing tokenizer tests).

### 12. Compose micro-fixes
- `ui/search/SearchScreen.kt:252` — add `key` to `HistoryList` items; `:287-300` — add `contentType` to `AgendaList` (header vs result); `:330-338` — wrap `ResultRow`'s `buildAnnotatedString` in `remember(result.snippet, c)`.
- `ui/screens/OutlineScreen.kt:98` — key `LaunchedEffect` on `notebookId` (or a loaded flag) instead of the whole `state`; `:124` — `remember(loaded.document)` the level-1 headline count.

## Cleanup tier

### 13. Delete confirmed-dead code (zero call-sites, verified by whole-word grep)
- `ui/theme/Type.kt:82` — `monoBody()`
- `org/OrgMutations.kt:34` — `setTitle()`
- `settings/SettingsRepository.kt:216` — `setNotebookMode()`
- `data/GroveDatabase.kt:73,76` — `IndexDao.noteByOrgId`, `noteByCustomId` (Room generates impl code for these)
- `ui/screens/SettingsScreen.kt:534` — private `PlaceholderRow`

### 14. Deduplicate `isPlanningLine`
Identical private fun in `org/OrgMutations.kt:256` and `org/OrgParser.kt:240` — hoist one shared `internal fun` into the `org` package (e.g. in `OrgParser.kt` or a small shared file) and delete the other.

### 15. Vault cache FIFO → LRU
`vault/Vault.kt:96` — construct the cache as `LinkedHashMap(initialCapacity, 0.75f, /* accessOrder = */ true)` (or `object : LinkedHashMap<...>(...) { override fun removeEldestEntry(...) = size > MAX }`) so eviction is least-recently-used.

### 16. Drop `androidx.compose.ui.tooling.preview`
`app/build.gradle.kts:153` — zero `@Preview` usages in source. Keep `debugImplementation ui.tooling` (line 176, layout inspector) and `profileinstaller` (line 169, commented as reserved for baseline profiles).

## Not doing (deliberately)
- FTS virtual table for search — deferred; the cache + precomputed dates fix covers current vault sizes without a schema migration.
- Viewport-windowed editor highlighting — per-line memoization chosen instead (simpler, no scroll-state plumbing).
- `SectionLabel` near-duplicate unification (DrawerContent vs SettingsScreen) — styling divergence may be intentional; skip.
- `Vault.notebooks()` full-parse count — verify it's not on a hot path first; only touch if it is.

## Verification
1. `./gradlew testDebugUnitTest` — full unit suite; parser/tokenizer/mutation tests must pass unchanged (especially after InlineTokenizer and inheritedTags rewrites — add a test asserting the O(n) inherited-tags pass matches `doc.inheritedTags` output on a nested fixture).
2. `./gradlew assembleDebug` and `./gradlew lintDebug`.
3. Manual on device/emulator (use /verify): type rapidly in a long note in the editor (no jank, highlighting correct per line); open a long note in read mode (scrolls, selection works, links open); search with tag/date queries and agenda view (results identical to before); run a sync and check the sync log still caps at 500 entries; background the app and confirm no sync-banner churn on return.
4. Behavior-parity spot checks: conflict resolve flow still works (projection query), notebook list ordering unchanged, per-notebook color/icon settings intact after dead-code removal.

## Suggested commit grouping (jj)
1. Dead code + regex hoists + small DB projections (items 7-9, 13-16) — mechanical, low risk.
2. Search cache + precomputed dates + QueryMatcher updates (items 1, 10).
3. Editor per-line memoization (item 3).
4. ReadNoteScreen LazyColumn + remember/lambda hoists (item 4) + Compose micro-fixes (item 12).
5. collectAsStateWithLifecycle sweep (item 2) + indexing O(n) pass + sync log trim (items 5-6).
