# CLAUDE.md

Guidance for Claude Code when working in this repository (Grove, a native Android org-mode app).

## Commands

```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew bundleRelease                             # release AAB

# Version (single source of truth: gradle.properties `versionName`)
./gradlew -q printVersionName
./gradlew -q printVersionCode

# Unit tests
./gradlew test
./gradlew testDebugUnitTest
./gradlew testDebugUnitTest --tests "com.rrajath.grove.SomeTest"

# Instrumented tests (needs connected device/emulator; Espresso + Compose UI test, no Robolectric)
./gradlew connectedAndroidTest

# Lint
./gradlew lint
./gradlew lintDebug
```

Docs site (`docs-site/`) is a separate Astro/Starlight project with its own `package.json` and its own CLAUDE.md — run `npm run dev` / `build` / `deploy` from inside `docs-site/`, not from the root.

## Project Overview

Grove is a native Android org-mode note-taking app for Emacs org-mode users. `.org` files on disk are the sole source of truth — never a database. Todo keywords, tags, priorities, schedule/deadline + repeaters, refiling, capture templates, agenda, and full-text search are all built on a hand-written org parser (no external org-mode library is used).

**Reference docs** (check before assuming — these are the source of truth, not this file):
- `internal-docs/prd-android-orgmode-app.md` — full PRD (gitignored, local only)
- `internal-docs/DESIGN_SYSTEM.md` — design tokens, typography, component specs. Read before touching any UI element; don't invent colors/spacing it doesn't cover — ask instead.
- `docs/architecture.md` — architecture writeup (tracked, kept current)
- `gradle/libs.versions.toml` / `app/build.gradle.kts` — exact stack versions; don't trust a hardcoded version number in this file, check there instead

## Architecture

### Layers
**Data** — `.org` files in the vault directory are the sole source of truth. Room (`data/GroveDatabase.kt`) is a rebuildable index only (notes table, FTS-backed search cache, sync log, reminders) — never treat it as authoritative.

**Sync** (`sync/`, `vault/`) — `WorkManager` for periodic/boot sync, `FileObserver` for local-directory change detection, foreground `Service` when Continuous mode is active. All I/O on `Dispatchers.IO`. State machine: Idle → Checking → Pulling → Merging → Pushing → Done/Conflict/Error. **Only the Local Directory backend (SAF, pairs with Syncthing) is implemented.** WebDAV/Dropbox are unbuilt v2 ideas mentioned only in a doc comment — don't assume they work.

**UI** — Jetpack Compose only, no XML layouts. `ModalNavigationDrawer`/`ModalBottomSheet` (M3) for drawer/capture picker. ViewModels + `StateFlow`. Dark mode follows system with manual override. `NavHost` lives in `ui/GroveApp.kt`; the route table is `ui/nav/Routes.kt` — treat that file as the source of truth for routes, not any list here (it grows with almost every feature).

### Where things live (`app/src/main/java/com/rrajath/grove/`)

| Path | What's there |
|---|---|
| `org/` | Hand-written org-mode parser: tokenizer, blocks, tables, timestamps, mutations (`OrgParser.kt`, `OrgTable.kt`) |
| `sync/` | Sync engine/state machine, conflict detection (`SyncEngine.kt`, `SyncConflicts.kt`) |
| `vault/` | Filesystem abstraction over the vault — `FileStore` interface, `SafFileStore`/`JvmFileStore` impls |
| `capture/` | Share-sheet intake, capture templates (`ShareReceiverActivity.kt`, `TemplatesRepository.kt`) |
| `reminders/` | Alarms, notifications, digest scheduling, boot rescheduling |
| `search/` | Full-text/query search over the note index |
| `data/` | Room database — all entities/DAOs (`GroveDatabase.kt`) |
| `settings/` | Preferences persistence/serialization |
| `widget/` | Home-screen Glance widgets (`LedgerWidget.kt`, `CaptureWidget.kt`) |
| `icon/`, `whatsnew/` | App icon/notification appearance; changelog parsing for the What's New dialog |
| `ui/nav/` | Route table + nav transitions — **source of truth for routes** |
| `ui/screens/` | Top-level screens (largest UI dir) |
| `ui/screens/settings/` | Individual settings sub-screens (~10) |
| `ui/editor/` | Org text editor (syntax highlight, toolbar) |
| `ui/components/` | Shared composables |
| `ui/capture/`, `ui/search/`, `ui/vault/`, `ui/agenda/`, `ui/reminders/`, `ui/newbadge/`, `ui/theme/` | Feature-scoped screens/viewmodels |

When a feature isn't in this table, grep inside the closest matching package before searching the whole tree.

## Editor Implementation Notes
- Edit mode: `BasicTextField` + a custom `VisualTransformation` tokenizes org lines and applies `SpanStyle` per syntax token.
- Read mode: a custom `AnnotatedString`/composable renderer maps the org AST to `Text`/`Column` etc. — no `WebView`.
- Org tables in Read mode: `OrgTableView` (`ui/components/OrgTableView.kt`) + `parseOrgTable` (`org/OrgTable.kt`) — pinned bold header row, shared horizontal scroll, height-capped scrolling body. Edit mode still shows raw `| a | b |` text. Cell-level inline markup and column alignment are unimplemented.

## Conventions
- Settings pages and the Import/Export Settings workflow must be updated together — any add/change/delete in Settings needs a matching change to import/export.
- After every commit, add a short (1-2 sentence) entry to `CHANGELOG.md` under `[Unreleased]`. A GitHub Actions release (on a `v*.*.*` tag) archives that section automatically.
- No ktlint/detekt/Spotless is configured — match surrounding style by hand.

## Key Design Decisions
| Decision | Resolution |
|---|---|
| Org parser | Custom hand-written parser (`org/`) — no external org-mode library is used |
| Sync v1 | Local Directory backend only; conflict picker UI (keep local / keep remote / keep both), no auto-merge |
| Template "under heading" target | Offer both exact heading name and `CUSTOM_ID`; `CUSTOM_ID` is recommended |
