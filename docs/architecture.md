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
- `RoomNoteIndex` implements `NoteIndex` over Room: each notebook file becomes a `NotebookEntity` (revision, top-level note count, conflict marker) plus one `NoteEntity` per headline (title, keyword, tags, inherited tags, planning timestamps, IDs, full body text for search) and a mirrored row in the `notes_fts` FTS5 table. All of that happens inside one `@Transaction` per file, so the FTS mirror can never drift from the rows it indexes. The database is **never trusted as state** — it has destructive migrations and a "wipe and rebuild on next sync" recovery path by design.
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

**Matching is still decided in Kotlin; SQLite only narrows the candidates.** `FtsQuery` turns a parsed query's text terms into an FTS5 `MATCH` expression and `NoteCandidateQuery` turns its facets (`i.`/`p.`/`t.`/`b.`, and the Filters panel's chips) into a SQL `WHERE`. Both are built to return a **superset** of the real results, never a subset, so `QueryMatcher` and `matchesFilters` produce byte-identical output to scanning the whole vault. Anything that cannot be guaranteed a superset is simply not pushed down: terms shorter than a trigram, negated terms, date-window arithmetic over org timestamp strings, and non-ASCII operands (SQLite's `NOCASE`/`LIKE` fold only ASCII, Kotlin's `ignoreCase` folds all of Unicode). If *any* AND-group of a query has no usable text term, narrowing is abandoned for that query and the full scan runs — a facet-only group can match rows containing none of the query's text, so narrowing there would drop valid results. `FtsParityTest` pins this equivalence for every query shape.

Because the FTS5 module is absent from Android's platform SQLite (which is why Room only offers `@Fts3`/`@Fts4`), the database runs on `BundledSQLiteDriver` from `androidx.sqlite:sqlite-bundled`. The `notes_fts` virtual table is created by hand in a `RoomDatabase.Callback` and read through `@RawQuery`; if creation ever fails, `IndexDao.ftsAvailable` stays false and everything degrades to the full scan rather than breaking.

`SearchViewModel` no longer holds the whole vault in memory. It keeps only a `NoteFacetRow` projection — no titles, no bodies — to build the filter catalog and the blank-state quick counts, and loads full rows per search, scoped to the candidates. The Agenda screen likewise reads only rows that actually have a SCHEDULED or DEADLINE. `NoteMeta` lazily caches its parsed scheduled/deadline/closed/created dates; sorting and the agenda view use those instead of re-running the timestamp regex per comparison.

## Agenda (`ui/agenda/`)

`AgendaBuckets` owns the day model, deliberately as a pure object (no Android, no ViewModel) so the rules are JVM-testable — `AgendaViewModel` only wires settings and the index flow into it and maps the resulting buckets to rows. The model is stricter than the search one: **an item belongs to exactly one day — its SCHEDULED date if it has one, otherwise its DEADLINE.** That is what makes the "Group by · Date" sections disjoint. A heading with both dates appears once, on its scheduled day, carrying a red `⚑ <date>` chip that announces the deadline. "Overdue" follows the same rule: it is that one date being in the past, not either date being in the past. This replaced `QueryMatcher.agenda`, which bucketed a note under every day either of its dates touched.

The "Show" lever is `Open` · one chip per configured todo-type keyword · `Everything`. The middle chips come from `GroveApplication.keywords`, so the filter follows whatever `todoKeywords` is set to rather than assuming `NEXT`/`WAITING`; `Open` is state-agnostic (anything not done-type, headings with no keyword included). A persisted keyword filter whose keyword has since been removed from the config falls back to `Open` rather than silently matching nothing.

The levers (grouping, state filter, tags/source-file on rows) persist in `SettingsRepository` so they survive a cold start; the Today/Upcoming tab is navigational and resets each visit.

Agenda is also a mutation surface, not just a view: the row checkbox toggles done (`OrgMutations.markDone` / `reopen`), swipe gestures write planning dates, and the overdue card's "Move to today" rewrites every overdue heading at once. That last one edits several files, so the undo snapshot is a *list* of pre-mutation file texts rather than a single one. Within a file, headings are rewritten highest-line-first and the document re-parsed between edits, since adding a planning line shifts every line index below it.

## Reminders (`reminders/`)

A system notification fires when a heading's SCHEDULED or DEADLINE timestamp arrives, with **Complete** (flips the TODO keyword to the first done-type keyword) and **Reschedule** (deep-links back into the app to `PlanningDatesScreen`, focused on whichever of the two dates the reminder was for) actions.

- **Identity without file mutation.** A heading is tracked by a best-effort composite key (file name + ancestor-title path + own title + level) rather than an injected `CUSTOM_ID` — renaming or moving a heading can drop tracking for that one reminder, an accepted trade-off in exchange for never writing an ID property the user didn't ask for.
- **Pure core, thin Android shell.** `ReminderKeys` (key/notification-id derivation), `ReminderPlanning` (which reminders a parsed `OrgDocument` implies, and `triggerAtMillis` from a timestamp + the "Default reminder time" setting for date-only stamps), and `ReminderDiff` (schedule/cancel/unchanged diffing against previously stored reminders) are plain Kotlin, unit-tested like the rest of the core. `AlarmScheduler` (exact alarm via `setExactAndAllowWhileIdle`, falling back to inexact scheduling when exact-alarm access isn't granted or is revoked mid-flight), `ReminderNotification` (channel, actions, the `grove://reminder/...` deep-link URI), and `ReminderReconciler` (ties diffing to Room + `AlarmManager`) are the Android-facing layer.
- **Reconciliation hooks into indexing, not a separate scan.** `RoomNoteIndex.indexNotebook` calls back per file right after it's parsed, and `SyncManager` exposes `onNotebookIndexed`/`onSyncCompleted`, so reminders for an already-parsed notebook go live without waiting on the rest of the vault — consistent with the cold-open fix that decoupled instant file listing from background parsing.
- **Three receivers**, each holding `goAsync()` for the duration of its work since a `BroadcastReceiver`'s process can be killed the moment `onReceive` returns: `ReminderAlarmReceiver` (shows the notification when an alarm fires), `ReminderActionReceiver` (headless Complete — loads the file from the vault, mutates via `OrgMutations`, saves, triggers a sync; only clears the notification/alarm/row after a *confirmed* successful save, never on a best-effort attempt), `ReminderBootReceiver` (re-arms stored alarms and fires any that are overdue-and-unfired, filtering out reminders that already fired so a reboot doesn't re-notify for something the user already saw and dismissed).
- **Contextual permissions.** `POST_NOTIFICATIONS` and exact-alarm access are requested only when the first reminder actually needs scheduling, via a dismissible `ReminderPermissionBanner` (shown on Settings and Notebooks) rather than proactively at launch — reconciliation always computes and persists what *should* be scheduled regardless of permission state, and arms the actual `AlarmManager` calls once granted.

## UI (`ui/`)

Jetpack Compose throughout; no XML layouts and no WebViews.

- **Navigation**: a single `NavHost` (`Routes`) with `ModalNavigationDrawer`; deep links for `grove://note/{id}` and `grove://capture`. A note is addressed by `NoteRef` = `"file.org@headlineLineIndex"` until cross-file IDs land.
- **State**: ViewModels expose `StateFlow`s assembled with `combine` from the vault, the Room index, the sync manager, and settings. Screens collect with `collectAsStateWithLifecycle()`, so collection (and the upstream combine work) pauses while the app is backgrounded. Fast-ticking inputs (per-file sync progress) are combined downstream of the expensive list mapping/sorting, which sits behind `distinctUntilChanged`. `GroveApplication` is the composition root — manual DI, app-scoped singletons wired with flows (e.g. changing the vault folder swaps the `FileStore`, which re-attaches the sync engine; changing TODO keywords clears and rebuilds the index).
- **Editor**: a `BasicTextField` with `OrgVisualTransformation` — highlight-only spans with `OffsetMapping.Identity`, so the text is never altered and cursor math stays trivial. Highlighting is memoized per line content, so a keystroke re-tokenizes only the edited line rather than the whole buffer. The editor edits one note's subtree, not the whole file; saving splices the subtree back via `OrgMutations.replaceSubtree`, guarded by a revision check (stale-file banner → Overwrite or Reload).
- **Read mode**: a custom `AnnotatedString` renderer over the org AST (`BlockParser`/`InlineTokenizer`); the note subtree renders in a `LazyColumn` (one item per child heading) so long notes compose lazily, with document traversals memoized per document. Tables render as monospace plain text in v1. File-level `#+` keywords (document preamble) and `:PROPERTIES:` drawers render as faded, collapsible monospace key-value sections (`CollapsibleKvSection`), gated by the "Show preface" / "Show property drawers" Settings toggles — display-only; the `.org` file is never rewritten.
- **Theme**: the full design-token palette from `design/README.md` lives in `GroveColors` (light + dark), exposed as `MaterialTheme.grove` alongside the Material color scheme, including the `syn-*` org syntax tokens and the 6-color heading-star cycle.
- **Launcher icon sync** (`icon/AppIconManager`): "Sync App Icon with Theme" (Settings > Appearance) swaps the launcher icon by enabling exactly one `activity-alias` in `AndroidManifest.xml` (`.IconDefault` or one `.Icon<Theme>` per `ThemePreference`) and disabling the rest via `PackageManager.setComponentEnabledSetting`. Because switching the enabled alias closes the app's task, `GroveApp` defers `AppIconManager.applyIcon` to a lifecycle `ON_STOP` observer — the swap happens invisibly when the app goes to the background (hence the "restart the app" toast on enable). The alias `ComponentName`s are built from the fixed manifest package (`com.rrajath.grove`), not `context.packageName`, since the debug build's `applicationIdSuffix` would otherwise produce nonexistent class names. The drawer header logo (`BrandMark`) follows the same toggle: it stays the default light mark when off, and the active theme when on.

## Threading

All file and database I/O is on `Dispatchers.IO` (the `FileStore` implementations switch internally); parsing and matching are cheap enough for default dispatchers; the UI thread only ever touches state flows. Sync runs are serialized behind a mutex so concurrent triggers can't interleave.

## Testing

The JVM unit suite (`app/src/test`) covers the pure core: parser round-trips over golden fixtures, mutations, timestamps, placeholder expansion, capture insertion (incl. datetrees), the sync engine against fake stores/indexes, query parsing/matching, line editing, and vault behavior over `JvmFileStore`. UI and SAF behavior are exercised manually / by instrumented tests.

One device-specific trap worth knowing: Android's ICU regex engine is stricter than the JVM's (e.g. a bare `}` outside a character class throws). Regexes must escape `}`/`]`, and JVM tests will not catch violations — see the comment in `PlaceholderExpander`.
