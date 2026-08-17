# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

**Versioning:** `versionCode` and `versionName` are decoupled.
- `versionCode` is still fully automatic: it's the number of commits reachable
  from the release commit (`git rev-list --count HEAD`), so it keeps
  incrementing on every push with no action needed.
- `versionName` is a manually-controlled SemVer string (`major.minor.patch`),
  set in the `versionName` key in `gradle.properties`. CI reads it as-is via
  `./gradlew -q printVersionName` and never computes or bumps it. Bump it by
  hand whenever a release should carry a new version number: major = breaking
  change, minor = new feature, patch = fix/tweak.
- `1.0.0` marks the point this project switched from the old auto `1.0.<N>`
  scheme (`N` = commit count) to manual SemVer, coinciding with the first
  public release. Entries below that predate the switch keep the `1.0.<N>`
  values they were actually released under.

Every entry below still corresponds 1:1 to a real GitHub Release.

The three entries marked *(local build, no GitHub Release)* predate commit 54,
which is when the release workflow was first added; those changes shipped
locally but nothing was ever tagged or published for them.

**Cutting a release is fully automatic for `versionCode`.** Add your changes
under `## [Unreleased]` as you go (that part still takes a human; nobody else
knows what the change was for). On push to `main`, CI reads the current
version (`versionCode` from the git commit count, `versionName` from
`gradle.properties`), and:
- if `## [Unreleased]` has content, it tags `v<versionName>`, publishes a
  GitHub Release with both APKs using that content as the release notes, then
  pushes a follow-up commit renaming `## [Unreleased]` to
  `## [<versionName>] - <date> (build <versionCode>)` and opening a fresh empty
  `## [Unreleased]` above it — the `(build N)` suffix is `versionCode`, kept
  alongside `versionName` because the in-app What's New modal needs a value
  that's always unique per release, which `versionName` alone no longer is;
- if `## [Unreleased]` is empty, the push builds and tests as normal but no
  release is cut.

Nothing needs to be run or renamed by hand for `versionCode`. `versionName`
does need a manual bump in `gradle.properties` before pushing a release that
should carry a new version number — pushing again without bumping it re-uses
the same tag/version and just re-uploads the APKs to the existing release.

## [Unreleased]

### Added
- Favorited notes: star/unstar from the outline, sidebar, and read view now resolves through the heading's stable `:CUSTOM_ID:`/`:ID:` instead of a raw line number, so favorites survive edits elsewhere in the file.

### Fixed
- Fixed favorited notes losing their star when a note above them was refiled or deleted: the cleanup that drops favorites inside a removed subtree was comparing a favorite's stale stored line index against the removed range instead of resolving its current position first.

## [1.0.0] - 2026-08-15 (build 290)

### Fixed
- Disabled AGP's "Dependency metadata" APK Signing Block entry (`dependenciesInfo.includeInApk`/`includeInBundle = false`), which F-Droid's build scanner rejects as a non-standard signing block, failing the reproducible-build check.

## [1.0.0] - 2026-08-15 (build 287)

### Fixed
- Pinned `android.ndkVersion` (29.0.14206865) so release builds strip bundled native libraries (from dependencies, not Grove's own source) with a consistent NDK across environments. An unpinned NDK let CI and F-Droid's build server pick different versions, producing byte-different `.so` files and failing F-Droid's reproducible-build verification against the CI-published APK.

## [1.0.0] - 2026-08-15 (build 282)

### Fixed
- CI stopped publishing new GitHub Releases (breaking Obtainium and anyone else watching Releases) once `versionName` became manually-bumped and started repeating across pushes: the release tag was `v$VERSION` alone, so every push after the first collided with the existing tag and silently clobbered its assets instead of creating a new release. The tag now includes the auto-incrementing `versionCode` (`v$VERSION-$VERSION_CODE`), guaranteeing a fresh tag — and a genuinely new release — on every push with content under `## [Unreleased]`. Release titles stay human-friendly (`v1.0.0 (build 277)`), independent of the tag string.

## [1.0.0] - 2026-08-15 (build 279)

### Changed
- Settings now shows the build number in brackets next to the version name (e.g. "Grove v1.0.0 (277)"), making it possible to tell builds with the same `versionName` apart without pulling `adb shell dumpsys package`.

## [1.0.0] - 2026-08-15 (build 277)

### Fixed
- Ledger widget: an unbounded section (e.g. an already-corrupted index misclassifying hundreds of notes, as with the prior KILL-keyword cold-start race) could push Glance's `LazyColumn` past Android's ~1MB RemoteViews transaction limit, which surfaced as the host's generic "Can't show content" placeholder with nothing logged. Each section is now capped at 20 rows with a "+N more · view all" row (deep-links to the in-app Agenda screen) when truncated; the section header's count stays the true, untruncated total.

## [1.0.0] - 2026-08-15 (build 274)

### Added
- `.github/workflows/deploy-docs-site.yml`: deploys `docs-site` to Cloudflare on push to `main`, scoped to `docs-site/**` changes, mirroring the `paths-ignore` on the app's build workflow so a push never triggers both. Requires `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` repo secrets.

### Fixed
- `docs-site`: screenshots and other optimized images rendered as alt text instead of the actual image once deployed to Cloudflare. The `@astrojs/cloudflare` adapter defaults to transforming images at runtime via Cloudflare's Images product, which isn't provisioned on the account (and isn't emulated by `wrangler dev`), so every image request 404'd. Fixed by setting `imageService: 'compile'` so images are optimized at build time instead, matching the site's fully static output.

## [1.0.0] - 2026-08-15 (build 271)

### Changed
- Moved the docs/landing site (`docs-site/`, Astro + Starlight) into this repo instead of a separate one, so docs can be updated alongside code. The app CI workflow now ignores `docs-site/**`; it deploys separately as a Cloudflare Worker with static assets (`docs-site`, `npm run deploy`).

## [1.0.0] - 2026-08-14 (build 266)

### Added
- Added a Screenshots section to `README.md` and the F-Droid/Play store listing assets
  (`fastlane/metadata/android/en-US/images/`): six phone screenshots and a 512x512 `icon.png`
  rendered from the default adaptive-icon layers, since the app ships no raster launcher icon.

### Fixed
- Fixed the agenda ledger widget permanently showing the system's "Can't show content"
  fallback for vaults containing a SCHEDULED/DEADLINE timestamp with an out-of-range hour or
  minute (e.g. a hand-typo'd `25:00`, or a >24h CLOCK duration cookie that reads like a clock
  time). `OrgTimestamp.parse` already guarded an invalid *date* against throwing, but not an
  invalid *time*: `LocalTime.of` threw an uncaught `DateTimeException` straight out of the
  widget's `provideGlance`, which Glance has no crash UI for, so the AppWidgetHost fell back to
  its generic placeholder on every redraw — surviving delete/re-add because the bad timestamp
  lives in the vault, not the widget instance. An out-of-range time now rejects the whole
  timestamp the same way an out-of-range date already did. The widget's section-building is
  also now wrapped defensively, so a future data problem degrades to an empty ledger instead of
  taking the whole widget down again.
- Fixed the daily digest notification again reporting hundreds of tasks as overdue with a custom
  done-keyword (e.g. `KILL`) left unrecognized, on some cold starts. This is a second, narrower
  gap in the same area as the earlier "Fix cold-start race that could index notebooks with stale
  TODO keywords" fix (commits 343f65b/fd1dc85), not a full regression of it: that fix made
  `GroveApplication.onCreate` await `settingsRepository.settings.first()` before wiring up
  `syncManager.attach`, which does stop DataStore's *disk* read from racing the first sync. But
  `keywords` (the parsed `OrgKeywords` used to classify every heading during indexing) is a
  *separate* `by lazy` `StateFlow`, eagerly seeded with the default keyword set until its own
  independent subscription delivers a real value — and nothing forced that subscription to
  start early. Its very first read happened synchronously, with no suspension point, inside the
  very first sync's indexing pass, so it could still return the default seed regardless of how
  much wall-clock time had passed since the settings read. `onCreate` now explicitly blocks on
  `keywords` itself until it has emitted the value derived from the settings just read, before
  letting that first sync fire, closing the gap the settings-only wait left open.
- Fixed favorites in the sidebar drawer becoming "Note not found" after the underlying `.org`
  file was edited outside the app (Grove's whole model is that files are edited in Emacs/synced
  via Syncthing too, not just in-app). `FavoriteNote` was keyed purely by a raw line number,
  which drifts the moment any line above it is added or removed externally. Favoriting a note
  already wrote a stable `:CUSTOM_ID:` onto its heading, but discarded the id afterward instead
  of storing it; favorites, `NoteRef` navigation, and the read/edit-mode note loaders now resolve
  by that id first, falling back to the line number only when there's no id (older favorites, or
  a note that never got one).

## [1.0.259] - 2026-08-13

### Fixed
- The in-app "What's New" modal now tracks the last-seen release by `versionCode` instead of
  `versionName`. It previously compared `BuildConfig.VERSION_NAME` directly, which worked only
  because that string used to embed the ever-increasing commit count; now that `versionName` is a
  manually-bumped SemVer string that can repeat across releases (e.g. several pushes all still
  `1.0.0`), that comparison would have silently stopped surfacing new changelog entries after the
  first one. CHANGELOG.md's archived release headers now carry a `(build N)` suffix — the
  `versionCode` at cut time — and `ChangelogParser`/`SettingsRepository` key off that instead.
- Shared-link title fetching (PageTitleFetcher) no longer accepts a generic app-shell
  `<title>` — like YouTube's bare "YouTube" — as a shared link's title. The previous fix for
  this only special-cased Reddit's known shell titles; the check is now site-agnostic: it
  derives the shared URL's own brand from its host and rejects a fetched title that reduces to
  nothing but that brand (alone, or as a short lead-in tagline), falling back to the WebView
  fetch either way so the real, client-rendered title is used instead.
- Fixed that brand check still missing YouTube links shared via the native share sheet, which
  hand over a `youtu.be/<id>` short link: `PageTitleFetcher` was comparing the fetched title
  against the shortener's own host ("youtu") rather than the real site it redirects to
  ("youtube"), so the brand never matched and the "YouTube" placeholder slipped through. HTTP
  fetches now follow redirects manually so the placeholder check runs against the final,
  post-redirect URL; the WebView fallback's poll loop does the same using `webView.url`.
  Verified on-device: a shared `youtu.be` link now resolves to the real video title.

## [1.0.255] - 2026-08-12

### Fixed
- Quick capture's save icon now matches the note/preface editor's UX exactly: green while
  dirty (tap saves immediately), grey once saved (tap shows a "last saved at" toast), no
  blink animation. It previously started hidden, blinked twice on every auto-save, and used
  the two colors with inverted meaning.
- Auto-capitalizing the first letter of a heading now also fires right after a TODO keyword
  (e.g. `* TODO buy milk` → `* TODO Buy milk`), including for swipe-typed words, matching the
  existing behavior on plain headings. Also now respects a custom `todoKeywords` configuration
  instead of assuming the default set.
- The Quick Add widget's text field now auto-capitalizes like a sentence, matching every other
  text entry point in the app.
- The ten newest themes (Chalk, Cobalt, Delft, Sage, Ash Rose, Obsidian, Lantern, Lighthouse,
  Moss, Wisteria) now use a hue-distinct color for property/drawer keys instead of a shade of
  the same neutral used for values, so preface, `:PROPERTIES:`, and `:LOGBOOK:` drawer keys
  are visually distinct from their values, matching Grove Dark/Light and the other community
  themes.
- Double-tapping a subheading while reading now opens the editor with the cursor and scroll
  position landing on that subheading instead of always at the top of the note. Previously,
  double-tapping anywhere jumped into the editor at the very first line, forcing a manual
  scroll through the whole buffer to reach a subheading buried further down. Double-tapping
  the note's own heading/body is unchanged.
- Report a bug screen: the steps-to-reproduce placeholder now shows each numbered step on its
  own line instead of run together on one line; hitting Enter on a numbered item now continues
  the list (or removes the item if it was left empty), auto-capitalizing the first letter typed
  into a new item, matching the note editor's list behavior; removed the "Privacy policy" link
  (no such page exists) and replaced em dashes with plain punctuation throughout the screen's
  copy; the "Report sent" checkmark now uses the same check icon as the Notebooks screen's sync
  indicator instead of a plain "✓" glyph; a successful send now shows a popup with a link to the
  filed GitHub issue and Dismiss/Go to Issue buttons instead of a toast; "Copy as text" is now
  styled as a visible link instead of unstyled plain text. The "What went wrong" and "Steps to
  reproduce" fields now auto-capitalize like a sentence as you type (via the keyboard's own
  sentence-cap input mode, same as every other text field in the app), rather than never
  capitalizing at all.
- Sharing a Reddit link into Grove no longer saves the note titled just "Reddit" (or "Reddit -
  The heart of the internet"). Reddit serves that generic placeholder to every URL over plain
  HTTP and only swaps in the real post/subreddit title once its page finishes loading
  client-side; the title fetcher now recognizes those placeholders and waits for the real title
  to appear instead of accepting the placeholder as final.

## [1.0.251] - 2026-08-12

### Added
- Ten new themes in Settings § Look and Feel: Chalk, Cobalt, Delft, Sage, Ash Rose (light-family)
  and Obsidian, Lantern, Lighthouse, Moss, Wisteria (dark-family), each with a matching
  "Sync App Icon with Theme" launcher icon variant
- Settings § Help § Report a bug screen: description (required) and steps-to-reproduce
  (optional) fields, toggles for including device/app info and the recent error log, an
  expandable "show exactly what gets sent" payload preview, and Send/Copy actions. Send has
  no crash server to submit to yet, so it surfaces the entered report as a toast instead;
  Copy places a plain-text version on the clipboard for pasting into a GitHub issue.

## [1.0.249] - 2026-08-11

### Changed
- The planning calendar's "has items" marker (days some other note has a SCHEDULED/DEADLINE on)
  is now a bold, violet-tinted day number instead of a dot, matching how Emacs org-mode marks its
  scheduling calendar; the "has items" legend swatch is gone since the coloring speaks for itself
- The planning calendar no longer marks today with its own dot unless today itself is scheduled
  or deadlined

### Fixed
- The planning calendar dialog's status and navigation bar icons no longer render light-on-light
  in light themes; the dialog opens its own platform window that wasn't picking up the app's
  theme-driven icon appearance
- The Ledger widget's mark-done tap target is back to the visible ring's actual width instead of
  the wider region that was compensating for an unrelated stale-render bug already fixed elsewhere

## [1.0.245] - 2026-08-10

### Fixed
- Fixed a cold-start race where the very first sync after launching the app (most likely right
  after an update) could index some notebooks before the persisted TODO keyword config had
  finished loading, silently falling back to the default keywords and leaving custom keywords
  (e.g. a user-defined `KILL` state) unrecognized and unhighlighted in the agenda until the next
  full reindex

## [1.0.242] - 2026-08-10

### Added
- Metadata bottom sheet: a "Refile" option next to "Add note". In Read mode it opens the Refile
  picker directly; in the Editor it first confirms ("this note will be saved and refiled to the
  location you choose"), saves the buffer, then opens the picker against the saved file
- A completed refile from the metadata sheet now shows the same "Refiled to X" snackbar (with
  Undo) as refiling from the Outline view

### Changed
- Agenda's swipe-left/swipe-right dropdowns now only offer Mark as Done and Schedule Task; Set
  Deadline is no longer selectable there (still supported if set via an older settings export)
- Settings › Sharing's target-file field is now the same notebook-autocomplete field (with
  inline filename validation) used by the capture-template editor's Target file field, instead of
  a plain, unvalidated text field
- Search's "Browse tags" quick-start card is now "Unscheduled": it filters to open TODOs with
  neither a scheduled date nor a deadline, using a live count like the other quick-start cards
- Search's quick-start cards now use real Material icons at a consistent size instead of
  text-glyph icons that rendered at mismatched sizes
- Search's saved-search star now aligns with the title line instead of centering against both
  lines

### Fixed
- The time picker's digits, the capture template editor's Name field, and its Heading name field
  (including their placeholder text) now use the app's UI font instead of silently falling back
  to the read-mode serif font
- The text-selection teardrop handle no longer hangs over the previous screen, still blinking,
  when navigating back out of a settings page (or the capture template editor) while a text field
  is focused
- Refiling a note out from under the Read-mode or Editor screen now waits for the move to
  actually land (and its undo window to close) before leaving, instead of navigating away the
  instant the picker closed — which risked cancelling the refile's file write mid-flight

## [1.0.240] - 2026-08-10

### Changed
- Removed the Synthwave, Nord, and Dracula themes (and their app-icon variants)
- The theme dropdown's active-theme checkmark and the outline screen's overflow-menu toggles now
  use real Material check/checkbox icons instead of raw "✓" text glyphs, matching the checkmark
  style used elsewhere in the app (e.g. the notebooks list top bar's synced indicator)
- Removed the Sentry crash-reporting integration entirely (Gradle plugin, manifest config, CI
  upload step)

### Fixed
- Preface, properties-drawer, and logbook keys are now visibly colored in Grove Dark, Tokyo Day,
  Catppuccin Latte, Rosé Pine Dawn, and Rosé Pine Moon — their key color had been left equal to
  the body-text color, making keys blend into values

## [1.0.236] - 2026-08-08

### Fixed
- The metadata sheet's and the state-change sheet's (swipe reveal row "state" action) keyword
  chips now always tint by done/active state, not just the currently-selected chip, matching the
  keyword badges shown everywhere else in the app (Outline rows, Search results, Agenda rows)
- The Agenda screen's Today/Upcoming tabs now have proper spacing below them; the overdue card and
  list no longer sit flush against the tab bar
- The Agenda screen's Group by and Show (state) levers are now tracked separately for the Today and
  Upcoming tabs; changing one no longer silently re-buckets or re-filters the other tab, and
  Upcoming still defaults to grouping by date

### Changed
- The Agenda screen's selected Today/Upcoming tab is now marked with a plain darker fill instead of
  an accent-colored outline
- The Agenda screen's ⇅ levers button now highlights with an accent tint while its panel is open

## [1.0.234] - 2026-08-07

### Added
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

## [1.0.219] - 2026-08-07

### Added
- Phone numbers written in prose (e.g. `833-806-1627`, `(833) 806-1627`, `+1 833-806-1627`) are
  now detected in read mode, shown reformatted as `(833) 806-1627`, and tappable to place a call —
  no `[[tel:...]]` link syntax required

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
