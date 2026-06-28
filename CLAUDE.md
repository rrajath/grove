# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease

# Unit tests
./gradlew test
./gradlew testDebugUnitTest                        # debug variant only
./gradlew testDebugUnitTest --tests "com.rrajath.grove.SomeTest"  # single test

# Instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Lint
./gradlew lint
./gradlew lintDebug
```

## Project Overview

Grove is a native Android Org-mode note-taking app — a first-class mobile companion for Emacs org-mode users. Notes are always plain `.org` files on disk (file-first philosophy). The app is in **early scaffold stage**: `MainActivity.kt` is a placeholder; all features remain to be built.

**Key reference documents:**
- `prd-android-orgmode-app.md` — full product requirements
- `design/README.md` — pixel-accurate design spec (color tokens, typography, all 11 screens)
- `design/Grove.dc.html` — interactive high-fidelity prototype (open in browser)

## Architecture

### Stack
- Kotlin, Jetpack Compose, Material 3
- `minSdk 34`, `compileSdk 36`, AGP 9.0.1, Kotlin 2.0.21
- Package: `com.rrajath.grove`

### Layered architecture (from PRD §13)

**Data layer**
- `.org` files in the configured sync directory are the sole source of truth — never a database
- SQLite via Room is a rebuild-able index only (FTS5 for full-text search, search cache)
- Evaluate `org-java` or other open-source parsers before writing a custom one

**Sync layer**
- `WorkManager` for periodic/boot-triggered background sync (respects Doze)
- `FileObserver` for local-directory change detection
- Foreground `Service` with notification when Continuous sync mode is active
- All I/O on `Dispatchers.IO` coroutines; never block the UI thread
- Sync state machine: Idle → Checking → Pulling → Merging → Pushing → Done/Conflict/Error
- Supported backends: Local Directory (recommended, pairs with Syncthing), WebDAV, Dropbox

**UI layer**
- Jetpack Compose for all UI; no XML layouts
- `ModalNavigationDrawer` and `ModalBottomSheet` (Material 3) for drawer and capture picker
- `NavHost` with deep links (`grove://note/{id}`) for widget/notification shortcuts
- ViewModels + `StateFlow` for state; see `AppState` in `design/README.md`
- Dark mode follows system (with manual override)

### Navigation routes
```
onboarding
notebooks
outline/{notebookId}
note/{noteId}?mode=read
note/{noteId}?mode=edit
capture
capture/{templateId}
search
conflict/{notebookId}
settings
```

## Design System
Full reference: `docs/DESIGN_SYSTEM.md`
Whenever a task adds, modifies, or styles any UI element, read that file first and follow it. Don't invent colors, spacing, or components it doesn't cover — ask instead.

## Editor Implementation Notes

- Use `BasicTextField` with a custom `VisualTransformation` to tokenize org lines and apply `SpanStyle` per syntax token in edit mode.
- For read mode, build a custom `AnnotatedString` renderer mapping org AST nodes to Compose composables (`Text`, `Column`, etc.); no `WebView`.
- Org table rendering is deferred to v2 — show as monospace plain text in v1.

## Key Design Decisions (from PRD §15)

| Decision | Resolution |
|---|---|
| Org parser | Use existing library (org-java or similar); custom parser only if the library has an unfixable gap |
| Conflict resolution v1 | Conflict picker UI (keep local / keep remote / keep both); no auto-merge in v1 |
| Template "under heading" target | Offer both exact name and `CUSTOM_ID`; mark `CUSTOM_ID` as recommended |
| Sync recommendation | Syncthing + local directory (peer-to-peer, no account limits) |
