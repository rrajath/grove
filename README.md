# Grove

**A native Android Org-mode note-taking app — a first-class mobile companion for Emacs org-mode users.**

Grove edits plain `.org` files in a folder you choose. There is no account, no proprietary database, and no export step: the files on disk *are* your notes, byte-for-byte, and they sync to your laptop with whatever tool you already trust (Syncthing is the recommended pairing). If you stop using Grove tomorrow, your notes are exactly where they always were.

## Highlights

- **File-first** — `.org` files in a synced folder are the sole source of truth. Grove's internal database is only a rebuildable search index.
- **Lossless org engine** — a custom parser models documents as a thin view over the raw text. Parse → serialize is byte-identical by construction, so Grove never reformats a file it didn't deliberately edit.
- **Zero-friction capture** — org-capture style templates with `%U`, `%^{prompt}`, `%cursor`-style placeholders; targets include top/bottom of file, under a heading (by name or `CUSTOM_ID`), and year/month/day datetrees. Reachable from a home-screen widget, the share sheet, an optional persistent notification, and `grove://capture` deep links.
- **Real editor** — raw org subtree editing with syntax highlighting, a formatting toolbar, list continuation on Enter, a metadata sheet (TODO state, priority, tags with autocomplete, SCHEDULED/DEADLINE pickers), repeater advancement on DONE, and autosave with a stale-file guard.
- **Outline operations** — collapsible heading tree with body previews, expand/collapse all, move/cut/copy/paste subtrees, cycle state by swipe, narrow to a subtree.
- **Orgzly-compatible search** — `i.todo s.7d t.work .b.archive OR p.a`, saved searches, and an `ad.N` agenda view. See [docs/search-syntax.md](docs/search-syntax.md).
- **Sync that respects your tools** — change detection by file revision, Syncthing `.sync-conflict-*` detection with a keep-local / keep-remote / keep-both picker, manual through continuous sync modes, `.orgzlyignore` support.
- **A warm, deliberate design** — IBM Plex Sans/Serif/Mono, an earth-tone palette with full dark mode, and org syntax tokens colored the way an Emacs theme would.

## Getting started

### Requirements

- Android 14+ (`minSdk 34`)
- To build: JDK 17+, Android SDK 36. Open in Android Studio, or:

```bash
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # lint
```

### First run

1. Launch Grove and pick your org folder (any folder reachable through Android's file picker).
2. Point Syncthing — or any other sync tool — at the same folder to share it with your other machines.
3. Capture something.

## Project layout

```
app/src/main/java/com/rrajath/grove/
├── org/        Org-mode engine: parser, mutations, timestamps, line editing
├── vault/      File access: FileStore abstraction, SAF + JVM impls, Vault facade
├── sync/       Sync engine, state machine, conflict handling, Android triggers
├── data/       Room index (rebuildable cache) over the vault
├── capture/    Capture templates, placeholder expansion, entry insertion
├── search/     Orgzly-style query parser, matcher, snippets, saved searches
├── settings/   Preferences (DataStore) and settings repository
├── widget/     Home-screen capture widget + persistent capture notification
└── ui/         Jetpack Compose screens, view models, theme
```

## Documentation

| Document | What it covers |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Layers, data flow, threading, and the invariants that hold the app together |
| [docs/terminology.md](docs/terminology.md) | Glossary of org-mode and Grove-specific terms used throughout the code |
| [docs/design-decisions.md](docs/design-decisions.md) | Why things are the way they are, with the trade-offs considered |
| [docs/search-syntax.md](docs/search-syntax.md) | Full reference for the search query language |
| `prd-android-orgmode-app.md` | The original product requirements document |
| `design/README.md` | Pixel-level design spec: color tokens, typography, all screens |
| `MILESTONES.md` | Development milestone tracker (M1–M7, v1 descopes, v2 backlog) |

## Tech stack

Kotlin 2.2 · Jetpack Compose (Material 3) · Room · DataStore · WorkManager · Glance · kotlinx.serialization · JUnit 4

## Fonts

Grove bundles IBM Plex Sans, Serif, and Mono, licensed under the SIL Open Font License (see `FONT_LICENSE_OFL.txt`).
