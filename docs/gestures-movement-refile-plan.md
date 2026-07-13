# Outline Gestures, Movement & Refile

## Context

Grove's outline screen currently has minimal gestures: swipe-right = cycle TODO state, swipe-left = narrow, long-press = NodeMenu dropdown. The prototype (`design/Grove.dc.html`, Gestures screen — markup lines ~929–1050, JS ~1252–1546) specifies a much richer interaction model, and the user wants it built to pixel-match:

- **Swipe-to-reveal action panels** on heading rows (right-swipe → state actions; left-swipe → structure actions), tap to trigger.
- **Long-press → "Move & indent" command bar** replacing the top bar: move up/down, promote/demote, plus (user-added) delete, with ✕/✓ to exit. The old NodeMenu dropdown is **removed entirely**.
- **Refile** to the top level of any org file or under any heading in any file (including the current one), via a two-step bottom sheet with **drill-down-per-level** navigation (user choice).
- **Undo snackbar** for structural ops, **toast** for state ops / blocked moves.

User-confirmed decisions: promote/demote move the **whole subtree**; Schedule/Deadline open the **M3 date picker** (pre-filled); demote blocked with no previous same-level sibling; promote blocked at level 1. Move up/down must swap whole sibling subtrees — a heading never travels through its own or another heading's descendants (existing `OrgMutations.moveSubtree` already does this correctly).

## Prototype spec extraction (source of truth for visuals)

- **Swipe physics**: panel width 184dp (4 cells × 46dp), open threshold 66dp, rubber-band factor 0.18 past 184, settle animation 340ms `CubicBezierEasing(0.22, 1, 0.36, 1)`, direction-lock (vertical scroll unaffected), tap on open card closes it, only one row open at a time, long-press 430ms only when closed.
- **Right-swipe panel** (anchored left): State `⟳` amber/amberSoft · Schedule `◷` blue/blueSoft · Deadline `⚑` red/redSoft · Favorite `★` accent/accentSoft. Each cell: glyph 15–17sp over 9sp ink2 label.
- **Left-swipe panel** (anchored right): Above `↑+` blue/blueSoft · Below `↓+` blue/blueSoft · Sub-note `↳` green/greenSoft · Refile `➜` accent/accentSoft.
- **Focused row**: 2dp accent outline (inset), 12dp radius, surface bg, shadow, z-lifted.
- **Command bar**: 56dp, accentSoft bg, 1dp bottom line. ✕ 40dp circle accent · "Move & indent" 13.5sp SemiBold accent (weight 1) · 38dp/10dp-radius buttons ↑ ↓ ⇤ ⇥ · **delete** (red glyph, user addition) · ✓ 38dp accent bg / accentInk fg.
- **Refile sheet**: top radius 20dp, drag handle, header (optional ← 36dp circle when inside a notebook; "Refile 1 note" 16sp SemiBold; crumb 12sp ink2), scrollable rows (12dp radius, 1dp border — accent border + accentSoft bg when selected; glyph `▤` notebooks / `✳` headings, accent PlexMono 15sp; label PlexMono 14.5sp Medium; sub "N headings" 11.5sp ink3; 8dp gaps, 12–14dp padding), footer with top divider: Cancel (surface-2, line border, ink2) + "Refile here" (weight 1, accent/accentInk when enabled, surface-2/ink3 disabled). Crumbs: "Choose a destination notebook" → "file › top level · or pick a heading" → "file › A › B".
- **Toast**: bottom-center dark pill — ink bg, bg-color text 13sp Medium, 10×16dp padding, 20dp radius, ~1.9s. Messages: "Can't move further", "Already top level", "Can't demote further", "★ Added to favorites", "Removed favorite", "State → X", "Undone".
- **Undo snackbar**: bottom, 14dp side margins, ink bg, 12dp radius, message 13.5sp + "UNDO" 13sp Bold accent, ~4.2s. For: move, promote, demote, delete, refile.

## Implementation

### 1. `org/OrgMutations.kt` — pure mutations (+ tests first)

- **`moveSubtree`**: change return type `String?` → `Pair<String, Int>?` (new text + heading's new lineIndex). Up: new line = `other.lineIndex`; down: `h.lineIndex + (subtreeEndLine(other) - other.lineIndex)`. Swap logic unchanged.
- **`promoteSubtree(doc, h): String?`** — null if `h.level == 1`; else re-star every headline line in `[h.lineIndex, subtreeEndLine(h))` by −1.
- **`demoteSubtree(doc, h): String?`** — null if `h` has no previous same-level sibling (compute siblings the same way `moveSubtree` does); else shift subtree by +1.
- Extract private **`relevelSubtree(lines, targetLevel)`** from `pasteUnder`'s re-star logic (`STARS_LINE`); refactor `pasteUnder` onto it (no behavior change).
- **`refileInsert(doc, target: OrgHeadline?, subtree): Pair<String, Int>`** — `target != null`: as last child releveled to `target.level + 1` (pasteUnder semantics, but returns insert lineIndex); `target == null`: releveled to 1, appended at EOF (mirror `insertNote`'s trailing-empty-line adjustment).
- **`refileWithinFile(doc, source, targetLine: Int?): Pair<String, Int>?`** — null if `targetLine` inside source's own subtree; else one pipeline: `subtreeText` → `deleteSubtree` → reparse → adjust `targetLine` by −(subtree size) when it was after the source → `refileInsert`. Never delete-then-insert against the stale doc.

**Tests** (extend `app/src/test/java/com/rrajath/grove/org/OrgMutationsTest.kt`, existing fixture style): moveSubtree new-lineIndex up/down incl. past a deep sibling subtree; null at first/last sibling; promote shifts whole subtree / null at level 1 / bodies byte-identical; demote shifts subtree / null for first child and first top-level; refileInsert to null target (files with and without trailing blank line) and under a nested heading; refileWithinFile source-before-target, source-after-target, into-own-subtree null; snapshot-restore equals original text.

### 2. `ui/vault/VaultViewModels.kt` — `DocumentViewModel`

- **Events**: `data class OutlineToast(message, id)` / `OutlineSnack(message, id)` as `StateFlow`s with ViewModel-side timers (`delay(1900)` / `delay(4200)`, clear if same id). Public `showToast(msg)`.
- **Undo**: `UndoSnapshot(files: List<Pair<fileName, text>>)`, single-step, captured before each undoable mutation (same-file ops snapshot in-memory `loaded.document.text`; cross-file refile snapshots both). `undo()` → save all snapshotted files, reparse current file into `_state`, request sync, clear snack, toast "Undone".
- **Focus**: `focusedLine: StateFlow<Int?>` + `setFocus(Int?)` — in the VM so it updates atomically with `_state` after mutations (moveUp/Down set it to the returned new line when it was focused; delete/refile clear it).
- **Ops**: `moveUp/moveDown` use new pair return (edge → toast "Can't move further"; success → snack "Moved up/down"); `promote/demote` (toasts "Already top level"/"Can't demote further"; snacks "Promoted"/"Demoted"); `deleteSubtree` gains snapshot + snack "Note deleted" + focus clear + removal of favorites inside the deleted line range; `setScheduled(h, ts?)`/`setDeadline(h, ts?)` thin `mutate` wrappers (toast "Scheduled · Tue, Apr 15" via `EEE, MMM d`); `cycleState` gains toast.
- **`refile(source, destFile, targetLine: Int?)`**: same file → `refileWithinFile`; cross-file → `vault.open(destFile)`, snapshot both, `subtreeText`+`deleteSubtree` source / `refileInsert` dest, save both, refresh `_state`; snack `"Refiled to file › heading"` (or "› top level"); clean up favorites in moved range.
- **Refile picker state**: `RefileUiState(sourceLine, notebooks: List<RefileNotebook>?, pickedFile: String?, pickedDoc: OrgDocument?, path: List<Int>)` + `startRefile(h)` (loads `vault.notebooks()`), `refilePickNotebook` (`vault.open` off-main; current file reuses loaded doc), `refileDrillInto(line)`, `refileBack()` (pop path / back to notebook list), `refileCancel()`, `refileConfirm()` → `refile(source, pickedFile, path.lastOrNull())`. Tapping a heading both selects and drills (confirmed drill-down behavior). Rows inside the source's own subtree are filtered out for same-file refile.

### 3. New `ui/components/SwipeRevealRow.kt`

`SwipeRevealRow(leftActions, rightActions, enabled, onTap, onLongPress, content)` with `SwipeAction(glyph, label, fg, bg, onClick)`:
- `Box(clipToBounds)`: behind, two 4×46dp cell rows (left/right anchored); front card in `graphicsLayer { translationX = offset.value }` with opaque bg.
- `Animatable` offset; `Modifier.draggable(Horizontal)` (touch-slop arbitration gives the pan-y behavior; drag start cancels pending long-press); rubber-band `max + (x − max) * 0.18`; settle to ±184dp when `|offset| ≥ 66dp` else 0, `tween(340, CubicBezierEasing(0.22f, 1f, 0.36f, 1f))`.
- `combinedClickable`: `onClick` closes if open else `onTap`; `onLongClick` only when closed. Modifier order: clickable before draggable.
- Hoisted `openRowLine: Int?` in OutlineScreen keeps a single row open; `LaunchedEffect(doc)` snaps offset shut after any mutation. Action tap closes then fires.

### 4. `ui/screens/OutlineScreen.kt`

**Remove**: `SwipeToDismissBox` block (~387–434), `NodeMenu` (~586–637), `InsertMenu` (~641–666) + their state, `narrowedTo` state + banner + `visibleHeadlines` narrow param (narrow becomes unreachable — accepted), and slim `NodeOps` down to the surviving ops.

**Add**:
- Wrap each row in `SwipeRevealRow`. Right panel → `cycleState`, schedule picker, deadline picker, favorite toggle. Left panel → `insertSiblingAbove/Below`, `newChild`, `startRefile`. Inserts keep the existing navigate-to-editor flow (no undo snack for inserts — undoing mid-edit would discard typing; deliberate deviation).
- **Focus mode**: long-press → `viewModel.setFocus(h.lineIndex)`; focused row styled per spec; swipe disabled on all rows while focused; `BackHandler` exits.
- **`StructureCommandBar`** private composable in `Scaffold.topBar` swap (`focusedLine != null`). All button handlers resolve `doc.headlineAtLine(focusedLine)` fresh at click time (headlines are stale after every mutation — `headlineAtLine` already exists at VaultViewModels.kt:318).
- **Date pickers**: `DatePickerDialog` per the `MetadataSheet.kt` (~155–177) pattern, `initialSelectedDateMillis` pre-filled from `h.planning.scheduled/deadline`.
- **Overlays**: `GroveToast` / `GroveUndoSnackbar` (new `ui/components/Feedback.kt`) rendered in the Loaded branch root Box, driven by `viewModel.toast`/`viewModel.snack`.
- **Refile sheet**: new `ui/screens/RefileSheet.kt`, `ModalBottomSheet(containerColor = c.surface, RoundedCornerShape(topStart/End = 20.dp))` per the spec extraction above. Step-1 heading counts from `vault.notebooks()`' existing `noteCount`.

### 5. `ui/GroveApp.kt` — favorite toggle

Change outline's `onFavorite` wiring (~line 173) to a toggle: `if (isFavorite) appViewModel.removeFavorite(...) else addFavorite(...)`; toasts via `viewModel.showToast`. Favorite lineIndex drift under structural edits is pre-existing; only the cheap piece ships now (drop favorites inside deleted/refiled ranges) — the general remap is documented as a known limitation.

## Files

| File | Change |
|---|---|
| `org/OrgMutations.kt` | moveSubtree return pair; promoteSubtree, demoteSubtree, relevelSubtree, refileInsert, refileWithinFile |
| `ui/vault/VaultViewModels.kt` | toast/snack/undo, focusedLine, new ops, refile + picker state |
| `ui/screens/OutlineScreen.kt` | remove SwipeToDismissBox/NodeMenu/InsertMenu/narrow; wire everything |
| `ui/components/SwipeRevealRow.kt` | **new** |
| `ui/components/Feedback.kt` | **new** — GroveToast, GroveUndoSnackbar |
| `ui/screens/RefileSheet.kt` | **new** |
| `ui/GroveApp.kt` | favorite toggle wiring |
| `app/src/test/.../OrgMutationsTest.kt` | updated + new tests |
| `docs/DESIGN_SYSTEM.md`, `PROGRESS.md`, docs | update after implementation (gesture components are new inventory) |

Order: OrgMutations + tests → ViewModel plumbing → SwipeRevealRow → OutlineScreen wiring → RefileSheet → GroveApp → visual polish vs prototype.

## Verification

1. `./gradlew testDebugUnitTest` (new mutation tests) and `./gradlew assembleDebug`.
2. On emulator vs prototype in browser: swipe physics (threshold, rubber band, settle, tap-close, one-open-at-a-time, vertical scroll unaffected); long-press → command bar (repeat moves, subtree integrity, edge toasts, promote/demote subtree, delete + UNDO, ✕/✓/back); schedule/deadline prefill + write; favorite toggle toasts; refile — other file top level, other file nested with breadcrumb growth and back-popping, same file both directions, own-subtree rows excluded, disabled→enabled footer, cross-file UNDO restores both files.
3. Confirm removals: no dropdown on long-press, no narrow entry point.

## Risks

- Stale `OrgHeadline` after any mutation → always re-resolve via `headlineAtLine(focusedLine)`.
- `collapsed: Set<Int>` fold state drifts on structural edits (pre-existing; moves make it visible) — if trivial, remap the two swapped ranges on move; otherwise document.
- Undo overwrites files; a sync pulling changes inside the 4.2s window would be clobbered — accepted for v1.
- Cross-file refile reuses the picker's parsed dest doc at confirm — acceptable v1 freshness window.
