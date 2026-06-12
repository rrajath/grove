# Grove — Milestone Tracker

Each milestone ends with a git commit, gated on both commands passing:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Plan reference: PRD in `prd-android-orgmode-app.md`, design spec in `design/README.md`.

---

## M1 — Foundation: design system, navigation shell, identity ✅

- [x] git init, baseline commit of scaffold
- [x] Full design-token palette (light/dark, syn-* colors) as `GroveColors` + `MaterialTheme.grove`
- [x] IBM Plex Sans/Serif/Mono bundled in `res/font` (OFL license at repo root)
- [x] Grove `Typography` per design spec, scaled by font-size preference
- [x] Adaptive launcher icon (3-bar asterisk mark)
- [x] `GroveNavHost` with all routes; placeholder screens for later milestones
- [x] Navigation drawer per spec
- [x] Onboarding screen (static; folder picker lands in M2)
- [x] Notebooks home chrome (app bar, sync strip, Capture FAB, empty state)
- [x] Settings: Appearance group functional (theme / font size / default note mode via DataStore)
- [x] Unit tests: route builders, preference enums
- [x] Verified: tests + build green, committed

## M2 — Org engine + vault: browse real notes ✅

- [x] Lossless org parser (`org/`): OrgDocument slice model, parser, serializer, timestamps + repeaters, inline tokenizer
- [x] Round-trip byte-identical tests over golden fixtures
- [x] `FileStore` interface + `SafFileStore` (SAF tree, "wt" writes); onboarding folder picker
- [x] `.orgzlyignore` support
- [x] Notebook list with real files; create notebook
- [x] Outline view (read-only): expand/collapse, chips, tags, fold counts
- [x] Read mode: rendered headings/body, inline markup, links; tables as plain mono text
- [x] Verified: 67 unit tests green, assembleDebug green, committed

## M3 — Capture (priority feature #1) ✅

- [x] Placeholder expansion (`%t %T %u %U %date %time %day %year %month %cursor %? %^{prompt} %clipboard %shared_text %shared_url`)
- [x] Target locations: top/bottom of file, under heading (name or CUSTOM_ID, first/last child), datetree (date & datetime)
- [x] Capture picker bottom sheet + capture editor (prompt dialog, datetree breadcrumb, cursor at %cursor) + Save creates target file if missing
- [x] Built-in default templates (Journal Entry, Quick Note, TODO)
- [x] Settings → Templates management (edit/reorder/delete + template editor screen)
- [x] Verified: 97 unit tests green, assembleDebug green, committed
- Note: template editor target-file field is free text in v1; notebook picker dropdown deferred to polish (M7)

## M4 — Sync engine (priority feature #2) + Room index

- [ ] `SyncBackend` interface + `LocalDirectoryBackend` (v2 backends slot in here)
- [ ] `SyncEngine` state machine + `ChangeDetector` (mtime/size snapshots)
- [ ] Auto-sync modes: manual / on open-close / periodic (WorkManager) / foreground polling
- [ ] Room index (notebooks, notes, revisions) — rebuildable cache
- [ ] Conflict picker (keep phone / laptop / both → CONFLICT heading) + notification + badges
- [ ] Force Load / Force Save; rename/delete-to-trash; sync log

## M5 — Full editor

- [ ] Raw editor with syntax highlighting (VisualTransformation) + formatting toolbar
- [ ] Metadata sheet: state / priority / tags / SCHEDULED / DEADLINE with org-valid timestamps
- [ ] Autosave on navigate-away; tag & keyword autocomplete
- [ ] Repeater advancement on DONE (`+`, `++`, `.+`)
- [ ] Outline structural ops: new sub-note, move, cut/copy/paste, delete, swipe actions
- [ ] Settings → Notes group

## M6 — Search, saved searches, agenda

- [ ] Query parser for Orgzly syntax (s. d. c. cr. i. b. t. tn. p. / AND OR NOT / o. sort / ad.N / period aliases)
- [ ] FTS4 index + snippets + ranking + 300ms debounce + history
- [ ] Search screen with match highlighting + Advanced panel
- [ ] Saved searches in drawer (defaults: Scheduled Today, All TODO, This Week)
- [ ] Agenda view (day-grouped)

## M7 — Zero-friction capture surfaces + polish

- [ ] Capture home-screen widget (Glance), configurable pinned template
- [ ] Share-sheet target (text/URL; images → attachments dir)
- [ ] Deep links `grove://note/{id}`; notification + lock-screen capture shortcuts
- [ ] Outline drag-and-drop reorder (descopable)
- [ ] Display toggles, sync log viewer, settings completion, design QA pass
