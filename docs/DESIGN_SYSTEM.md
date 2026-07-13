# Grove Design System

This document is the single source of truth for the Grove design system as implemented in code.
Cross-reference `design/README.md` for the original pixel-accurate spec and `Grove.dc.html` for
the interactive prototype.

---

## Color Tokens

All tokens live in `GroveColors` (`ui/theme/Color.kt`). Access them via `MaterialTheme.grove`
inside any `@Composable`. Never hardcode hex values — always reference a token.

```kotlin
val c = MaterialTheme.grove
Box(Modifier.background(c.surface)) { Text("hello", color = c.ink) }
```

### Surface / Background

| Token | Light | Dark | Usage |
|---|---|---|---|
| `bg` | `#f3ede1` | `#16130e` | Scaffold and screen background |
| `surface` | `#fbf8f1` | `#201c15` | Cards, elevated panels, sheets |
| `surface2` | `#ece4d5` | `#2a251b` | Inset areas, secondary panels, code spans |
| `surface3` | `#e3d9c6` | `#352f23` | Pressed state, tertiary inset |

### Text / Ink

| Token | Light | Dark | Usage |
|---|---|---|---|
| `ink` | `#2a251f` | `#ece4d5` | Primary body text |
| `ink2` | `#6c6356` | `#b4a98f` | Subtitles, secondary labels, hints |
| `ink3` | `#9c9384` | `#7c7460` | Timestamps, placeholders, muted labels |

### Dividers / Borders

| Token | Light | Dark | Usage |
|---|---|---|---|
| `line` | `#e3dbcb` | `#322c20` | Default dividers, card borders |
| `line2` | `#d3c9b6` | `#433a2b` | Stronger dividers, drag handles |

### Accent (brand)

| Token | Light | Dark | Usage |
|---|---|---|---|
| `accent` | `#8a5a2b` | `#cb9d62` | Primary interactive color — buttons, active chips, FAB |
| `accentInk` | `#fffaf2` | `#1a160d` | Text on accent-colored backgrounds |
| `accentSoft` | `#8a5a2b` @ 0.18α | `#cb9d62` @ 0.18α | Chip backgrounds, icon tiles, subtle accent fill |

### Semantic Colors

Each semantic color has a full-opacity foreground and a soft (~0.14–0.18α) background variant.

| Token | Light | Dark | Usage |
|---|---|---|---|
| `green` / `greenSoft` | `#4f7a3a` / 0.14α | `#8fb46a` / 0.17α | DONE state, synced badge, success |
| `amber` / `amberSoft` | `#a9761d` / 0.16α | `#d7a64f` / 0.17α | TODO state, Modified badge, warnings |
| `red` / `redSoft` | `#a5462f` / 0.13α | `#d2856a` / 0.16α | Priority A, overdue deadlines, errors |
| `blue` / `blueSoft` | `#3f6f86` / 0.13α | `#7fb0c4` / 0.16α | Timestamps, IN-PROGRESS state, keywords |
| `violet` | `#7a6db8` | `#ad9bd1` | Heading level 5 (outline depth cycle) |

### Org Syntax Highlighting

Used exclusively for colorizing `.org` source text. Do not use these for UI chrome.

| Token | Light | Dark | Usage |
|---|---|---|---|
| `synStar` | same as `green` | same as `green` | Heading asterisks `*`, `**`, `***` |
| `synTodo` | same as `amber` | same as `amber` | TODO/DONE keyword text in editor |
| `synDone` | same as `green` | same as `green` | DONE keyword in editor |
| `synKw` | `#8a7a5c` | `#b4a98f` | Planning keywords (SCHEDULED, DEADLINE) |
| `synTs` | same as `blue` | same as `blue` | Timestamps `<…>` and `[…]` |
| `synTag` | same as `accent` | same as `accent` | `:tags:` |
| `synLink` | same as `blue` | same as `blue` | `[[links]]` |
| `synProp` | `#8a7a5c` | `#7c7460` | Property drawer keys |

### Heading-Star Color Cycle

`GroveColors.starColor(level)` returns the color for an outline heading by nesting depth,
cycling through: **green → blue → amber → red → violet → accent** and back.

```kotlin
val starColor = MaterialTheme.grove.starColor(headingLevel)
```

### Additional Themes

Beyond Light/Dark, `GroveColors` ships five more curated palettes (all dark-family, `isDark = true`),
each a full-opacity port of the named community theme. Full token values live in `ui/theme/Color.kt`
(`GroveTokyoNightColors`, `GroveSynthwaveColors`, `GroveDraculaColors`, `GroveCatppuccinColors`,
`GroveNordColors`) — this table covers the core tokens only.

| Theme | `bg` | `surface` | `ink` | `accent` | `green` | `amber` | `red` | `blue` | `violet` |
|---|---|---|---|---|---|---|---|---|---|
| Tokyo Night | `#1a1b26` | `#1f2335` | `#c0caf5` | `#7aa2f7` | `#9ece6a` | `#e0af68` | `#f7768e` | `#7dcfff` | `#bb9af7` |
| Synthwave | `#262335` | `#2a2140` | `#f8f8f2` | `#ff7edb` | `#72f1b8` | `#fede5d` | `#fe4450` | `#03edf9` | `#b967ff`* |
| Dracula | `#282a36` | `#2d2f3d` | `#f8f8f2` | `#bd93f9` | `#50fa7b` | `#ffb86c` | `#ff5555` | `#8be9fd` | `#bd93f9` |
| Catppuccin | `#1e1e2e` | `#292a3d` | `#cdd6f4` | `#cba6f7` | `#a6e3a1` | `#fab387` | `#f38ba8` | `#89b4fa` | `#f5c2e7` |
| Nord | `#2e3440` | `#333b4a` | `#eceff4` | `#88c0d0` | `#a3be8c` | `#ebcb8b` | `#bf616a` | `#81a1c1` | `#b48ead` |

\* Synthwave has no purple/violet in its source palette — this value is derived (blended between
its accent pink and blue) to keep the heading-star cycle's 5th color visually distinct.

### Material ColorScheme Mapping

`GroveTheme` bridges the custom tokens into Material 3 automatically:

| Material role | Grove token |
|---|---|
| `primary` | `accent` |
| `onPrimary` | `accentInk` |
| `primaryContainer` | `accentSoft` |
| `background` | `bg` |
| `surface` | `surface` |
| `surfaceVariant` | `surface2` |
| `surfaceContainerHigh` | `surface2` |
| `surfaceContainerHighest` | `surface3` |
| `outline` | `line2` |
| `outlineVariant` | `line` |
| `error` | `red` |

---

## Typography

Defined in `ui/theme/Type.kt`. Font families are bundled as TTF assets in `res/font/`.

### Font Families

| Variable | Typeface | Weights available | Use for |
|---|---|---|---|
| `PlexSans` | IBM Plex Sans | Normal, Medium, SemiBold | All UI chrome |
| `PlexSerif` | IBM Plex Serif | Normal, Medium, SemiBold | Read-mode body prose |
| `PlexMono` | IBM Plex Mono | Normal, Medium, SemiBold, Bold | Editor, file names, timestamps, code spans |

### Type Scale

Accessed via `MaterialTheme.typography.*` — `GroveTheme` populates all roles.

| Material role | Family | Weight | Size | Usage |
|---|---|---|---|---|
| `displayMedium` | PlexSans | SemiBold | 30sp | App name (onboarding only) |
| `titleLarge` | PlexSans | SemiBold | 19sp | Screen titles in app bars |
| `titleMedium` | PlexSans | SemiBold | 17sp | Notebook / file names |
| `bodyLarge` | PlexSerif | Normal | 16sp, lh 1.65 | Read-mode body paragraphs |
| `bodyMedium` | PlexSans | Normal | 15sp | List item text, settings rows |
| `bodySmall` | PlexSans | Normal | 13.5sp | Subtitles, descriptions |
| `labelLarge` | PlexSans | SemiBold | 15sp | Buttons, prominent labels |
| `labelMedium` | PlexSans | SemiBold | 13sp | Secondary buttons |
| `labelSmall` | PlexSans | SemiBold | 11.5sp | Chips, badges, section headers |

### Mono Body Utility

For anything rendered in monospace (editor buffer, timestamps, file paths, breadcrumbs):

```kotlin
// monoBody() is a TextStyle helper, not a MaterialTheme role
Text("travel.org", style = monoBody())          // 13.5sp normal
Text("*", style = monoBody().copy(fontWeight = FontWeight.Bold))  // heading stars
```

Font size scales with `FontSizePreference` (SMALL = 0.88×, MEDIUM = 1.0×, LARGE = 1.14×),
passed into `groveTypography(scale)` and `monoBody(scale)`.

### Org Inline Rendering

Use `annotateOrgInline(text, colors)` from `ui/components/OrgInlineText.kt` to convert org
inline markup to a Compose `AnnotatedString`. Handles bold, italic, underline, `=code=`,
`[[links]]`, and timestamps. Pass `onLink` to make links tappable.

```kotlin
Text(annotateOrgInline(heading.text, c, onLink = { url -> openUrl(url) }))
```

---

## Spacing & Layout

### Spacing Scale

| Name | Value | Examples |
|---|---|---|
| xs | 4dp | Gap between inline chips |
| sm | 8dp | Icon-to-label gap, pill internal vertical padding |
| md | 12–14dp | List item internal horizontal padding |
| lg | 16–18dp | Screen horizontal gutter, card internal padding |
| xl | 22–26dp | Section top padding, onboarding screen padding |

### Fixed-Height Regions

| Region | Height |
|---|---|
| App bar (`GroveTopBar`) | 56dp + status bar inset |
| Standard list row | 56–64dp |
| Extended FAB | 54dp |
| Formatting toolbar (editor) | 48dp |
| Bottom sheet drag handle | 4dp tall, 38dp wide |

### Screen Horizontal Gutter

- Onboarding: 26dp both sides
- Notebook / outline list: 16–18dp
- Read / edit note: 24dp
- Settings: 16dp

---

## Corner Radii

| Component | Radius |
|---|---|
| Bottom sheet top corners | 24dp |
| Large cards / modals | 18dp |
| App icon squircle | ~27% of tile size (27% × 104dp ≈ 28dp) |
| FAB (extended) | 17dp |
| FAB (icon-only) | 18dp |
| Cards, list tiles | 13–14dp |
| Conflict warning banner | 13dp |
| Small buttons (Save in capture) | 11dp |
| Notebook icon tiles | 12dp |
| Primary button (onboarding) | 14dp |
| Segmented control container | 10dp |
| Segmented control active pill | 8dp |
| Tags/keyword chips | 20dp (pill) |
| TODO/priority chips | 5dp |
| Inline code spans | 5dp |
| Search field | 13dp |
| Pills (`Pill` composable) | 20dp |

---

## Elevation & Shadow

Material elevation is kept at zero or minimal values; visual depth is achieved via background
color layering (`bg` → `surface` → `surface2` → `surface3`). When a shadow is needed:

- **Light mode**: `0 2px 10px rgba(60,45,25,0.10), 0 1px 2px rgba(60,45,25,0.06)`
- **Dark mode**: `0 6px 22px rgba(0,0,0,0.45), 0 1px 3px rgba(0,0,0,0.35)`
- **FAB**: `0 6px 18px rgba(138,90,43,0.40)` (light) — amber-tinted drop shadow

Use `Modifier.shadow()` only on the FAB and bottom sheet; everywhere else rely on color layering.

---

## Icon Conventions

### Material Icons

Grove uses Material Icons from the standard `androidx.compose.material.icons` library.
The default icon set (Filled style) is preferred. Use Outlined sparingly for secondary /
less-prominent actions.

| Screen / Context | Icon | Token |
|---|---|---|
| Hamburger / open drawer | `Icons.Default.Menu` | — |
| Back navigation | `Icons.Default.ArrowBack` | — |
| Search | `Icons.Default.Search` | — |
| Overflow menu | `Icons.Default.MoreVert` | — |
| Dismiss / close | `Icons.Default.Close` | — |
| Sync status — ok | `Icons.Default.Check` | `green` |
| Sync status — error | `Icons.Default.Warning` | `amber` |
| Sync — spinning | `Icons.Default.Sync` (animated) | `ink2` |
| Scheduled date | `Icons.Default.Schedule` | `blue` |
| Deadline | `Icons.Default.CalendarToday` | `red` |
| Drag handle (templates) | `Icons.Default.DragHandle` | `ink3` |
| Settings | `Icons.Default.Settings` | `ink2` |
| Agenda | `Icons.Default.ViewList` | `ink2` |

### Custom Drawables

| Resource | Usage |
|---|---|
| `R.drawable.ic_pin` | Pin icon on notebook rows (favorites / pinned state) |
| `R.drawable.ic_shortcut_journal` | Launcher shortcut — Journal Entry |
| `R.drawable.ic_shortcut_quick_note` | Launcher shortcut — Quick Note |
| `R.drawable.ic_launcher_foreground` | Adaptive icon foreground (asterisk mark) |
| `R.drawable.ic_launcher_background` | Adaptive icon background (`#efe4cf`) |

### Notebook Icon Glyphs

Notebooks use Unicode org-mode asterisk glyphs in `PlexMono` Bold at 17sp, colored to match
the notebook's accent. The default glyph set (shown in icon picker):

`✦` `✶` `✸` `✺` `❋` `✷`

Each glyph is rendered on a 42×42dp tile with 12dp corner radius. The tile background is the
soft variant of the chosen color (`greenSoft`, `accentSoft`, `blueSoft`, `redSoft`).

### BrandMark (App Asterisk)

The Grove asterisk — five rounded spokes radiating from center at 36°/108°/180°/252°/324°
(spoke reach = half the mark canvas, width = 7/32 of it; same geometry as the launcher
foregrounds, where spokes are 4.2×15dp on the 108dp adaptive-icon canvas) — is drawn via
the `BrandMark` composable.
Use it wherever the brand identity is needed (onboarding, nav drawer header, app icon).
Never substitute a Unicode character or bitmap.

---

## Reusable Components

### `BrandMark` — `ui/components/BrandMark.kt`

Five-spoke asterisk in a squircle tile, drawn on Canvas.

```kotlin
BrandMark(tileSize = 74.dp)                    // onboarding, large
BrandMark(tileSize = 40.dp)                    // nav drawer header
BrandMark(
    tileSize = 36.dp,
    tileColor = c.surface2,
    barColor = c.ink2,
)                                              // muted / contextual use
```

**When to use**: wherever the Grove brand needs to appear in-app. Do not use for notebook
icons (those use Unicode glyphs on colored tiles).

---

### `Pill` — `ui/components/Common.kt`

Rounded badge for status labels and short counts.

```kotlin
Pill(text = "Modified", fg = c.amber, bg = c.amberSoft)
Pill(text = "1 conflict", fg = c.amber, bg = c.amberSoft, onClick = { openConflict() })
Pill(text = "Recommended", fg = c.green, bg = c.greenSoft)
Pill(text = "✓", fg = c.green, bg = Color.Transparent)
```

Internally: 20dp corner radius, 9dp horizontal / 3dp vertical padding, 11.5sp SemiBold PlexSans.

**When to use**: sync status badges on notebook rows, conflict indicators, inline contextual labels
(e.g., "Recommended", "auto-created"). Not for TODO/DONE keyword chips — those use a 5dp radius
inline chip pattern.

---

### `FavoriteStar` — `ui/components/Common.kt`

Amber ★ marking a favorited heading.

```kotlin
FavoriteStar()                                        // drawer-style inline use
FavoriteStar(modifier = Modifier.padding(top = 2.dp)) // nudged onto a heading's first line
```

Internally: a single `★` glyph, 12sp PlexSans in `amber`. No background, not tappable —
favoriting happens through the outline node menu, the star is display-only.

**When to use**: anywhere a favorited headline is rendered — right-aligned at the end of
outline rows and read-mode heading lines (top-padded so it sits on the first line of a
wrapped title). The nav drawer's Favorites section uses the same `★` glyph in its item
rows. Don't confuse with `☆` (outline "save search" action) or `starColor()` (heading
asterisk color cycle).

---

### `SegmentedControl` — `ui/components/Common.kt`

Two-option toggle for mode switching (Read / Edit).

```kotlin
SegmentedControl(
    options = listOf("Read", "Edit"),
    selected = if (mode == NoteMode.READ) 0 else 1,
    onSelect = { idx -> onModeChange(if (idx == 0) NoteMode.READ else NoteMode.EDIT) },
    modifier = Modifier.width(140.dp),
)
```

Internally: `surface2` container background, 10dp container radius, 3dp internal padding,
8dp active pill radius. Active pill uses `accent` bg + `accentInk` text; inactive uses
transparent bg + `ink2` text.

**When to use**: binary mode toggle. Only place currently: Read ↔ Edit in the note app bar.

---

### `CollapsibleKvSection` — `ui/components/CollapsibleKvSection.kt`

Collapsed-by-default, faded monospace key/value box (design/Grove.dc.html lines
499-552, 1682+). Two call sites: the top of Outline for a notebook's file-level `#+`
keyword lines (once per notebook, gated by the "Show header tags" Settings toggle), and
Read mode for each heading's own `:PROPERTIES:` drawer (gated by "Show property
drawers"). Read mode no longer shows file-level header tags — only its own drawer.

```kotlin
CollapsibleKvSection(
    label = "#+ header tags",           // or ":PROPERTIES:"
    entries = listOf("#+TITLE:" to "Kyoto — Day 2"),
    expanded = expanded,
    onToggle = { expanded = !expanded },
)
```

Internally: `surface2` background, 10dp corner radius, whole section at 66% opacity.
Header row (only tap target) is 8dp/12dp padding, 8dp gap, with a 10sp `ink3` caret
that rotates 90° on expand (animated), a 12sp `ink3` label, and a right-aligned 11sp
`ink3` count. Body (when expanded): 30dp/12dp/10dp padding, 3dp row gap, 12sp rows —
key in `synKw`, value in `ink2`. Expansion state is per-section, in-memory (Outline's
header-tags box persists its expanded state per-notebook via `rememberSaveable`).

**When to use**: display-only metadata that shouldn't compete visually with note
content — never mutates the underlying `.org` file.

---

### `ThemeDropdownPicker` — `ui/components/Common.kt`

Theme picker (Settings → Appearance → Theme) as a collapsed trigger plus an inline expanding
list — no popup menu. The trigger row (`surface2` fill, 12dp radius, 1dp `line` border that
turns `accent` while open, 11×9dp padding) shows the active theme's three 9dp dots, its name
(14sp Medium `ink`), and an 11sp `ink3` chevron that rotates 180° while open. The list expands
in place below (8dp gap): a `surface2` container, 13dp radius, 6dp padding, 4dp row spacing,
capped at 280dp with internal scroll. Each row previews its theme — own `bg` fill, 11dp radius,
3 dots, label in the theme's `ink` (13.5sp SemiBold) — and the active row gets a 2dp border plus
a trailing ✓, both in its first dot color. Selecting a row applies the theme and collapses the list.

```kotlin
ThemeDropdownPicker(
    selected = settings.theme,
    onSelect = onSetTheme,
    modifier = Modifier.fillMaxWidth(),
)
```

Preview colors (bg/ink/dots) are hardcoded per theme rather than derived from `GroveColors`,
matching `design/Grove.dc.html`'s `themeList()` — notably the Dark theme's row uses its
`surface` color, not `bg`, for legibility against the picker's own surface background.

**When to use**: the single Settings theme picker. Not a general-purpose dropdown component.

---

### `GroveTopBar` — `ui/components/Common.kt`

Edge-to-edge app bar that consumes the status bar inset.

```kotlin
GroveTopBar(
    leading = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
    title = { Text("travel.org", style = MaterialTheme.typography.titleMedium) },
    actions = {
        IconButton(onClick = onSearch) { Icon(Icons.Default.Search, null) }
        IconButton(onClick = onMenu) { Icon(Icons.Default.MoreVert, null) }
    },
)
```

Fixed 56dp height + `statusBarsPadding()`, 8dp horizontal padding, leading / weight-1 title /
trailing slots. Renders on `bg` (no elevation / surface lift).

**When to use**: every screen that has an app bar. Do not use Material3 `TopAppBar` directly —
it does not integrate the status bar inset the same way.

---

### `annotateOrgInline` — `ui/components/OrgInlineText.kt`

Converts an org inline-markup string to a Compose `AnnotatedString` with syntax-colored spans.

```kotlin
// In a read-mode paragraph or outline row:
Text(
    text = annotateOrgInline(node.text, c),
    style = MaterialTheme.typography.bodyLarge,
)

// With tappable links:
Text(
    text = annotateOrgInline(node.text, c, onLink = { url -> launcher.launch(url) }),
    style = MaterialTheme.typography.bodyMedium,
)
```

Handles: `*bold*` (SemiBold), `/italic/`, `_underline_`, `=code=` (PlexMono + `surface2` bg),
`[[url][desc]]` links (`synLink`, optional `onLink` callback), `<timestamps>` / `[timestamps]`
(`synTs`).

**When to use**: anywhere org body text or heading text is rendered outside the raw editor
(outline rows, read mode, capture breadcrumb, search result snippets). For the raw editor,
use `OrgVisualTransformation` instead.

---

### `SwipeRevealRow` / `SwipeAction` — `ui/components/SwipeRevealRow.kt`

Row that swipes horizontally to reveal a 4-cell action panel on either side
(prototype Gestures screen physics).

```kotlin
SwipeRevealRow(
    leftActions = listOf(SwipeAction("⟳", "State", c.amber, c.amberSoft) { /* … */ }),
    rightActions = listOf(SwipeAction("➜", "Refile", c.accent, c.accentSoft) { /* … */ }),
    enabled = focusedLine == null,
    forceClose = openRowLine != h.lineIndex,
    onOpenChanged = { open -> openRowLine = if (open) h.lineIndex else null },
    onTap = { /* open note */ },
    onLongPress = { /* enter focus mode */ },
) { OutlineNode(…) }
```

Physics constants (do not change without the prototype): panel 184dp = 4 × 46dp cells,
open threshold 66dp, rubber-band factor 0.18 past the panel, settle 340ms
`CubicBezierEasing(0.22, 1, 0.36, 1)`. Tap on an open card closes it; action cells are
`fg` glyph (16sp) over a 9sp Medium label on the action's `Soft` bg. The parent keeps at
most one row open (`forceClose` + `onOpenChanged`) and snaps all rows shut on any
document mutation.

**When to use**: outline heading rows. Reuse for any future list with swipe quick actions.

---

### `GroveToast` / `GroveUndoSnackbar` — `ui/components/Feedback.kt`

Transient feedback overlays driven by `DocumentViewModel.toast` / `.snack`
(ViewModel owns the ~1.9s / ~4.2s timers).

```kotlin
Box(Modifier.fillMaxSize()) {
    GroveUndoSnackbar(snack, onUndo = viewModel::undo,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 86.dp))
    GroveToast(toast,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp))
}
```

Toast: bottom-center pill — `ink` bg, `bg`-color 13sp Medium text, 16×10dp padding, 20dp
radius. Snackbar: full-width with 14dp side margins, `ink` bg, 12dp radius, 13.5sp message +
Bold 13sp `accent` "UNDO". Both are lifted above the FAB so UNDO stays tappable.

**When to use**: toast for state changes and blocked ops; snackbar only for undoable
structural ops (move, promote, demote, delete, refile).

---

### `RefileSheet` — `ui/screens/RefileSheet.kt`

Two-step refile destination picker (`ModalBottomSheet`, 20dp top radius, `surface` bg):
notebook list → per-level heading drill-down. Tapping a heading selects **and** drills;
"Refile here" targets the crumb's last heading, or the file's top level. Rows are 12dp
radius with a 1dp `line` border — `▤` glyph for notebooks, `✳` for headings (PlexMono
15sp `accent`), label PlexMono 14.5sp Medium, "N headings" sub-label 11.5sp `ink3`.
Footer: Cancel (surface-2 / line border / ink2) + weight-1 "Refile here"
(accent/accentInk enabled, surface-2/ink3 disabled). State machine lives in
`DocumentViewModel` (`RefileUiState`, `startRefile` … `refileConfirm`).

**Archive quick action**: when the source heading resolves an `ARCHIVE` target (its own
`:PROPERTIES:`, the nearest ancestor's, or the file's `#+ARCHIVE:` keyword — nearest-
ancestor-wins inheritance, see `org/ArchiveLocation.kt`), a pinned row is shown above
everything else in the sheet: 12dp radius, filled `accentSoft` (vs. the plain-list rows'
`line`-bordered/transparent look, so it reads as the primary action), `◆` glyph in
`accent`, "Archive" title (PlexSans SemiBold 14sp `ink`) + resolved crumb sub-label
("archive.org › Inbox", PlexSans 11.5sp `ink2`), trailing `→` in `accent`. One tap
refiles immediately (no drill-down/confirm step) via `DocumentViewModel.refileToArchive()`,
auto-creating the destination file and/or any missing heading in its path.

---

### `StructureCommandBar` — `ui/screens/OutlineScreen.kt` (private)

"Move & indent" bar that replaces `GroveTopBar` while a row is focused (long-press):
56dp on `accentSoft` with a 1dp `line` bottom rule; ✕ 40dp circle (accent glyph),
13.5sp SemiBold accent title, 38dp/10dp-radius `surface` buttons ↑ ↓ ⇤ ⇥, ⌫ in `red`,
and a ✓ confirm on `accent`/`accentInk`. Every handler re-resolves the focused headline
from the current document at click time. Back gesture exits focus mode.

---

## Screen Inventory

| Screen | Route | Key components used |
|---|---|---|
| Onboarding | `onboarding` | `BrandMark`, `Pill` ("Recommended"), primary button |
| Notebooks | `notebooks` | `GroveTopBar`, `Pill` (sync badges), icon glyph tiles, FAB |
| Nav Drawer | (overlay) | `BrandMark`, plain `Text` rows, `★` favorites glyph |
| Outline | `outline/{notebookId}` | `GroveTopBar`, `annotateOrgInline`, keyword chips, `starColor()`, `FavoriteStar` |
| Read Note | `note/{noteId}?mode=read` | `GroveTopBar`, `SegmentedControl`, `annotateOrgInline`, tag chips, `FavoriteStar` |
| Edit Note | `note/{noteId}?mode=edit` | `GroveTopBar`, `SegmentedControl`, `OrgVisualTransformation`, formatting toolbar, `MetadataSheet` |
| Capture Picker | (bottom sheet) | `ModalBottomSheet`, icon glyph tiles, `PlexMono` |
| Capture Editor | `capture/{templateId}` | `GroveTopBar`, `monoBody()`, formatting toolbar |
| Search | `search` | `GroveTopBar`, `annotateOrgInline`, `Pill` ("Advanced") |
| Conflict | `conflict/{notebookId}` | `GroveTopBar`, warning banner, diff cards, action buttons |
| Settings | `settings` | `GroveTopBar`, `ThemeDropdownPicker` (theme), `SegmentedControl` (font), keyword chips, `Pill` ("default") |

---

## Theme Entry Point

Wrap every screen in `GroveTheme`. It is already applied at the root in `MainActivity`.

```kotlin
GroveTheme(
    theme = ThemePreference.LIGHT,   // LIGHT | DARK | TOKYONIGHT | SYNTHWAVE | DRACULA | CATPPUCCIN | NORD
    fontSize = FontSizePreference.MEDIUM, // SMALL | MEDIUM | LARGE
) {
    GroveApp()
}
```

There is no "follow system" option — the app always uses the explicitly selected `ThemePreference`,
picked via `ThemeDropdownPicker` on the Settings screen and persisted through `SettingsRepository`.

Inside any composable: `MaterialTheme.grove` → full `GroveColors` token set.
