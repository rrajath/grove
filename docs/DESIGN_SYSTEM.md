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

The Grove asterisk — three rounded bars at 0°/60°/120° — is drawn via `BrandMark` composable.
Use it wherever the brand identity is needed (onboarding, nav drawer header, app icon).
Never substitute a Unicode character or bitmap.

---

## Reusable Components

### `BrandMark` — `ui/components/BrandMark.kt`

Three-bar asterisk in a squircle tile, drawn on Canvas.

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

### `ThemeSwatchPicker` — `ui/components/Common.kt`

Wrapping 7-chip theme picker (Settings → Appearance → Theme). Each chip previews its theme:
own `bg` fill, 11dp corner radius, 3 small (9dp) accent/green/blue-ish dots, and its label in
the theme's `ink` color. The active chip gets a 2dp border in its first dot color; inactive
chips get a neutral `rgba(128,128,128,0.22)` border.

```kotlin
ThemeSwatchPicker(
    selected = settings.theme,
    onSelect = onSetTheme,
    modifier = Modifier.fillMaxWidth(),
)
```

Preview colors (bg/ink/dots) are hardcoded per theme rather than derived from `GroveColors`,
matching `design/GroveThemes.dc.html`'s `themeList()` — notably the Dark theme's chip uses its
`surface` color, not `bg`, for legibility against the picker's own surface background.

**When to use**: the single Settings theme picker. Not a general-purpose swatch component.

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

## Screen Inventory

| Screen | Route | Key components used |
|---|---|---|
| Onboarding | `onboarding` | `BrandMark`, `Pill` ("Recommended"), primary button |
| Notebooks | `notebooks` | `GroveTopBar`, `Pill` (sync badges), icon glyph tiles, FAB |
| Nav Drawer | (overlay) | `BrandMark`, plain `Text` rows |
| Outline | `outline/{notebookId}` | `GroveTopBar`, `annotateOrgInline`, keyword chips, `starColor()` |
| Read Note | `note/{noteId}?mode=read` | `GroveTopBar`, `SegmentedControl`, `annotateOrgInline`, tag chips |
| Edit Note | `note/{noteId}?mode=edit` | `GroveTopBar`, `SegmentedControl`, `OrgVisualTransformation`, formatting toolbar, `MetadataSheet` |
| Capture Picker | (bottom sheet) | `ModalBottomSheet`, icon glyph tiles, `PlexMono` |
| Capture Editor | `capture/{templateId}` | `GroveTopBar`, `monoBody()`, formatting toolbar |
| Search | `search` | `GroveTopBar`, `annotateOrgInline`, `Pill` ("Advanced") |
| Conflict | `conflict/{notebookId}` | `GroveTopBar`, warning banner, diff cards, action buttons |
| Settings | `settings` | `GroveTopBar`, `ThemeSwatchPicker` (theme), `SegmentedControl` (font), keyword chips, `Pill` ("default") |

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
picked via `ThemeSwatchPicker` on the Settings screen and persisted through `SettingsRepository`.

Inside any composable: `MaterialTheme.grove` → full `GroveColors` token set.
