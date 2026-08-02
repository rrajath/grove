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
