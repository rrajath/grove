# Architecture

Grove is a single-module Android app organized into layered packages. The layers are held together by three invariants; almost every design choice in the codebase traces back to one of them:

1. **Files are the source of truth.** The `.org` files in the user's chosen folder are the only durable state. Anything Grove stores elsewhere (the Room index, parse caches) must be derivable from the files by re-reading them.
2. **Parsing is lossless.** An `OrgDocument` is a *view over* the raw text, not a replacement for it. Serializing a parsed document returns the original text unchanged. Edits are text splices, never re-renders of a model.
3. **External tools may touch the files at any time.** Syncthing, Emacs over SSH, a file manager — Grove detects changes by revision and never assumes it is the only writer.

## Layer map

```
┌──────────────────────────────────────────────────────────────┐
│ ui/            Compose screens · ViewModels · theme          │
│                (StateFlow in, user intents out)              │
├──────────────┬──────────────┬──────────────┬─────────────────┤
│ capture/     │ search/      │ sync/        │ settings/       │
│ templates,   │ query parse, │ engine,      │ DataStore       │
│ placeholders,│ match, rank, │ triggers,    │ preferences     │
│ insertion    │ snippets     │ conflicts    │                 │
├──────────────┴──────────────┼──────────────┴─────────────────┤
│ org/                        │ data/                          │
│ parser · mutations ·        │ Room index — a rebuildable     │
│ timestamps · line editing   │ cache over the vault           │
├─────────────────────────────┴────────────────────────────────┤
│ vault/    Vault facade → FileStore (SafFileStore | JvmFileStore)
└──────────────────────────────────────────────────────────────┘
```

Dependencies point downward only. `org/`, `capture/`, `search/`, and the `SyncEngine` are pure Kotlin with no Android imports, which is what makes the ~150-test JVM unit suite possible.

## The org engine (`org/`)

`OrgParser.parse(text)` produces an `OrgDocument`:

- `text` and `lines` — the verbatim file content. `serialize()` simply returns `text`.
- `headlines` — a flat, document-ordered list of `OrgHeadline` values. Each headline records its `lineIndex`, `level` (star count), parsed keyword/priority/title/tags, planning timestamps, properties drawer contents, and the line ranges of its own body (`bodyStart`..`contentEnd`).

Tree structure (children, subtrees, parents, inherited tags) is *computed* from the flat list by comparing levels, not stored. This keeps the model trivially consistent with the text.

All edits go through `OrgMutations` (move/delete/cut/paste subtrees, set keyword/priority/tags/planning, mark done with repeater advancement) or `CaptureInserter`. Both work the same way: compute a line range from the parsed view, splice new lines into the raw text, and re-parse. Lines outside the splice are preserved byte-for-byte.

Supporting pieces: `OrgTimestamp` (parse/format org timestamps incl. repeaters), `OrgKeywords` (configurable TODO/DONE keyword sets, `"TODO IN-PROGRESS | DONE CANCELLED"` syntax), `InlineTokenizer` and `BlockParser` (inline markup and block structure for rendering), and `LineEditing` (pure cursor-aware helpers for list continuation and heading demotion, shared by both editors).

## The vault (`vault/`)

`FileStore` is a small suspend interface (`list / read / write / create / rename / delete / exists`) over a **flat** directory of files. Two implementations:

- `SafFileStore` — production. Wraps a persisted Storage Access Framework tree URI. Listing is a single child-documents cursor query; writes use mode `"wt"` (write+truncate — plain `"w"` does not truncate on all providers); document-provider quirks (name mangling on create, providers that throw from rename/delete) are absorbed here.
- `JvmFileStore` — plain `java.io`, used by unit tests and reusable for a future direct-filesystem location.

`Vault` is the facade the rest of the app uses: it lists notebooks (filtering non-`.org` files, Syncthing conflict copies, and `.orgzlyignore` matches), opens files as parsed `OrgDocument`s with an `(name, mtime, size)`-keyed parse cache, and implements create/rename/save/trash. "Delete" is a soft delete: the file is renamed to `<name>.org.trash` (with a `-N` suffix if needed) so it leaves the notebook list but stays in the synced folder, recoverable from any device.

A file's **revision** is the string `"mtime:size"` — cheap to compute from a directory listing and good enough to detect external edits.

## Sync (`sync/` + `data/`)

For the v1 local-folder backend, "sync" means **re-indexing**: the files are already the truth (Syncthing moves the bytes), so Grove's job is noticing changes and keeping its index current.

- `SyncEngine` is the pure state machine: `Idle → Checking → Pulling → Done/Error`. It diffs current revisions against the index's known revisions, re-reads changed files into the index, removes vanished ones, and detects Syncthing `.sync-conflict-*` copies. It talks only to the `FileStore` and `NoteIndex` interfaces, so it is fully unit-tested; v2 remote backends (WebDAV/Dropbox) will implement `FileStore` against a remote and add an upload leg (the `Merging`/`Pushing` states exist for them).
- `RoomNoteIndex` implements `NoteIndex` over Room: each notebook file becomes a `NotebookEntity` (revision, top-level note count, conflict marker) plus one `NoteEntity` per headline (title, keyword, tags, inherited tags, planning timestamps, IDs, capped body text for search). The database is **never trusted as state** — it has destructive migrations and a "wipe and rebuild on next sync" recovery path by design.
- `SyncManager` is the Android-side orchestration: serializes runs behind a mutex, wires triggers (manual button, app foreground/background via `ProcessLifecycleOwner`, periodic `WorkManager` job ≥15 min, continuous 10 s polling while foregrounded), keeps a sync log in Room, posts conflict notifications, and implements conflict resolution (keep current / keep conflict copy / keep both — "both" demotes the conflict copy's content under a `* CONFLICT` heading).

## Capture (`capture/`)

A `CaptureTemplate` is `(name, icon, target file, target location, template text)`, persisted as JSON in DataStore; built-in defaults are seeded until the user first saves.

The pipeline at capture time:

1. `PlaceholderExpander.prompts()` finds `%^{prompt}` placeholders; the UI collects answers first.
2. `PlaceholderExpander.expand()` substitutes placeholders (`%t %T %u %U %date %time %day %year %month %clipboard %shared_text %shared_url` …) and records where `%cursor`/`%?` puts the caret. For date-granularity targets (datetree by date), `%U`/`%u` expand date-only.
3. `CaptureInserter.withHeadingStars()` prefixes the entry with the heading stars it will receive on insert (4 for a datetree entry, 1 for top/bottom of file) so the editor is WYSIWYG.
4. On save, `CaptureInserter.insert()` splices the entry into the target file: at top/bottom, under a heading found by `CUSTOM_ID` or exact title, or into a `year → month → day` datetree whose nodes are created on demand in chronological position. The first line is re-leveled to the insertion depth; body lines are kept verbatim.

Capture is reachable from the notebook-list FAB, a Glance home-screen widget, the share sheet (first URL → `%shared_url`, the rest → `%shared_text`), an optional ongoing notification, and the `grove://capture` deep link.

## Search (`search/`)

`QueryParser` parses the Orgzly-compatible syntax (see [search-syntax.md](search-syntax.md)) into a `SearchQuery`: an OR-list of AND-groups of `Condition`s plus sort and agenda directives. `QueryMatcher` evaluates conditions against indexed `NoteEntity` rows; `Snippets` produces highlighted excerpts.

Matching runs **in memory over the Room index** rather than SQLite FTS — simpler, fully unit-testable, and fast at v1 scale. The candidate-generation step is isolated so FTS can be swapped in later without changing the query API.

## UI (`ui/`)

Jetpack Compose throughout; no XML layouts and no WebViews.

- **Navigation**: a single `NavHost` (`Routes`) with `ModalNavigationDrawer`; deep links for `grove://note/{id}` and `grove://capture`. A note is addressed by `NoteRef` = `"file.org@headlineLineIndex"` until cross-file IDs land.
- **State**: ViewModels expose `StateFlow`s assembled with `combine` from the vault, the Room index, the sync manager, and settings. `GroveApplication` is the composition root — manual DI, app-scoped singletons wired with flows (e.g. changing the vault folder swaps the `FileStore`, which re-attaches the sync engine; changing TODO keywords clears and rebuilds the index).
- **Editor**: a `BasicTextField` with `OrgVisualTransformation` — highlight-only spans with `OffsetMapping.Identity`, so the text is never altered and cursor math stays trivial. The editor edits one note's subtree, not the whole file; saving splices the subtree back via `OrgMutations.replaceSubtree`, guarded by a revision check (stale-file banner → Overwrite or Reload).
- **Read mode**: a custom `AnnotatedString` renderer over the org AST (`BlockParser`/`InlineTokenizer`); tables render as monospace plain text in v1.
- **Theme**: the full design-token palette from `design/README.md` lives in `GroveColors` (light + dark), exposed as `MaterialTheme.grove` alongside the Material color scheme, including the `syn-*` org syntax tokens and the 6-color heading-star cycle.

## Threading

All file and database I/O is on `Dispatchers.IO` (the `FileStore` implementations switch internally); parsing and matching are cheap enough for default dispatchers; the UI thread only ever touches state flows. Sync runs are serialized behind a mutex so concurrent triggers can't interleave.

## Testing

The JVM unit suite (`app/src/test`) covers the pure core: parser round-trips over golden fixtures, mutations, timestamps, placeholder expansion, capture insertion (incl. datetrees), the sync engine against fake stores/indexes, query parsing/matching, line editing, and vault behavior over `JvmFileStore`. UI and SAF behavior are exercised manually / by instrumented tests.

One device-specific trap worth knowing: Android's ICU regex engine is stricter than the JVM's (e.g. a bare `}` outside a character class throws). Regexes must escape `}`/`]`, and JVM tests will not catch violations — see the comment in `PlaceholderExpander`.
