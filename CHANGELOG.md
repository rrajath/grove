# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

**Versioning:** this project does not use manual SemVer bumps. Every version is
`1.0.<N>`, where `N` is the number of commits reachable from the release commit
(`git rev-list --count HEAD`), the same value `./gradlew -q printVersionName`
prints. Every entry below corresponds 1:1 to a real GitHub Release.

The three entries marked *(local build, no GitHub Release)* predate commit 54,
which is when the release workflow was first added; those changes shipped
locally but nothing was ever tagged or published for them.

**Cutting a release is fully automatic.** Add your changes under
`## [Unreleased]` as you go (that part still takes a human; nobody else knows
what the change was for). On push to `main`, CI computes the version, and:
- if `## [Unreleased]` has content, it tags `v1.0.<N>`, publishes a GitHub
  Release with both APKs using that content as the release notes, then pushes
  a follow-up commit renaming `## [Unreleased]` to `## [1.0.<N>] - <date>`
  and opening a fresh empty `## [Unreleased]` above it;
- if `## [Unreleased]` is empty, the push builds and tests as normal but no
  release is cut.

Nothing needs to be run or renamed by hand: just keep the Unreleased section
updated and push.

## [Unreleased]

## [1.0.219] - 2026-08-07

### Added
- Phone numbers written in prose (e.g. `833-806-1627`, `(833) 806-1627`, `+1 833-806-1627`) are
  now detected in read mode, shown reformatted as `(833) 806-1627`, and tappable to place a call —
  no `[[tel:...]]` link syntax required
- Capture template editor's Target file field now has an inline expanding suggestion list (▾ to
  toggle, filters live as you type) below the field, listing the vault's existing notebooks, styled
  like the settings screen's other dropdown pickers
- That Target file field is validated as a filesystem-safe `.org` filename (checked live and on
  focus-out); Save is disabled until it's valid
- The Template field is validated for unsupported `%...` placeholders and shows the offending tokens
  inline; both the Capture Templates settings list and the "Capture to…" picker sheet now show a
  small `⚠ N` badge on any template with filename or placeholder errors
- Left/right swipes on the planning-dates calendar now step to the previous/next month, same
  direction as the existing ‹/› arrows, with the outgoing month sliding away as the new one slides in

### Fixed
- Auto-capitalizing the first letter of a heading only worked for individual keystrokes; swipe-typed
  or autocorrect-committed words (delivered as one multi-character insertion, not one key event per
  letter) skipped it entirely. Both paths now capitalize consistently
- The template editor's Template field could be hidden behind the keyboard when focused, since the
  screen never reserved space for the keyboard; it now scrolls the focused field above it

## [1.0.217] - 2026-08-07

### Changed
- Metadata sheet's Tags field is now a search/create dropdown instead of a free-text
  space-separated field: typing filters the vault's tag pool into a tappable list, with a
  "Create tag "…"" option when there's no exact match. Applied tags render below the field as one
  `:tag:tag:` colon string; tapping a segment removes that tag
- Widget quick-add's date and notebook chips now open a dropdown listing every option instead of
  cycling one tap at a time. Date adds a "Custom date…" entry that opens the same
  `PlanningDatesScreen` used by Reschedule; presets still resolve at send time so "Today" stays
  correct even if the sheet is left open across midnight
- Updated the design system doc for the tags dropdown and the widget quick-add date/notebook
  pop-out chips described above

### Fixed
- Typing in the metadata sheet's tag field could get silently cleared partway through: the
  autocomplete dropdown's dismiss handler wiped the typed text on any dismissal, including
  transient ones while the keyboard/sheet were still settling into place. Dismissing the dropdown
  no longer touches what's been typed
- The metadata sheet's tag field could be hidden behind the keyboard when focused, and scrolling
  the sheet fully open could push the "+ Add note" button out of view with no way to reach it,
  since the sheet's content never reserved space for the keyboard or allowed scrolling past it
- CI's unit test build was failing: the last backfill introduced a `### Documentation` subsection
  heading, which `ChangelogParserTest` correctly rejects since it isn't one of the Keep-a-Changelog
  categories the parser and widget "what's new" screen support. That bullet now lives under
  `### Changed` instead

## [1.0.208] - 2026-08-06

### Fixed
- Extra TODO keywords squished the metadata sheet's State chips instead of wrapping. The State and
  Priority chip rows were plain `Row`s, so once the configured keywords no longer fit on one line
  Compose shrank each chip to fit, and the last one degraded into a column of single letters
  (`K I L L`). Both rows are now `FlowRow`s that wrap onto additional lines, and a chip's label
  never wraps inside the chip itself the row stayed put, still
  showing its old keyword. The file on disk and the search index were in fact being updated
  correctly every time — only the widget's *display* was stale. It read its rows into local
  variables before handing them to Glance, and while a widget session is live `updateAll()` only
  recomposes that already-captured snapshot instead of re-reading anything, so the ledger kept
  redrawing the same rows until the app was relaunched. The widget now reads the index and settings
  from inside its own composition, so it redraws itself as soon as an edit lands. This also fixes
  the same staleness after widget quick-add, and rows whose keyword pill rendered as plain title
  text
- Marking a task done from the Agenda widget did nothing at all in release builds, while working
  correctly in debug builds of the same commit. Glance starts a widget action by looking up the
  callback class by name and constructing it reflectively; R8 could not see that constructor being
  called from anywhere, so it removed it from the release APK and the tap threw before any of the
  widget's code ran. Release builds now keep the constructor on widget action callbacks

## [1.0.206] - 2026-08-06

### Fixed
- Marking a task done from the Agenda widget could silently do nothing if Android had killed the
  app process in the background: the tap read the vault before its background initialization had
  finished, so the write never happened even though the circle still showed as tapped. It now waits
  for the vault to finish loading before writing
- Settings > Notes > TODO keywords' "Apply" action is now always available, not just after editing
  the field, so it also works as a manual "rebuild index" action (e.g. to recompute a notebook's
  done/open state after it was indexed under an older keyword config)

## [1.0.204] - 2026-08-06

### Fixed
- The Agenda widget's task circle now has a much larger tap target (it absorbed the dead space that
  used to sit between it and the title), and its ring border is thinner
- Tapping anywhere in the Agenda widget's header row (logo, "N today · N in N days") now opens
  Grove, not just the logo icon; the "+" button is unaffected
- Grove now shows up as an app that can open `.org` files from more sources (file managers that tag
  them `application/octet-stream`, and cloud providers whose content URI doesn't end in `.org`) —
  the intent filter previously required both a matching MIME type and a matching path in the same
  rule, which most senders satisfy only one of
- The daily reminder digest ("You have X tasks due today") now counts tasks with a specific
  time-of-day too, so its total always matches what the Agenda screen shows for today (overdue +
  due today); it previously excluded timed tasks since they also get their own "due now"
  notification, which made the digest undercount
- Marking a task done in the Agenda widget now removes it from the widget right away. The
  widget used to depend on the app's background sync to notice the file changed and reindex it,
  which was fire-and-forget and, if the widget action revived a process Android had killed in the
  background, could be a complete no-op — the task would only disappear once some later, unrelated
  sync happened to run. It's now indexed immediately from the text just written, independent of
  background sync

## [1.0.200] - 2026-08-05

### Fixed
- Sync conflicts' "Keep both" now line-diffs the current file against the conflict copy and keeps
  both versions right where they diverge, instead of appending the whole conflict copy under a new
  heading at the end
- Pressing Enter in a bulleted, numbered, or checklist item now always continues the list, even
  right after editing a word in the middle of the line and moving the cursor to the end before
  hitting Enter
- The Agenda widget's section-header divider line now spans the full remaining width of the row
  instead of just its right half
- Favoriting a note now adds a `:CUSTOM_ID:` property to it (only if it doesn't already have an
  `:ID:` or `:CUSTOM_ID:`), so the favorite is stably referenceable from the sidebar
- Sharing text or a link into Grove from another app no longer opens Grove's UI; the note is saved
  to the configured file and a toast confirms it, exactly as before, but silently

## [1.0.198] - 2026-08-05

### Added
- Settings › Reminders now has a "Notify me" dropdown (at the time of event, 5/10/15/30 minutes
  before, 1 hour before, 1 day before) that shifts the "due" notification earlier and reworks its
  message to match ("Your task is due in 15 minutes", etc.); only affects SCHEDULED/DEADLINE
  stamps that carry their own time of day
- Settings › Agenda has a new "Widget" section: a transparency slider for the home-screen Agenda
  ledger widget's background, and a "Days ahead" field controlling how far into the future it shows

## [1.0.196] - 2026-08-05

### Added
- New home-screen widget: an Agenda ledger showing overdue tasks first, then upcoming tasks
  grouped by day (Today, Tomorrow, then by date), sorted by priority and time within each day.
  Tap a task to open it in read mode, tap the circle to mark it done (recurring tasks and
  auto-archive behave the same as on the Agenda screen), tap "+" for a small quick-add composer
  (defaults to the Sharing settings' target notebook), or tap the header icon to open the app

## [1.0.194] - 2026-08-05

### Added
- The star button on the Search page now opens a searchable dropdown of saved searches
  (plus the quick-start cards) instead of a plain name prompt: typing continuously filters
  the list, picking an existing entry asks to overwrite it, and a name not in the list saves
  as a new search
- The Filters bottom sheet's negatable facets (Notebook, Tags, TODO state, Priority) now show
  a "tap again to exclude" hint next to their section label
- The Filters bottom sheet now also has Closed and Created date facets, matching the existing
  Scheduled/Deadline ones (Today/Next 7 days/Overdue/No date/Custom range)
- The Advanced panel's operator chips gained d./c./cr., alongside the existing t./i./s./b./p.

### Fixed
- `s.overdue`/`d.overdue` search expressions returned nothing: the "overdue" period token
  wasn't recognized at all; they now match anything scheduled/due strictly before today
- `s.nodate`/`d.nodate` returned nothing: only `s.none`/`d.none` were recognized; `nodate` is
  now accepted as an alias for `none`
- `s.today`/`d.today` matched anything scheduled/due on or before today (i.e. everything
  already overdue too) instead of only today itself
- The Search page's "Overdue" quick-start card only checked the deadline date by default,
  missing open tasks that were overdue on their scheduled date instead
- The "Open tasks" quick-start card's default search ANDed every active TODO keyword together
  instead of ORing them, which could never match anything once more than one keyword was
  configured; the same AND-instead-of-OR mirroring bug affected any multi-value Filters
  selection (e.g. two tags picked together) whenever the mirrored text field re-narrowed the
  results a second time
- The Advanced panel's operator chips now reflow naturally with the available width instead
  of a fixed two-row split, so short chips no longer wrap to a second line while there's still
  room on the first
- Scheduled/deadline pills no longer render on already-done items in search results, since a
  completed item's dates aren't actionable

## [1.0.190] - 2026-08-05

### Added
- Long-pressing a Saved Search or Favorite in the nav drawer now opens a Move up/Move
  down/Rename/Delete menu instead of just a delete confirmation, so both lists can be
  reordered and renamed in place, not only pruned
- A What's New modal now appears once after an app update, listing everything from
  CHANGELOG.md since the version you last saw, formatted the same way as the changelog
  itself (Added/Fixed/etc. sections with bullet items)

### Fixed
- Killing (marking done) a recurring task with auto-archive enabled no longer archives it: a
  repeating SCHEDULED/DEADLINE keeps the task's keyword active (per org semantics, only the date
  advances), but auto-archive was checking the requested keyword instead of the task's actual
  post-mutation state, so it refiled still-active recurring tasks to the archive location anyway
- Setting a SCHEDULED or DEADLINE to a date/time already in the past no longer fires an
  immediate "task is due" notification; only a genuine catch-up (the device was off or the
  app was closed when an already-armed future reminder came due) still fires one

## [1.0.187] - 2026-08-04

### Added
- Double-tapping Outline's PREFACE section now opens an editor scoped to just the file's
  preamble (everything before the first heading), mirroring double-tap-to-edit elsewhere

### Fixed
- Daily reminder digest no longer overcounts tasks: a heading whose SCHEDULED carries a
  time-of-day (and so fires its own "due now" notification) no longer falls back to a
  date-only DEADLINE on the same heading as a substitute anchor date, which could count it
  as due in the digest even though the Agenda screen would never show it under today or
  overdue
- Agenda rows now show a heading's inherited tags (its own tags plus every ancestor
  heading's tags), matching org-mode tag inheritance, instead of only the heading's
  own tags
- Sync conflict screen's "Keep both" button now actually merges both versions (appending the
  conflicting copy under a `* CONFLICT` heading) instead of silently leaving the file
  unchanged when the conflict marker had already cleared from the index, which previously
  looked identical to "Keep current"
- Read mode now renders a heading's SCHEDULED/DEADLINE planning line before its
  `:PROPERTIES:`/`:LOGBOOK:` drawers, matching the order edit mode (and the underlying org
  file) already uses, instead of showing drawers first
- Tapping a `.org` file in a file manager (or setting Grove as its default handler) now actually
  opens the matching notebook instead of silently doing nothing — the manifest's file-open
  intent-filter was correctly registered, but nothing on the receiving end matched the
  incoming `content://`/`file://` URI against the vault and navigated to it

## [1.0.179] - 2026-08-04

### Fixed
- Shared-link capture now falls back to a hidden WebView to read the real page title when the
  plain HTTP fetch returns a bot-check interstitial (e.g. Reddit's "Please wait for
  verification" page), instead of using the placeholder title verbatim

## [1.0.174] - 2026-08-04

### Changed
- The sidebar's Agenda/saved-search icons and the search icon (sidebar, Notebooks/Outline
  top bars, search field) now use the exact path data from the design prototype instead of
  approximated Material Icon paths
- Refile sheet's notebook rows now use a dedicated notebook icon instead of the "▤" text
  glyph
- Search date filters (`s.none`/`d.none`/`c.none`/`cr.none`) now match notes whose
  corresponding timestamp is absent, instead of being treated like every other "within
  period" filter
- The launcher icon no longer auto-switches to a dark-mode variant; light-theme colors are
  now used by default in both themes

## [1.0.172] - 2026-08-03

### Changed
- Notebooks list now supports pull-to-refresh instead of a dedicated refresh button in the
  top bar
- The search icon (sidebar, Notebooks/Outline top bars, search field) now uses a dedicated
  `ic_search` drawable instead of the "⌕" text glyph / `Icons.Default.Search`
- The sidebar's Settings, Agenda, and saved-search icons now use dedicated `ic_settings`,
  `ic_calendar_view_day`, and `ic_filter_center_focus` drawables instead of text glyphs
- Search's Filters button now uses `Icons.Default.FilterList` instead of the "⚙" text glyph

### Fixed
- Tapping "Manage" from the Capture picker now opens Settings § Capture Templates directly
  instead of the Settings home page

## [1.0.168] - 2026-08-03

### Changed
- Edit mode's formatting toolbar now draws Bold/Italic/Underline/Code/Checklist as custom
  vector icons instead of text glyphs (`B`, `I`, `U`, `</>`, `☑`), matching the icon style
  already used for the rest of the toolbar

## [1.0.164] - 2026-08-02

### Changed
- Favorites now use a dedicated star icon instead of the plain "★" character: filled amber
  in read mode and the outline row indicator, outlined to match the existing color scheme
  in the sidebar and the outline swipe row
- The "Note" swipe action on Outline's swipe-reveal row and Agenda's swipe-to-commit row now
  uses a custom `ic_note` drawable instead of the Material `EditNote` icon

### Fixed
- Back gesture on Settings § Notes and § Sharing now shows the previous screen fading in
  like every other screen instead of fading to blank — a `BackHandler` there was silently
  swallowing the predictive-back gesture before `NavHost` could animate it
- The favorites star in read mode now aligns flush with the property drawer's right edge
  below it
- Read mode's metadata sheet now shows "SCHEDULED"/"DEADLINE" instead of the abbreviated
  "SCHED"/"DUE"
- Fixed a crash when changing the theme in Settings with "Sync app icon with theme" on:
  launcher shortcuts were republished against the icon alias for the *new* theme before
  `AppIconManager` had actually enabled it (that switch is deferred to when the app
  backgrounds), so `ShortcutManagerCompat` rejected the publish with "is not main activity"

## [1.0.162] - 2026-08-02

### Fixed
- Agenda swipe-row checkbox and priority badge now sit 12dp from the row edges instead of 2dp
- Edit mode's metadata sheet: SCHED and DUE now stack on separate lines instead of sharing one row

## [1.0.157] - 2026-08-02

### Added
- Settings § Notes: "Auto-archive done items?" — marking a task with any done-type keyword
  (not just `DONE`) now refiles it automatically, resolved the same way org resolves
  `ARCHIVE`: the heading's own property, its nearest ancestor's, the file's `#+ARCHIVE:`
  keyword, then a new Settings-configured fallback location (picked via the same drill-down
  picker as manual refile). Works identically from the Outline swipe, Agenda, Search, and
  edit mode, with a "Marked done. Refiled to `<location>`" snackbar and Undo
- Edit mode's metadata sheet gained an "+ Add note" action, logging free text into the
  heading's LOGBOOK drawer the same way the Outline's "Note" swipe action does
- Read mode gained the same ☰ metadata sheet as edit mode: state, priority, tags,
  Schedule/Deadline, and Add note are all editable without switching to edit mode first,
  saving straight to disk

### Changed
- Edit mode's Schedule and Deadline are now one combined row instead of two, opening the
  same dual-date picker already used for both
- Switching a note between read and edit mode is now instantaneous; the full-screen
  transition now only plays when actually navigating to a different note

### Fixed
- Settings export/import now carries the last-used refile destination, matching the other
  refile-shaped settings that were already portable
- Cold start no longer wipes and rebuilds the entire note index when your TODO keyword
  setting differs from the default: a startup-vs-change detection bug treated every launch
  as a keyword config change, forcing a full reparse of every notebook instead of trusting
  the existing cache

## [1.0.155] - 2026-08-01

### Fixed
- Reminder digest ("You have X tasks due today") was double-counting tasks
  that carry an explicit time-of-day: those already get their own individual
  notification, so the digest now only bundles date-only reminders, matching
  its intended count
- Digest count no longer disagrees with Agenda by one when a heading carries
  both a SCHEDULED and a DEADLINE date landing on the same side of today: it
  now collapses to a single task the same way Agenda's "a heading belongs to
  exactly one day" rule does, instead of counting the heading's two reminders
  table rows separately

## [1.0.151] - 2026-08-01

### Changed
- Screen transitions now use the fade-through from the Android predictive-back
  guidelines instead of a horizontal slide, which had made moving between
  screens look like swiping through full-screen photos. Dragging back fades the
  previous screen in and scales it down into place, following the gesture and
  rewinding if you let go early; going forward mirrors it as a zoom in

## [1.0.147] - 2026-08-01

### Added
- Predictive back gesture support: navigating between screens now slides the
  previous screen in, partially visible, while dragging back
- Agenda swipe panel: an "Add note" button rides alongside "Mark done"; a
  partial swipe reveals both, a full swipe still marks the task done directly

### Fixed
- Agenda swipe-reveal background now has rounded corners matching the card
  in front of it, instead of showing square corners underneath

### Changed
- Removed em dashes from UI text, code comments, and documentation throughout
  the project, replacing each with punctuation that fit the sentence

## [1.0.144] - 2026-07-31

### Fixed
- Search page UI improvements

## [1.0.143] - 2026-07-31

### Fixed
- Search results swipe gesture bug
- Light theme status bar icons

## [1.0.142] - 2026-07-31

### Added
- Split settings into separate pages for better organization
- Swipeable search results interface

### Changed
- Reordered theme list for better discoverability

## [1.0.141] - 2026-07-30

### Fixed
- Agenda screen UI improvements and other minor enhancements

## [1.0.140] - 2026-07-30

### Added
- Agenda keyword filters

### Changed
- Swipe-panel alignment improvements

### Fixed
- Notification icon display

## [1.0.138] - 2026-07-29

### Added
- Tap-to-save functionality for planning dates
- Outline note capture from agenda

### Changed
- Calendar and flag icons replacing SCHEDULED/DEADLINE text labels

### Fixed
- Logbook note continuation formatting
- Swipe-reveal panel colors
- Repeater sentence alignment

## [1.0.136] - 2026-07-29

### Changed
- Replaced two-step date picker with full-screen SCHEDULED + DEADLINE editor

## [1.0.135] - 2026-07-29

### Added
- Outline search functionality
- State picker in outline view
- Agenda done filter

### Fixed
- Outline autosave issues

## [1.0.133] - 2026-07-28

### Added
- Agenda swipe actions (mark done, reschedule, etc.)
- Outline narrowing feature
- LOGBOOK drawer support in outline view

## [1.0.132] - 2026-07-27

### Changed
- Migrated search to FTS5 trigram index with SQL facet pushdown (performance improvement)

## [1.0.130] - 2026-07-27

### Fixed
- Reminder notification tap handling
- Keyboard gap when editing
- Agenda snippet overflow

## [1.0.128] - 2026-07-26

### Changed
- Extracted Agenda into its own dedicated screen

### Fixed
- Search bugs and visual polish

## [1.0.127] - 2026-07-26

### Added
- Agenda overdue section
- Agenda day pagination
- Per-priority agenda colors

## [1.0.126] - 2026-07-25

### Added
- Auto-scroll during read mode drag-selection

### Fixed
- Selection auto-scroll in org editors

## [1.0.123] - 2026-07-24

### Changed
- Renamed header tags to preface
- Dropped per-row sync checkmark

## [1.0.121] - 2026-07-24

### Added
- Reminders system with alarm scheduling and notifications
- Boot receiver rebinding for alarms

## [1.0.120] - 2026-07-23

### Changed
- Improved outline expand/collapse visual feedback
- Settings UX improvements

### Fixed
- Editor scroll jitter

## [1.0.118] - 2026-07-23

### Changed
- Redrew outline fold caret as canvas glyph
- Enlarged collapsed-chevron visual size
- Conflict diff labeling by date

## [1.0.117] - 2026-07-23

### Added
- Reusable PlanningDatePicker with date+time step

## [1.0.116] - 2026-07-20

### Added
- Fade and checkmark animations for checklist parent items (when all children complete)
- Zero-padded timestamp minutes
- Long-press for date-only timestamp creation
- FAB clearance improvements on lists

## [1.0.114] - 2026-07-20

### Added
- Checklist insert button

### Changed
- Redrew read-mode list markers as canvas glyphs

### Fixed
- Drawer swipe and back gesture handling breaking after leaving home screen

## [1.0.111] - 2026-07-20

### Added
- Checklist list items with configurable read-mode tap-to-cycle states

## [1.0.109] - 2026-07-18

### Fixed
- App shortcut deep links
- Notebook sorting
- FAB scrim rendering
- Autosave check mark visibility
- Link button behavior

## [1.0.107] - 2026-07-17

### Added
- Support for dashed tags in headers
- Last-used refile location memory

### Fixed
- Timestamp and content line break handling

## [1.0.105] - 2026-07-14

### Added
- Dynamic launcher shortcuts per capture template

## [1.0.103] - 2026-07-13

### Added
- Auto-archive feature during refile operations
- Notebook display name customization in settings

### Changed
- Moved header tags to top of file

### Fixed
- Refile-undo tap-through bug
- Sticky header tags visibility

## [1.0.100] - 2026-07-13

### Added
- Outline gestures: swipe-reveal panels
- Move/indent command bar for outline editing
- Refile functionality in outline

### Fixed
- Command bar centering
- Tag and favorite alignment
- FAB cursor focus

## [1.0.98] - 2026-07-12

### Changed
- Converted Settings theme chips to inline dropdown per prototype

## [1.0.96] - 2026-07-12

### Added
- Favorite stars for notes
- Header tag display

### Fixed
- Header-tags visibility issues

## [1.0.95] - 2026-07-11

### Added
- Theme-synced launcher icons
- 5-spoke brand mark in UI

## [1.0.90] - 2026-07-10

### Added
- Debug build type with distinct applicationId for testing alongside release builds

### Changed
- Even out editor toolbar spacing
- Capture auto-save checkmark
- Unified notebook-row checkmark icon

### Fixed
- Toolbar icons wrapping on narrow screens
- Toolbar divider between code and link buttons removed

## [1.0.84] - 2026-07-09

### Added
- Performance optimization: lifecycle-aware flow collection (reduced memory usage)
- O(n) inherited-tags indexing algorithm
- Lazy read view rendering
- Memoized tree traversals
- Memoized editor highlighting per line
- O(1) tokenizer overlap check
- Search index memory caching
- Precomputed timestamp dates
- Theme prototype design files

### Changed
- Hot-path regexes optimization
- Database projections optimization
- Sync state machine improvements

### Fixed
- Dead code removal throughout codebase

## [1.0.82] - 2026-07-08

### Added
- Theme picker with 7 themes for Settings screen

## [1.0.80] - 2026-07-08

### Fixed
- Sentry release version mismatch
- Capture template label handling

## [1.0.77] - 2026-07-08

### Added
- Sentry error tracking and session monitoring
- Sentry release tracking in Android Manifest

### Fixed
- Quick-capture autosave crash
- Editor scroll position persistence
- Autosave toast notifications
- Insert submenu positioning

### Changed
- Removed Sentry wizard test exception from MainActivity

## [1.0.73] - 2026-07-07

### Changed
- Scroll jump buttons extended functionality
- Reverted double-tap cursor positioning

## [1.0.71] - 2026-07-07

### Added
- Auto-save functionality for capture and edit modes
- Scroll to top/bottom buttons

## [1.0.70] - 2026-07-06

### Added
- Insert submenu with formatting options
- Unified capture keyboard

### Fixed
- Selection auto-scroll behavior
- Link menu positioning
- Broken Compose imports in clean builds

## [1.0.67] - 2026-06-28

### Fixed
- Markdown preview indentation
- Heart icon removal from favorite menu

## [1.0.65] - 2026-06-27

### Changed
- App icon background refinement

## [1.0.64] - 2026-06-27

### Changed
- Architecture documentation updates
- Generated design system documentation

## [1.0.63] - 2026-06-27

### Changed
- Minor app icon adjustments

## [1.0.62] - 2026-06-27

### Added
- Empty-heading validation
- Favorites feature for quick access
- Unit tests for heading validation and favorite note serialization

## [1.0.59] - 2026-06-26

### Fixed
- Sync race condition
- Pin icon behavior
- Clipboard toast notifications
- Heading capitalization

## [1.0.58] - 2026-06-19

### Fixed
- Note editor UI issues
- Keyboard gap in editor

## [1.0.56] - 2026-06-18

### Fixed
- Quick capture UI bugs: scroll positioning, heading color, icons, placeholder popup

## [1.0.54] - 2026-06-14

### Added
- Settings: import/export preferences as JSON
- CI/CD: GitHub Release publishing with both APKs

### Fixed
- Settings serializer compile error

## [1.0.50] - 2026-06-13 *(local build, no GitHub Release)*

### Added
- M3: capture templates: org-capture for Android
- M4: sync engine + Room index: laptop edits appear on reopen
- M5: full org editor: syntax highlighting, metadata, structural ops
- M6: full-text + structured search, saved searches, agenda
- M7: zero-friction capture surfaces + polish
- README and docs (architecture, design decisions, search syntax, terminology)
- Icon color picker to notebook long-press menu
- Prefill heading stars in the capture editor
- Baseline profile for performance optimization
- Macrobenchmark module for startup and scroll timing
- Journal and Quick Note launcher shortcuts
- Org file handler registration
- O(1) fold detection in outlines instead of O(n²)
- Outline row title annotation caching

### Changed
- Outline: apply node edits optimistically instead of reloading from disk
- Editor: memoize the buffer parse in the view model
- Editor: cache syntax-highlight result and drop redundant headline spans
- Vault: fetch single-file metadata via stat() instead of listing the tree
- Search: run filtering and snippet building off the main thread
- Release: enable R8 minification and resource shrinking
- Editor: auto-capitalize sentences while typing
- Outline opens fully collapsed by default
- Editor toolbar spacing and icon sizing enlarged/unified
- Render org links in headings instead of raw `[[target][desc]]`
- Editor link button inserts named placeholders

### Fixed
- Top bar overlapping the status bar in edge-to-edge mode
- Capture crash: escape regex braces/brackets for Android's ICU engine
- Notebook creation: SAF providers appended .txt to new .org files
- Empty notebook UX: outline FAB adds a top-level note
- Various outline/editor/capture/home bugs
- Outline swipe-reveal panel colors and re-arm behavior

## [1.0.3] - 2026-06-11 *(local build, no GitHub Release)*

### Added
- Initial Android Studio Compose scaffold
- M1: design system, navigation shell, app identity
- M2: lossless org engine + SAF vault: browse real notes
