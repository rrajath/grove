# Architecture

Grove is a single-module Android app organized into layered packages. The layers are held together by three invariants; almost every design choice in the codebase traces back to one of them:

1. **Files are the source of truth.** The `.org` files in the user's chosen folder are the only durable state. Anything Grove stores elsewhere (the Room index, parse caches) must be derivable from the files by re-reading them.
2. **Parsing is lossless.** An `OrgDocument` is a *view over* the raw text, not a replacement for it. Serializing a parsed document returns the original text unchanged. Edits are text splices, never re-renders of a model.
3. **External tools may touch the files at any time.** Syncthing, Emacs over SSH, a file manager: Grove detects changes by revision and never assumes it is the only writer.

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
│ parser · mutations ·        │ Room index: a rebuildable      │
│ timestamps · line editing   │ cache over the vault           │
├─────────────────────────────┴────────────────────────────────┤
│ vault/    Vault facade → FileStore (SafFileStore | JvmFileStore)
└──────────────────────────────────────────────────────────────┘
```

Dependencies point downward only. `org/`, `capture/`, `search/`, and the `SyncEngine` are pure Kotlin with no Android imports, which is what makes the ~150-test JVM unit suite possible.

## The org engine (`org/`)

`OrgParser.parse(text)` produces an `OrgDocument`:

- `text` and `lines`: the verbatim file content. `serialize()` simply returns `text`.
- `headlines`: a flat, document-ordered list of `OrgHeadline` values. Each headline records its `lineIndex`, `level` (star count), parsed keyword/priority/title/tags, planning timestamps, properties drawer contents, and the line ranges of its own body (`bodyStart`..`contentEnd`).

Tree structure (children, subtrees, parents, inherited tags) is *computed* from the flat list by comparing levels, not stored. This keeps the model trivially consistent with the text.

All edits go through `OrgMutations` (move/delete/cut/paste subtrees, set keyword/priority/tags/planning, mark done with repeater advancement) or `CaptureInserter`. Both work the same way: compute a line range from the parsed view, splice new lines into the raw text, and re-parse. Lines outside the splice are preserved byte-for-byte.

Supporting pieces: `OrgTimestamp` (parse/format org timestamps incl. repeaters), `OrgKeywords` (configurable TODO/DONE keyword sets, `"TODO IN-PROGRESS | DONE CANCELLED"` syntax), `InlineTokenizer` and `BlockParser` (inline markup and block structure for rendering), and `LineEditing` (pure cursor-aware helpers for list continuation and heading demotion, shared by both editors).

## The vault (`vault/`)

`FileStore` is a small suspend interface (`list / read / write / create / rename / delete / exists`) over a **tree** of files. Every path is vault-relative with `/` separators (`projects/clients/acme.org`); a root-level file's path is just its name. Two implementations:

- `SafFileStore`: production. Wraps a persisted Storage Access Framework tree URI. `list` walks every subdirectory breadth-first (one child-documents cursor query per directory), skipping dot-directories and Syncthing's `.stversions`/`.stfolder`; warm path→doc-id caches (files and directories) keep repeat resolves cheap. `create` walks and creates any missing intermediate directories; `rename` splits a same-directory rename from a cross-directory move (`DocumentsContract.moveDocument`, with a copy+delete fallback for providers that refuse it). Writes use mode `"wt"` (write+truncate; plain `"w"` does not truncate on all providers); document-provider quirks (name mangling on create, providers that throw from rename/delete) are absorbed here.
- `JvmFileStore`: plain `java.io` (`walkTopDown`, `mkdirs` on create/rename), used by unit tests and reusable for a future direct-filesystem location.

`Vault` is the facade the rest of the app uses: it lists notebooks recursively (filtering non-`.org` files, Syncthing conflict copies, and `.orgzlyignore` matches), opens files as parsed `OrgDocument`s with an `(name, mtime, size)`-keyed parse cache, and implements create (into any directory, creating missing folders), rename, `moveNotebook` (relocate keeping the file name), `renameFolder` / `trashFolder` (rename or trash a whole directory by moving/trashing every descendant `.org` file — empty directories are left on disk since the tree is path-derived), save, and trash. "Delete" is a soft delete: the file is renamed to `<name>.org.trash` (with a `-N` suffix if needed) so it leaves the notebook list but stays in the synced folder, recoverable from any device.

A notebook's **identity** is its vault-relative path — the key everywhere: the Room index (`fileName`), `notebookColors` / favorites / the pin list, and every nav route and deep link (`Routes.encode` percent-encodes `/` to `%2F`). Since a root-level file's path equals its bare name, keys for files that predate nested folders stay valid; only nested files introduce new keys. A **folder's** identity is its vault-relative directory path: `folderColors` in settings is keyed by it, and `SettingsRepository.renameFolderStyle` / `deleteFolderStyle` re-key or drop those entries (plus every descendant file's) alongside the `Vault` file moves.

Pinned notebooks and folders share **one ordered list**, `GroveSettings.pinnedItems` (DataStore key `pinned_items`): `;`-joined `<tag><path>` tokens where the tag is `f` (file) or `d` (folder). List order is the Notebooks screen's Pinned-strip order (append on pin, remove on unpin), so a folder pinned after a file stays below it. `pinnedNotebooks` / `pinnedFolders` remain as derived filtered accessors. A pinned folder is lifted out of the inline tree entirely (its whole subtree with it) and shown only in the strip; `pinnedFolderSubtreeRows` re-flattens one pinned folder's contents, depth-shifted, so the strip row can expand in place, while a pinned folder over the drill threshold still opens the full-screen drill view on tap. The two older per-type keys (`pinned_notebooks`, `pinned_folders`) are folded into the unified list on first read (folders then files, matching the old strip order) and purged on the next write; settings import also reads them from an older export. The unified list travels with settings import/export.

The `flatten_notebook_folders` setting (Settings → Look and Feel) swaps the inline tree for a single flat list: `NotebooksViewModel` takes a dedicated state branch that fills `flatRows` / `flatPinned` from `NotebookTree.flatNotebookRows` / `flatPinnedRows` (pure, path-grouped; a pinned folder expands to its files inline, de-duped against nested pins) instead of `rows` / `pinnedStrip`, and `NotebooksScreen` renders every entry as a `FileRow(flat = true, showPathSubtitle = true)` — no folder rows, drill view, or expand/collapse-all. It's a view toggle only: folder identity, moves, and the pin list are unchanged.

A file's **revision** is the string `"mtime:size"`: cheap to compute from a directory listing and good enough to detect external edits.

## Sync (`sync/` + `data/`)

For the v1 local-folder backend, "sync" means **re-indexing**: the files are already the truth (Syncthing moves the bytes), so Grove's job is noticing changes and keeping its index current.

- `SyncEngine` is the pure state machine: `Idle → Checking → Pulling → Done/Error`. It diffs current revisions against the index's known revisions, re-reads changed files into the index, removes vanished ones, and detects Syncthing `.sync-conflict-*` copies. It talks only to the `FileStore` and `NoteIndex` interfaces, so it is fully unit-tested; v2 remote backends (WebDAV/Dropbox) will implement `FileStore` against a remote and add an upload leg (the `Merging`/`Pushing` states exist for them).
- `RoomNoteIndex` implements `NoteIndex` over Room: each notebook file becomes a `NotebookEntity` (revision, top-level note count, conflict marker) plus one `NoteEntity` per headline (title, keyword, tags, inherited tags, planning timestamps, IDs, full body text for search) and a mirrored row in the `notes_fts` FTS5 table. All of that happens inside one `@Transaction` per file, so the FTS mirror can never drift from the rows it indexes. The database is **never trusted as state**: it has destructive migrations and a "wipe and rebuild on next sync" recovery path by design.
- `SyncManager` is the Android-side orchestration: serializes runs behind a mutex, wires triggers (manual button, app foreground/background via `ProcessLifecycleOwner`, periodic `WorkManager` job ≥15 min, continuous 10 s polling while foregrounded), keeps a sync log in Room, posts conflict notifications, and implements conflict resolution (keep current / keep conflict copy / keep both; "both" demotes the conflict copy's content under a `* CONFLICT` heading).
  - **Trigger coalescing** (`SyncCoalescer`): every `requestSync` goes through one `Channel(CONFLATED)` with a single consumer, so a burst of triggers arriving close together (a save next to a poll tick, foreground + poll-start in one frame) collapses into one full pass instead of running back to back; a 200 ms debounce lets an idle-time burst settle first. `requestReindex` (single-file save path) and `clearAndResync` (keyword-change wipe) keep their own paths but still share the mutex.
  - **Continuous-poll change detection**: the 10 s poll fingerprints the directory listing (`directoryFingerprint`, FNV-1a over each file's name/mtime/size) and only calls `requestSync` when the fingerprint moves, so an unchanged vault costs one cheap SAF listing per tick and nothing else.

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

**Matching is still decided in Kotlin; SQLite only narrows the candidates.** `FtsQuery` turns a parsed query's text terms into an FTS5 `MATCH` expression and `NoteCandidateQuery` turns its facets (`i.`/`p.`/`t.`/`b.`, and the Filters panel's chips) into a SQL `WHERE`. Both are built to return a **superset** of the real results, never a subset, so `QueryMatcher` and `matchesFilters` produce byte-identical output to scanning the whole vault. Anything that cannot be guaranteed a superset is simply not pushed down: terms shorter than a trigram, negated terms, date-window arithmetic over org timestamp strings, and non-ASCII operands (SQLite's `NOCASE`/`LIKE` fold only ASCII, Kotlin's `ignoreCase` folds all of Unicode). If *any* AND-group of a query has no usable text term, narrowing is abandoned for that query and the full scan runs: a facet-only group can match rows containing none of the query's text, so narrowing there would drop valid results. `FtsParityTest` pins this equivalence for every query shape.

Because the FTS5 module is absent from Android's platform SQLite (which is why Room only offers `@Fts3`/`@Fts4`), the database runs on `BundledSQLiteDriver` from `androidx.sqlite:sqlite-bundled`. The `notes_fts` virtual table is created by hand in a `RoomDatabase.Callback` and read through `@RawQuery`; if creation ever fails, `IndexDao.ftsAvailable` stays false and everything degrades to the full scan rather than breaking.

`SearchViewModel` no longer holds the whole vault in memory. It keeps only a `NoteFacetRow` projection (no titles, no bodies) to build the filter catalog and the blank-state quick counts, and loads full rows per search, scoped to the candidates. The Agenda screen likewise reads only rows that actually have a SCHEDULED or DEADLINE. `NoteMeta` lazily caches its parsed scheduled/deadline/closed/created dates; sorting and the agenda view use those instead of re-running the timestamp regex per comparison.

## Agenda (`ui/agenda/`)

`AgendaBuckets` owns the day model, deliberately as a pure object (no Android, no ViewModel) so the rules are JVM-testable: `AgendaViewModel` only wires settings and the index flow into it and maps the resulting buckets to rows. The model is stricter than the search one: **an item belongs to exactly one day (its SCHEDULED date if it has one, otherwise its DEADLINE).** That is what makes the "Group by · Date" sections disjoint. A heading with both dates appears once, on its scheduled day, carrying a red `⚑ <date>` chip that announces the deadline. "Overdue" follows the same rule: it is that one date being in the past, not either date being in the past. This replaced `QueryMatcher.agenda`, which bucketed a note under every day either of its dates touched.

The "Show" lever is `Open` · one chip per configured todo-type keyword · `Everything`. The middle chips come from `GroveApplication.keywords`, so the filter follows whatever `todoKeywords` is set to rather than assuming `NEXT`/`WAITING`; `Open` is state-agnostic (anything not done-type, headings with no keyword included). A persisted keyword filter whose keyword has since been removed from the config falls back to `Open` rather than silently matching nothing.

The levers (grouping, state filter, tags/source-file on rows) persist in `SettingsRepository` so they survive a cold start; the Today/Upcoming tab is navigational and resets each visit.

Agenda is also a mutation surface, not just a view: the row checkbox toggles done (`OrgMutations.markDone` / `reopen`), swipe gestures write planning dates, and the overdue card's "Move to today" rewrites every overdue heading at once. That last one edits several files, so the undo snapshot is a *list* of pre-mutation file texts rather than a single one. Within a file, headings are rewritten highest-line-first and the document re-parsed between edits, since adding a planning line shifts every line index below it.

## Home-screen widgets (`widget/`)

Two Glance (`androidx.glance.appwidget`) widgets, both RemoteViews under the hood
so they render outside the app's own process/lifecycle:

- **Capture** (`CaptureWidget`/`CaptureWidgetReceiver`): one tap, opens the
  capture picker via the `grove://capture` deep link. No state of its own.
- **Agenda ledger** (`LedgerWidget`/`LedgerWidgetReceiver`): the "Widget A ·
  ledger" prototype variant, re-grouped by day (`LedgerBuckets`, pure/JVM-tested
  like `AgendaBuckets`) instead of priority — an always-first unbounded Overdue
  section, then one section per day out to a 14-day window, empty days omitted.
  Row construction is shared with the Agenda screen (`AgendaViewModel.row`), so
  meta chips/tags/priority match exactly. See `docs/DESIGN_SYSTEM.md`'s "Ledger
  widget" entry for the full visual spec and the Glance-platform limits it runs
  into (no border modifier, no backdrop blur, no real text-layout metrics —
  the checkbox/keyword-pill vertical centering against just the title's first
  line is a fixed-height-box approximation, not a measured one). The header's
  icon tile is the actual theme-synced app icon: `AppIconManager.mipmapRes`
  (new, alongside the existing `markColor`/`targetAlias` per-theme maps) picks
  the same per-theme adaptive-icon mipmap "Sync App Icon with Theme" swaps the
  launcher to.

  `provideGlance` collects the settings and index Flows **inside**
  `provideContent`'s composition (`collectAsState`, seeded with a `first()` read
  so the first frame after a cold session start is already correct). That
  placement is load-bearing, not stylistic: while a Glance session is alive,
  `updateAll()` only *recomposes* the existing content — it never re-invokes
  `provideGlance`. An earlier version read the index into local variables before
  `provideContent` and relied on `updateAll()` to refresh; because recomposition
  replayed the same captured snapshot, the ledger redrew identical rows forever
  and only a brand-new session (app relaunch, host rebind) ever showed an edit.
  That is what made widget mark-done look like a no-op even though the `.org`
  write and the Room reindex had both succeeded. Hoisting these reads back out
  of the composition will silently reintroduce that bug.

  Because the Flows are collected in-composition, any vault write that reaches
  Room now pushes the widget an update on its own. The explicit
  `LedgerWidget().updateAll(context)` calls — from the row checkbox's
  `MarkDoneAction` (an `ActionCallback`, same "mark done" semantics as
  `AgendaViewModel.toggleDone` — recurring headings advance their repeater
  instead of gaining a done keyword and are therefore never auto-archived; a
  non-recurring done item is auto-archived when Settings has it enabled — but
  with no undo), the quick-add composer's send action, and `SyncManager`'s
  `onSyncCompleted` hook — are kept as a nudge for the case where *no* session
  is running, which is the one situation where `updateAll()` does start one and
  re-run `provideGlance`. Tapping a row's body opens Read mode via the same
  `grove://note/{id}?mode=read` deep link the rest of the app uses (`NoteRef` +
  `Routes.encode`).

  `MarkDoneAction` logs a warning under the `GroveWidget` tag at each of its
  early-return guards (vault never loaded, file missing, no headline at the
  indexed line, no done keyword configured). Those bails are individually
  legitimate but were previously silent, which is what made this class of bug
  so hard to tell apart from "the tap never fired at all".

  Glance dispatches the tap by resolving the callback's *class name* out of the
  intent and calling `getDeclaredConstructor().newInstance()`. Glance's consumer
  ProGuard rule keeps `ActionCallback` implementations as classes but says
  nothing about their members, so R8 shrinks the no-arg constructor away as
  unreachable and the reflection throws — the class ships, the tap does nothing,
  and none of the guard logs above ever run. `app/proguard-rules.pro` therefore
  keeps `<init>()` on every `ActionCallback`. This failure mode is release-only
  (debug builds don't run R8), so a widget action that works from Android Studio
  can still be inert in a production build of the same commit; any new
  `ActionCallback` is covered by the existing rule, but verify a release build,
  not just a debug one.

  The "+" button opens `WidgetQuickAddActivity`, not the full Capture Picker:
  a small transient overlay on the same one-shot-errand pattern as
  `ui/reminders/RescheduleActivity` (empty `taskAffinity`, transparent window,
  excluded from recents), reproducing the prototype's compact `wCapOpen`
  composer instead of the multi-step template flow. The text field auto-focuses
  and shows the keyboard immediately; since a translucent activity's
  `windowSoftInputMode="adjustResize"` doesn't reliably resize the window, the
  activity instead calls `WindowCompat.setDecorFitsSystemWindows(window, false)`
  and the sheet rides the IME inset itself via `Modifier.imePadding()`. The
  notebook chip's default is Settings § Sharing's "Shared content target"
  (`GroveSettings.shareTargetFile`), resolved the same way
  `AppViewModel.consumeSharedContent` resolves it for a shared link — not any
  capture template's target file, since the composer never touches the
  template system. Sending appends a plain top-level heading to the bottom of
  the chosen notebook via `CaptureInserter.insert` +
  `OrgMutations.setPriority`/`setScheduled` — the same on-disk formatting path
  every other capture surface writes through, just without template
  placeholders/datetree targets, which this composer has no UI for.

## Reminders (`reminders/`)

A system notification fires when a heading's SCHEDULED or DEADLINE timestamp arrives, with **Complete** (flips the TODO keyword to the first done-type keyword) and **Reschedule** (opens `PlanningDatesScreen`, focused on whichever of the two dates the reminder was for) actions. Tapping the notification body instead deep-links into the app (`grove://reminder/...` → `ReminderResolveScreen` → the note in read mode).

- **Identity without file mutation.** A heading is tracked by a best-effort composite key (file name + ancestor-title path + own title + level) rather than an injected `CUSTOM_ID`: renaming or moving a heading can drop tracking for that one reminder, an accepted trade-off in exchange for never writing an ID property the user didn't ask for.
- **Pure core, thin Android shell.** `ReminderKeys` (key/notification-id derivation), `ReminderPlanning` (which reminders a parsed `OrgDocument` implies, and `triggerAtMillis` from a timestamp + the "Default reminder time" setting for date-only stamps; also stamps `ReminderEntity.hasExplicitTime = ts.time != null`), `ReminderDiff` (schedule/cancel/unchanged diffing against previously stored reminders), and `ReminderDigest` (the daily digest count) are plain Kotlin, unit-tested like the rest of the core. `AlarmScheduler` (exact alarm via `setExactAndAllowWhileIdle`, falling back to inexact scheduling when exact-alarm access isn't granted or is revoked mid-flight), `ReminderNotification` (channel, actions, the `grove://reminder/...` deep-link URI, and the digest notification), and `ReminderReconciler` (ties diffing to Room + `AlarmManager`) are the Android-facing layer.
- **Reconciliation hooks into indexing, not a separate scan.** `RoomNoteIndex.indexNotebook` calls back per file right after it's parsed, and `SyncManager` exposes `onNotebookIndexed`/`onSyncCompleted`, so reminders for an already-parsed notebook go live without waiting on the rest of the vault, consistent with the cold-open fix that decoupled instant file listing from background parsing.
- **Daily digest instead of a flood of date-only pings.** A heading's SCHEDULED/DEADLINE stamp with no time-of-day falls back to the "Default reminder time" setting for its `triggerAtMillis` (`ReminderPlanning`), but no longer shows its own notification when that alarm fires (`ReminderAlarmReceiver`/`ReminderReconciler.notify` both check `hasExplicitTime` first). Instead, `ReminderDigestScheduler` arms one independent, self-rescheduling `AlarmManager` alarm at the default reminder time; `ReminderDigestReceiver` fires it, computes `ReminderDigest.count` (overdue + scheduled-today + deadline-today, summed across *every* reminder regardless of `hasExplicitTime`, so a task with an explicit time still shows its own ping *and* is counted here), and shows one "You have X tasks due today" notification (skipped entirely when X is 0) that deep-links to `grove://agenda`. Explicit-time reminders are unaffected and keep firing individually at their own time.
- **Four receivers**, each holding `goAsync()` for the duration of its work since a `BroadcastReceiver`'s process can be killed the moment `onReceive` returns: `ReminderAlarmReceiver` (shows the notification when an alarm fires, unless the reminder is date-only), `ReminderDigestReceiver` (the daily digest, above), `ReminderActionReceiver` (headless Complete: loads the file from the vault, mutates via `OrgMutations`, saves, triggers a sync; only clears the notification/alarm/row after a *confirmed* successful save, never on a best-effort attempt), `ReminderBootReceiver` (re-arms stored alarms and the digest alarm, and fires any per-heading reminders that are overdue-and-unfired, filtering out reminders that already fired so a reboot doesn't re-notify for something the user already saw and dismissed).
- **Contextual permissions.** `POST_NOTIFICATIONS` and exact-alarm access are requested only when the first reminder actually needs scheduling, via a dismissible `ReminderPermissionBanner` (shown on Settings and Notebooks) rather than proactively at launch; reconciliation always computes and persists what *should* be scheduled regardless of permission state, and arms the actual `AlarmManager` calls once granted.
- **Reschedule is an errand, not a destination.** "Reschedule" starts `ui/reminders/RescheduleActivity`, a second activity with an empty `taskAffinity` (its own task, excluded from recents, transparent window) rather than a route inside `MainActivity`. The user reached the shade from some other app, so confirming a date finishes this activity and drops them straight back into that app; Grove's own task is neither launched nor disturbed. The write is headless in the same sense as `ReminderActionReceiver` (re-read the file, re-locate the heading by its composite key, `OrgMutations.setPlanningDates`, save, request a sync) and runs on `GroveApplication.appScope` so finishing the window the instant the user taps Apply cannot cancel a write still in flight. The reminder row and alarm are left for `ReminderReconciler` to re-derive from the re-indexed file.

## UI (`ui/`)

Jetpack Compose throughout; no XML layouts and no WebViews.

- **Navigation**: a single `NavHost` (`Routes`) with `ModalNavigationDrawer`; deep links for `grove://note/{id}` and `grove://capture`. A note is addressed by `NoteRef` = `"path/to/file.org@headlineLineIndex"` (the file part is the vault-relative path; `NoteRef.decode` splits on the last `@`, and `Routes.encode` percent-encodes the `/`s) until cross-file IDs land. A file's preamble (the free text/`#+` keywords before its first heading) has no headline to key off of. Its **editor** gets its own route, `preface/{fileName}` (`Routes.preface`); its **read view** rides the normal `note/{id}` route with a sentinel `NoteRef.lineIndex == PREFACE_LINE_INDEX` (`-1`, `NoteRef.isPreface` / `NoteRef.preface(file)`), which also flows unchanged through search results and the index (one FTS row per preface, keyed at `-1`). A single metadata **drawer** (file-level `:PROPERTIES:`, or a heading's `:PROPERTIES:` / `:LOGBOOK:`) is editable in isolation through `drawer/{fileName}?kind={kind}&noteId={noteId}` (`Routes.drawer`), which renders the same editor screen.
- **State**: ViewModels expose `StateFlow`s assembled with `combine` from the vault, the Room index, the sync manager, and settings. Screens collect with `collectAsStateWithLifecycle()`, so collection (and the upstream combine work) pauses while the app is backgrounded. Fast-ticking inputs (per-file sync progress) are combined downstream of the expensive list mapping/sorting, which sits behind `distinctUntilChanged`. `GroveApplication` is the composition root: manual DI, app-scoped singletons wired with flows (e.g. changing the vault folder swaps the `FileStore`, which re-attaches the sync engine; changing TODO keywords clears and rebuilds the index).
- **Compose stability**: the list-bearing UI state classes (`SearchUiState`, `AgendaUiState`, `EditorUiState`, and their nested holders) use `kotlinx.collections.immutable` `ImmutableList` fields rather than plain `List`, and small value holders are `@Immutable`, so a screen re-collecting an unchanged state can skip recomposition. Domain value types the compiler can't verify (`OrgKeywords`, `OrgTimestamp`, `ArchiveTarget`, `java.time.*`) are declared stable in `app/compose_stability.conf`.
- **Editor**: a `BasicTextField` with `OrgVisualTransformation`, highlight-only spans with `OffsetMapping.Identity`, so the text is never altered and cursor math stays trivial. Highlighting is memoized per line content, so a keystroke re-tokenizes only the edited line rather than the whole buffer. `EditNoteScreen` edits one note's subtree, not the whole file; saving splices the subtree back via `OrgMutations.replaceSubtree`, guarded by a revision check (stale-file banner → Overwrite or Reload). `EditRegionScreen` is the same pattern scoped to one region of a file instead of a subtree (`EditorViewModel.loadRegion` / `EditorUiState.region`): a smaller sibling with no heading, so no Read/Edit toggle or metadata sheet. `EditRegion.PREFACE` splices via `OrgMutations.prefaceText` / `replacePreface`; the three drawer regions (`FILE_PROPERTIES`, `HEADING_PROPERTIES`, `HEADING_LOGBOOK`) via `OrgMutations.fileDrawerRange` / `headingDrawerRange` + `replaceLines`, recomputing the line range from the fresh file on save. It opens via double-tapping the matching `CollapsibleKvSection` / `CollapsibleLogSection` (`doubleTapToEdit`, same gesture and timing as double-tapping any other note text into edit mode). `EditPrefaceScreen` remains as a thin `EditRegion.PREFACE` wrapper.
- **Read mode**: a custom `AnnotatedString` renderer over the org AST (`BlockParser`/`InlineTokenizer`); the note subtree renders in a `LazyColumn` (one item per visible child heading) so long notes compose lazily, with document traversals memoized per document and body lines resolved per row. A note whose subtree exceeds `LARGE_SUBTREE_THRESHOLD` (60 headings) opens with its inner headings folded (`defaultReadCollapse` seeds every descendant that has children into the collapsed set), so only the note body + a one-level section list mount; smaller notes open fully expanded. Each foldable heading carries an outline-style disclosure caret; `visibleReadRows` filters out anything nested under a folded ancestor. Text selection is scoped per section (one `SelectionContainer` per rendered heading), the trade-off for letting the subtree virtualize instead of living in one eager `Column`. Tables render as monospace plain text in v1. File-level `#+` keywords (document preamble), `:PROPERTIES:` drawers and `:LOGBOOK:` drawers render as faded, collapsible monospace sections (`CollapsibleKvSection` / `CollapsibleLogSection`), gated by the "Show preface" / "Show property drawers" Settings toggles. They never rewrite the `.org` file on their own, but double-tapping anywhere on a body row opens `EditRegionScreen` scoped to that region (`onDoubleTap`): the preamble for PREFACE, or the single drawer for a `:PROPERTIES:` / `:LOGBOOK:` section. Real content *before* the first heading (or a file with no headings — an org-roam note) is surfaced separately: the Outline shows a tap-only `PrefaceRow` above the headings, opening a preface-only Read view (`PrefaceContent`, reusing `BodyBlocks`; filename-only breadcrumb; no title or subtree). That preface is counted as a note and full-text indexed. Its metadata sheet opens with `headline = null`; any chip first calls `DocumentViewModel.withPrefaceHeading`, which splices a blank `* ` heading above the content (`OrgMutations.wrapPrefaceInHeading`) in one atomic edit, then applies the change and re-opens the note at that heading.
- **Theme**: the full design-token palette from `design/README.md` lives in `GroveColors` (light + dark), exposed as `MaterialTheme.grove` alongside the Material color scheme, including the `syn-*` org syntax tokens and the 6-color heading-star cycle.
- **Launcher icon sync** (`icon/AppIconManager`): "Sync App Icon with Theme" (Settings > Appearance) swaps the launcher icon by enabling exactly one `activity-alias` in `AndroidManifest.xml` (`.IconDefault` or one `.Icon<Theme>` per `ThemePreference`) and disabling the rest via `PackageManager.setComponentEnabledSetting`. Because switching the enabled alias closes the app's task, `GroveApp` defers `AppIconManager.applyIcon` to a lifecycle `ON_STOP` observer: the swap happens invisibly when the app goes to the background (hence the "restart the app" toast on enable). The alias `ComponentName`s are built from the fixed manifest package (`com.rrajath.grove`), not `context.packageName`, since the debug build's `applicationIdSuffix` would otherwise produce nonexistent class names. The drawer header logo (`BrandMark`) follows the same toggle: it stays the default light mark when off, and the active theme when on.

  **Notifications do not follow the toggle**, and can't be made to. `NotificationAppearance.markColor` feeds `setColor`, which is what older shades tint the small icon with, and `retintActive` repaints notifications already sitting in the shade when the theme changes (a notification's color is fixed at post time, so a reminder, which lives up to 72h, and the ongoing capture notification otherwise kept the previous theme's mark). But Android 16+ renders the *app's* icon in the shade instead, resolved from the manifest's `<application android:icon>`. Measured on API 37: the enabled alias, `setColor`, and an overridden `android.appInfo` extra were all ignored in favour of the manifest icon, so the shade shows the default mark regardless of the selected theme. There is no app-side lever for it short of shipping a different manifest icon.

## Threading

All file and database I/O is on `Dispatchers.IO` (the `FileStore` implementations switch internally); parsing and matching are cheap enough for default dispatchers; the UI thread only ever touches state flows. Sync runs are serialized behind a mutex and coalesced through `SyncCoalescer` so concurrent triggers can neither interleave nor pile up into repeated full passes.

## Testing

The JVM unit suite (`app/src/test`) covers the pure core: parser round-trips over golden fixtures, mutations, timestamps, placeholder expansion, capture insertion (incl. datetrees), the sync engine against fake stores/indexes, query parsing/matching, line editing, and vault behavior over `JvmFileStore`. UI and SAF behavior are exercised manually / by instrumented tests.

One device-specific trap worth knowing: Android's ICU regex engine is stricter than the JVM's (e.g. a bare `}` outside a character class throws). Regexes must escape `}`/`]`, and JVM tests will not catch violations; see the comment in `PlaceholderExpander`.
