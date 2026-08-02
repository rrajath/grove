# Grove Design System

This document is the single source of truth for the Grove design system as implemented in code.
Cross-reference `design/README.md` for the original pixel-accurate spec and `Grove.dc.html` for
the interactive prototype.

---

## Color Tokens

All tokens live in `GroveColors` (`ui/theme/Color.kt`). Access them via `MaterialTheme.grove`
inside any `@Composable`. Never hardcode hex values; always reference a token.

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
| `accent` | `#8a5a2b` | `#cb9d62` | Primary interactive color: buttons, active chips, FAB |
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

Beyond Grove Light/Grove Dark, `GroveColors` ships nine more curated palettes, each a full-opacity
port of the named community theme. Full token values live in `ui/theme/Color.kt`
(`GroveTokyoNightColors`, `GroveTokyoDayColors`, `GroveSynthwaveColors`, `GroveDraculaColors`,
`GroveCatppuccinColors`, `GroveCatppuccinLatteColors`, `GroveNordColors`, `GroveRosePineDawnColors`,
`GroveRosePineMoonColors`); this table covers the core tokens only. The Settings theme picker
labels the built-in Light/Dark pair "Grove Light"/"Grove Dark" to read as a matched set alongside
the other theme families; their `ThemePreference` enum entries and storage keys (`LIGHT`/`DARK`,
`"light"`/`"dark"`) are unchanged.

| Theme | `isDark` | `bg` | `surface` | `ink` | `accent` | `green` | `amber` | `red` | `blue` | `violet` |
|---|---|---|---|---|---|---|---|---|---|---|
| Tokyo Night | dark | `#1a1b26` | `#1f2335` | `#c0caf5` | `#7aa2f7` | `#9ece6a` | `#e0af68` | `#f7768e` | `#7dcfff` | `#bb9af7` |
| Tokyo Day | light | `#e1e2e7` | `#edeef2` | `#3760bf` | `#2e7de9` | `#587539` | `#b15c00` | `#f52a65` | `#007197` | `#9854f1` |
| Synthwave | dark | `#262335` | `#2a2140` | `#f8f8f2` | `#ff7edb` | `#72f1b8` | `#fede5d` | `#fe4450` | `#03edf9` | `#b967ff`* |
| Dracula | dark | `#282a36` | `#2d2f3d` | `#f8f8f2` | `#bd93f9` | `#50fa7b` | `#ffb86c` | `#ff5555` | `#8be9fd` | `#bd93f9` |
| Catppuccin Mocha | dark | `#1e1e2e` | `#292a3d` | `#cdd6f4` | `#cba6f7` | `#a6e3a1` | `#fab387` | `#f38ba8` | `#89b4fa` | `#f5c2e7` |
| Catppuccin Latte | light | `#eff1f5` | `#e6e9ef` | `#4c4f69` | `#8839ef` | `#40a02b` | `#df8e1d` | `#d20f39` | `#1e66f5` | `#7287fd` |
| Nord | dark | `#2e3440` | `#333b4a` | `#eceff4` | `#88c0d0` | `#a3be8c` | `#ebcb8b` | `#bf616a` | `#81a1c1` | `#b48ead` |
| Rosé Pine Dawn | light | `#faf4ed` | `#fffaf3` | `#575279` | `#907aa9` | `#286983` | `#ea9d34` | `#b4637a` | `#56949f` | `#907aa9` |
| Rosé Pine Moon | dark | `#232136` | `#2a273f` | `#e0def4` | `#c4a7e7` | `#3e8fb0` | `#f6c177` | `#eb6f92` | `#9ccfd8` | `#c4a7e7` |

\* Synthwave has no purple/violet in its source palette; this value is derived (blended between
its accent pink and blue) to keep the heading-star cycle's 5th color visually distinct.

Themes tagged `light` in the table above are non-`isDark` (light-family) palettes, following the
same token-layering convention as Grove Light (e.g. `surface` distinct from `bg`, dark `ink` on a
light `bg`) rather than the dark-family convention Tokyo Night/Synthwave/Dracula/Catppuccin
Mocha/Nord/Rosé Pine Moon use.

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

Accessed via `MaterialTheme.typography.*`; `GroveTheme` populates all roles.

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
| Conflict diff container | 13dp |
| Custom `TimePickerDialog` surface | 28dp |
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
- **FAB**: `0 6px 18px rgba(138,90,43,0.40)` (light), amber-tinted drop shadow

Use `Modifier.shadow()` only on the FAB and bottom sheet; everywhere else rely on color layering.

---

## Motion

### Screen transitions: `ui/nav/NavTransitions.kt`

Every Grove route is a full-screen surface, so route changes use the **fade-through**
from the [Android predictive-back guidelines][pb], never a slide. Sliding one
full-screen surface off while another slides on reads as paging through images
rather than as moving between levels of the app.

[pb]: https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back

| | Scale | Opacity |
|---|---|---|
| Leaving on back (`popExit`) | 100% → 90% | 100% → 0% over the first 35% |
| Arriving on back (`popEnter`) | 110% → 100% | 0% → 100%, starting at 35% |
| Leaving on forward (`exit`) | 100% → 110% | 100% → 0% over the first 35% |
| Arriving on forward (`enter`) | 90% → 100% | 0% → 100%, starting at 35% |

Because both surfaces shrink on a back and both grow on a forward, the scene reads
as one continuous zoom rather than a cross-dissolve. Neither screen is visible at the
35% mark; the `bg` fill behind the `NavHost` shows through.

Full committed duration 450ms. Fades use the guidelines' `CubicBezierEasing(0.1, 0.1, 0, 1)`;
scale uses `STANDARD_DECELERATE` = `CubicBezierEasing(0, 0, 0, 1)`, since the guidelines
call for a decelerating curve rather than raw linear gesture progress.

`NavHost` seeks this transition with the system back gesture's progress (the manifest
opts in via `android:enableOnBackInvokedCallback`), so the previous screen fades in
exactly as far as the user has dragged and rewinds if they release before the commit
point. Any new `enterTransition`/`exitTransition` must go through these four values;
`EnterTransition.None` disables predictive back entirely.

---

## Icon Conventions

### Material Icons

Grove uses Material Icons from the standard `androidx.compose.material.icons` library.
The default icon set (Filled style) is preferred. Use Outlined sparingly for secondary /
less-prominent actions.

| Screen / Context | Icon | Token |
|---|---|---|
| Hamburger / open drawer | `Icons.Default.Menu` | - |
| Back navigation | `Icons.Default.ArrowBack` | - |
| Search | `Icons.Default.Search` | - |
| Overflow menu | `Icons.Default.MoreVert` | - |
| Dismiss / close | `Icons.Default.Close` | - |
| Sync status: ok | `Icons.Default.Check` | `green` |
| Sync status: error | `Icons.Default.Warning` | `amber` |
| Save indicator (edit mode) | `Icons.Outlined.Save` | `green` unsaved changes (tap saves immediately), `ink3` saved (tap shows a "last saved at" toast); a plain color swap on save, no blink/animation |
| Auto-save indicator (capture) | `Icons.Outlined.Save` | `green` saved, `ink3` while dirty (passive; capture has no manual tap-to-save) |
| Sync: spinning | `Icons.Default.Sync` (animated) | `ink2` |
| Scheduled date | `Icons.Outlined.CalendarMonth` | `blue` |
| Deadline | `Icons.Filled.Flag` | `red` |
| Drag handle (templates) | `Icons.Default.DragHandle` | `ink3` |
| Settings | `Icons.Default.Settings` | `ink2` |
| Agenda | `Icons.Default.ViewList` | `ink2` |
| "None" (Default priority) | `Icons.Filled.Block` | tint follows active/inactive segment color |

Read mode's `PlanningChip` renders the *human* form of the timestamp,
`OrgTimestamp.formatHuman()`: `Jul 30`, `Jul 30 12:00`, `Jul 30 12:00-13:30`,
plus the year (`Jul 30, 2027`) only when it isn't the current one, and any
repeater/warning cookie kept verbatim. Read mode is prose; the literal
`<2026-07-30 Thu>` belongs in Edit mode and on disk (`OrgTimestamp.format()`),
which is unchanged. Other surfaces that show a raw stamp (Outline row chips)
have not been converted.

The Scheduled/Deadline icons replace the literal "SCHEDULED"/"DEADLINE" words next
to a formatted timestamp: Read mode's `PlanningChip`, Outline's own-heading chips,
Search's `DatePillText`, and Agenda's swipe-action reveals (`◷`/`⚑` glyphs retired
in favor of the same icons, keeping the "Sched"/"Deadl" labels). The full-screen
`PlanningDatesScreen` keeps its own `◷`/`⚑` glyphs in the section rows, a separate,
color-coded system (blue/red calendar cells) rather than a label to shorten.

Agenda's own row meta strip is the other exception: it uses literal `⚑` (deadline)
and `↻` (repeater) characters inline in `PlexMono` text, since they sit mid-sentence
in a run of mono chips rather than standing alone as an affordance.

### Custom Drawables

| Resource | Usage |
|---|---|
| `R.drawable.ic_pin` | Pin icon on notebook rows (favorites / pinned state) |
| `R.drawable.ic_shortcut_journal` | Launcher shortcut: Journal Entry |
| `R.drawable.ic_shortcut_quick_note` | Launcher shortcut: Quick Note |
| `R.drawable.ic_launcher_foreground` | Adaptive icon foreground (asterisk mark) |
| `R.drawable.ic_launcher_background` | Adaptive icon background (`#efe4cf`) |
| `R.drawable.ic_notification` | Notification small icon: the same mark on a 24dp canvas |

**Never use `ic_launcher_foreground` as a notification small icon.** It is a 108dp
adaptive-icon foreground whose mark spans only ~30/108 so it survives the launcher's
safe-zone crop; in a 24dp status-bar slot that renders as a speck next to system icons.
`ic_notification` is the same five-spoke path redrawn to fill a 24dp canvas (spokes to
r=10.5, inside the 22x22 content area Android expects).

Android draws small icons as an alpha mask and tints them, so the per-theme color comes
from `NotificationCompat.Builder.setColor`; see `icon/NotificationAppearance.kt` and
`AppIconManager.markColor`, which resolve the same (sync-enabled, theme) pair that
`targetAlias` uses for the launcher icon. In the notification shade, the circular badge
is drawn from the *launcher* icon, so it follows the theme through the alias switch
rather than through `setColor`.

### Notebook Icon Glyphs

Notebooks use Unicode org-mode asterisk glyphs in `PlexMono` Bold at 17sp, colored to match
the notebook's accent. The default glyph set (shown in icon picker):

`✦` `✶` `✸` `✺` `❋` `✷`

Each glyph is rendered on a 42×42dp tile with 12dp corner radius. The tile background is the
soft variant of the chosen color (`greenSoft`, `accentSoft`, `blueSoft`, `redSoft`).

### BrandMark (App Asterisk)

The Grove asterisk (five rounded spokes radiating from center at 36°/108°/180°/252°/324°,
spoke reach = half the mark canvas, width = 7/32 of it; same geometry as the launcher
foregrounds, where spokes are 4.2×15dp on the 108dp adaptive-icon canvas) is drawn via
the `BrandMark` composable.
Use it wherever the brand identity is needed (onboarding, nav drawer header, app icon).
Never substitute a Unicode character or bitmap.

---

## Reusable Components

### `BrandMark`: `ui/components/BrandMark.kt`

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

### `Pill`: `ui/components/Common.kt`

Rounded badge for status labels and short counts. Filled by default; pass `outline = true` for a
transparent-fill, border-only variant.

```kotlin
Pill(text = "Modified", fg = c.amber, bg = c.amberSoft)
Pill(text = "1 conflict", fg = c.amber, bg = c.amberSoft, onClick = { openConflict() })
Pill(text = "Recommended", fg = c.green, bg = c.greenSoft)
Pill(text = "✓", fg = c.green, bg = Color.Transparent)
Pill(text = "trip", fg = c.accent, bg = c.accentSoft, outline = true) // tag pill, Read mode
```

Internally: 20dp corner radius, 9dp horizontal / 3dp vertical padding, 11.5sp SemiBold PlexSans.
`outline = true` swaps the solid `bg` fill for a transparent background with a 1dp `fg`-colored
border instead (same shape/typography otherwise).

**When to use**: sync status badges on notebook rows, conflict indicators, inline contextual labels
(e.g., "Recommended", "auto-created"); these stay filled. Not for TODO/DONE keyword chips; those
use a 5dp radius inline chip pattern (outline screen only; read mode's TODO/keyword chips do use
`Pill`, filled). In Read mode and the Metadata sheet's tag suggestions, tag pills use `outline =
true` so they read as a distinct, lighter-weight category from the filled TODO-state pills sitting
next to them.

---

### `FavoriteStar`: `ui/components/Common.kt`

Amber ★ marking a favorited heading.

```kotlin
FavoriteStar()                                        // drawer-style inline use
FavoriteStar(modifier = Modifier.padding(top = 2.dp)) // nudged onto a heading's first line
FavoriteStar(modifier = Modifier.padding(top = 8.dp).offset(x = 3.dp)) // read-mode title row
```

Internally: a single `★` glyph, 12sp PlexSans in `amber`. No background, not tappable:
favoriting happens through the outline node menu, the star is display-only. The glyph's own
right-side font bearing leaves a small gap between its visual edge and its layout box's edge, so
call sites that need it flush against something to its right (e.g. Read mode's title row, meant
to align with the `:PROPERTIES:` drawer's right edge below it) add a small `offset(x = 3.dp)`
nudge alongside the usual top padding.

**When to use**: anywhere a favorited headline is rendered: right-aligned at the end of
outline rows and read-mode heading lines (top-padded so it sits on the first line of a
wrapped title). The nav drawer's Favorites section uses the same `★` glyph in its item
rows. Don't confuse with `☆` (outline "save search" action) or `starColor()` (heading
asterisk color cycle).

---

### `SegmentedControl`: `ui/components/Common.kt`

Toggle for 2-4 mutually exclusive options (mode switching, Settings rows).

```kotlin
SegmentedControl(
    options = listOf("Read", "Edit"),
    selected = if (mode == NoteMode.READ) 0 else 1,
    onSelect = { idx -> onModeChange(if (idx == 0) NoteMode.READ else NoteMode.EDIT) },
    modifier = Modifier.width(140.dp),
)

// An option can render as an icon instead of a text label: e.g. Settings' Default
// priority row uses a circle-slash glyph for "None" instead of a squeezed text label.
SegmentedControl(
    options = listOf("None", "A", "B", "C"),
    selected = priorityIndex,
    onSelect = onSelectPriority,
    optionIcons = listOf(Icons.Filled.Block, null, null, null),
    modifier = Modifier.width(200.dp),
)
```

Internally: `surface2` container background, 10dp container radius, 3dp internal padding,
8dp active pill radius. Active pill uses `accent` bg + `accentInk` text; inactive uses
transparent bg + `ink2` text. Text labels render at 11.5sp (the `labelSmall` scale) rather
than 13sp, so 3-4 option rows (e.g. Font size's Small/Medium/Large) fit without ellipsis at
the widths used in Settings. `optionIcons` is an optional `List<ImageVector?>`; a non-null
entry at an index renders a 15dp `Icon` (same active/inactive tint as the text) in place of
that option's label; leave the list null (or that index null) for a plain text option.

**When to use**: any 2-4-way exclusive toggle with short labels. Places currently: Read ↔ Edit
in the note app bar, and several Settings rows (Font size, Default priority, Default note mode,
Notebook display mode, Checklist states). For longer option labels that would get squeezed,
use `DropdownPicker` instead (e.g. Agenda swipe-left/swipe-right actions).

---

### `CollapsibleKvSection`: `ui/components/CollapsibleKvSection.kt`

Collapsed-by-default, faded monospace key/value box (design/Grove.dc.html lines
499-552, 1682+). Two call sites: the top of Outline for a notebook's file-level `#+`
keyword lines (once per notebook, gated by the "Show preface" Settings toggle), and
Read mode for each heading's own `:PROPERTIES:` drawer (gated by "Show property
drawers"). Read mode no longer shows the file-level preface: only its own drawer.

```kotlin
CollapsibleKvSection(
    label = "PREFACE",           // or ":PROPERTIES:"
    entries = listOf("#+TITLE:" to "Kyoto: Day 2"),
    expanded = expanded,
    onToggle = { expanded = !expanded },
)
```

Internally: `surface2` background, 10dp corner radius, whole section at 66% opacity.
Header row (only tap target) is 8dp/12dp padding, 8dp gap, with a 10sp `ink3` caret
that rotates 90° on expand (animated), a 12sp `ink3` label, and a right-aligned 11sp
`ink3` count. Body (when expanded): 30dp/12dp/10dp padding, 3dp row gap, 12sp rows:
key in `synKw`, value in `ink2`. Expansion state is per-section, in-memory (Outline's
preface box persists its expanded state per-notebook via `rememberSaveable`).

**When to use**: display-only metadata that shouldn't compete visually with note
content; never mutates the underlying `.org` file.

---

### `ThemeDropdownPicker`: `ui/components/Common.kt`

Theme picker (Settings → Look and Feel → Theme) as a collapsed trigger plus an inline expanding
list; no popup menu. The trigger row (`surface2` fill, 12dp radius, 1dp `line` border that
turns `accent` while open, 11×9dp padding) shows the active theme's three 9dp dots, its name
(14sp Medium `ink`), and an 11sp `ink3` chevron that rotates 180° while open. The list expands
in place below (8dp gap): a `surface2` container, 13dp radius, 6dp padding, 4dp row spacing,
capped at 280dp with internal scroll. Each row previews its theme: own `bg` fill, 11dp radius,
3 dots, label in the theme's `ink` (13.5sp SemiBold), and the active row gets a 2dp border plus
a trailing ✓, both in its first dot color. Selecting a row applies the theme and collapses the list.

```kotlin
ThemeDropdownPicker(
    selected = settings.theme,
    onSelect = onSetTheme,
    modifier = Modifier.fillMaxWidth(),
)
```

Preview colors (bg/ink/dots) are hardcoded per theme rather than derived from `GroveColors`,
matching `design/Grove.dc.html`'s `themeList()`; notably the Dark theme's row uses its
`surface` color, not `bg`, for legibility against the picker's own surface background.

List order: light themes first, then dark themes, each group alphabetical by label (Catppuccin
Latte, Grove Light, Rosé Pine Dawn, Tokyo Day, then Catppuccin Mocha, Dracula, Grove Dark, Nord,
Rosé Pine Moon, Synthwave, Tokyo Night), not declaration order in `ThemePreference`.

**When to use**: the single Settings theme picker. Not a general-purpose dropdown component.

---

### `DropdownPicker`: `ui/components/Common.kt`

General-purpose text-options dropdown, same collapsed-trigger-plus-inline-expanding-list
chrome as `ThemeDropdownPicker` (`surface2` fill, 12dp radius, 1dp `line` border that turns
`accent` while open, 11×9dp padding, 11sp `ink3` chevron that rotates 180° while open), but for
any small list of plain string options rather than the theme swatches. The trigger shows the
selected option's label (14sp Medium `ink`). The list expands below (8dp gap): `surface2`
container, 13dp radius, 6dp padding, 2dp row spacing. Each row is 10×9dp padding, 9dp radius;
the active row gets an `accentSoft` background, `accentInk`/SemiBold label, and a trailing 15dp
check icon (`Icons.Default.Check`, `accentInk`); inactive rows are plain `ink`/Medium text on
transparent background. Selecting a row fires `onSelect` and collapses the list.

```kotlin
DropdownPicker(
    options = AgendaSwipeAction.entries.map { it.label },
    selectedIndex = settings.agendaSwipeLeftAction.ordinal,
    onSelect = { onSetAgendaSwipeLeftAction(AgendaSwipeAction.entries[it]) },
    modifier = Modifier.fillMaxWidth(),
)
```

**When to use**: Settings rows with 2+ mutually-exclusive text options where a segmented
control would be too cramped for the label text (e.g. Agenda swipe-left/swipe-right action
pickers: "Schedule Task" / "Set Deadline" / "Mark as Done"). For short labels that fit a
compact pill row, prefer `SegmentedControl` instead.

---

### `GroveTopBar`: `ui/components/Common.kt`

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

**When to use**: every screen that has an app bar. Do not use Material3 `TopAppBar` directly:
it does not integrate the status bar inset the same way.

**Optional `subtitle` row**: a second, full-width row rendered below the 56dp bar (16dp
horizontal padding, 10dp bottom padding), for content that needs its own line rather than
competing with `actions` inside the fixed-height row:

```kotlin
GroveTopBar(
    leading = { IconGlyph("←", onClick = onBack) },
    actions = { SegmentedControl(options = listOf("Read", "Edit"), ...) },
    subtitle = { ReadModeBreadcrumb(fileName, path, onOpenBreadcrumb) },
)
```

Read mode (`ReadNoteScreen.kt`) is the one call site today: the breadcrumb trail
(file name › heading › ... › current heading, each segment tappable, monospace 11.5sp
`ink3`, `›` separators, horizontally scrolling if it overflows) used to sit in the `title`
slot, where a long path collided with the Read/Edit segmented control. It now renders as
`subtitle`, so row 1 is just the back button (left) and Read/Edit (right, in line with the
back button), and the breadcrumb occupies its own row underneath.

**When to use `subtitle`**: only when `title` content is wide/dynamic enough to contend with
`actions` for space in the 56dp row (e.g. a breadcrumb trail). Short, fixed-width titles
should stay in `title`.

---

### `annotateOrgInline`: `ui/components/OrgInlineText.kt`

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

### `SwipeRevealRow` / `SwipeAction`: `ui/components/SwipeRevealRow.kt`

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
a `fg`-tinted 16sp glyph or 17dp `Icon` over a 9sp Medium label on the action's `Soft` bg.

The glyph/icon sits in a fixed 22dp slot (`ActionMark`) so every cell's label lands on
the same baseline. Without it, a Material `Icon` measures exactly its `size` while a text
glyph measures its font's line height, and mixed panels (e.g. State ⟳ / Schedule icon /
Note icon / Fav ★) show visibly misaligned labels.
`SwipeAction.icon` (an `ImageVector`) takes precedence over `SwipeAction.glyph` (a plain
Unicode character) when both would apply: use `icon` when the action must match a Material
icon used elsewhere in the app (e.g. `MARK_DONE`'s checkmark matches the synced-notebook
`Icons.Default.Check` on the Notebooks top bar); use `glyph` for everything else. The parent
keeps at most one row open (`forceClose` + `onOpenChanged`) and snaps all rows shut on any
document mutation.

**When to use**: outline heading rows. Reuse for any future list with swipe quick actions.

---

### `SwipeCommitRow`: `ui/components/SwipeRevealRow.kt`

Gmail/Reminders-style swipe: swiping left or right past the 66dp open
threshold fires the single configured action immediately on release and the
row always springs back, unlike `SwipeRevealRow`'s reveal-then-tap panel,
there's only ever one action per direction (Settings § Agenda), so a second
tap to confirm would be one tap too many. Rubber-bands past a 96dp cap
(shorter than SwipeRevealRow's 184dp panel; no persistent open state to
travel toward). A colored full-bleed underlay (icon+label anchored near the
edge) previews the action mid-drag.

```kotlin
SwipeCommitRow(
    leftAction = SwipeAction("◷", "Sched", c.blue, c.blueSoft) { … },
    rightAction = SwipeAction(label = "Done", fg = c.green, bg = c.greenSoft, icon = Icons.Default.Check) { … },
    onTap = { onOpenNote(…) },
    shape = RoundedCornerShape(12.dp),
) { ResultRowContent(…) }
```

**When to use**: a list row with exactly one action per swipe direction.
Reuse `SwipeRevealRow` instead when a row needs a multi-action panel.

---

### `ResultRowContent`: `ui/components/ResultRow.kt`

Shared keyword-pill/priority/title/snippet layout for a Search or Agenda
result row. Callers own the outer tap target, padding, and the divider
between rows, plus their own text source (Search: match-highlighted;
Agenda: plain via `annotateOrgInline`) and meta row (Search: date pills +
tags; Agenda: file/heading breadcrumb) via a trailing `metaContent` slot.

```kotlin
ResultRowContent(
    keyword = result.keyword,
    isDone = result.isDone,
    priority = result.priority,
    titleText = titleText,       // AnnotatedString: highlighted or plain, caller's choice
    snippetText = snippetText,   // AnnotatedString?: same
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 9.dp, end = 11.dp, bottom = 11.dp),
) {
    Text(result.breadcrumb, fontFamily = PlexMono, fontSize = 11.5.sp, color = c.ink3)
}
```

**When to use**: any Search- or Agenda-shaped result row. Both screens'
`LazyColumn`s separate rows with a thin `line`-colored `HorizontalDivider`
(not part of this component; each screen's `items`/`itemsIndexed` loop owns
its own dividers, same as the file-grouped Search list).

---

### `GroveToast` / `GroveUndoSnackbar`: `ui/components/Feedback.kt`

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

Toast: bottom-center pill: `ink` bg, `bg`-color 13sp Medium text, 16×10dp padding, 20dp
radius. Snackbar: full-width with 14dp side margins, `ink` bg, 12dp radius, 13.5sp message +
Bold 13sp `accent` "UNDO". Both are lifted above the FAB so UNDO stays tappable.

**When to use**: toast for state changes and blocked ops; snackbar only for undoable
structural ops (move, promote, demote, delete, refile).

---

### `RefileSheet`: `ui/screens/RefileSheet.kt`

Two-step refile destination picker (`ModalBottomSheet`, 20dp top radius, `surface` bg):
notebook list → per-level heading drill-down. Tapping a heading selects **and** drills;
"Refile here" targets the crumb's last heading, or the file's top level. Rows are 12dp
radius with a 1dp `line` border: `▤` glyph for notebooks, `✳` for headings (PlexMono
15sp `accent`), label PlexMono 14.5sp Medium, "N headings" sub-label 11.5sp `ink3`.
Footer: Cancel (surface-2 / line border / ink2) + weight-1 "Refile here"
(accent/accentInk enabled, surface-2/ink3 disabled). State machine lives in
`DocumentViewModel` (`RefileUiState`, `startRefile` … `refileConfirm`).

**Archive quick action**: when the source heading resolves an `ARCHIVE` target (its own
`:PROPERTIES:`, the nearest ancestor's, or the file's `#+ARCHIVE:` keyword; nearest-
ancestor-wins inheritance, see `org/ArchiveLocation.kt`), a pinned row is shown above
everything else in the sheet: 12dp radius, filled `accentSoft` (vs. the plain-list rows'
`line`-bordered/transparent look, so it reads as the primary action), `◆` glyph in
`accent`, "Archive" title (PlexSans SemiBold 14sp `ink`) + resolved crumb sub-label
("archive.org › Inbox", PlexSans 11.5sp `ink2`), trailing `→` in `accent`. One tap
refiles immediately (no drill-down/confirm step) via `DocumentViewModel.refileToArchive()`,
auto-creating the destination file and/or any missing heading in its path.

**Settings reuse**: `RefileSheet` also drives Settings § Notes' "Archive location" picker (the
Auto-archive done items fallback) — `currentFileName`/`currentDoc` are nullable (no source-
subtree exclusion when there's no active note) and `headerTitle`/`confirmLabel` are overridable
("Set archive location" / "Set as archive location" there vs. "Refile 1 note" / "Refile here"
elsewhere). `ArchiveRow`/`LastUsedRow` simply don't render when the caller's `RefileUiState`
leaves `archiveTarget`/`lastUsedTarget` null, so the Settings invocation needs no other change.

---

### `MetadataSheet`: `ui/editor/MetadataSheet.kt`

Edit mode's `ModalBottomSheet` (design spec §5.2; 24dp top radius, `surface` bg), opened from
the top bar's `☰` icon. Sections, each preceded by a `SheetLabel` (PlexSans SemiBold 12sp,
1sp letter-spacing, `accent`): State (chips, `none` + every configured keyword, done-type
keywords tint `green`/`greenSoft`, others `amber`/`amberSoft`), Priority (`none`/`#A`/`#B`/`#C`
chips), Tags (`OutlinedTextField` + autocomplete `Pill` row), **Schedule/Deadline**, then
**+ Add note**.

**Schedule/Deadline row**: a single 8dp-radius `surface2` pill (was two separate SCHEDULED/
DEADLINE rows) showing both values inline — `SCHED <date>` in `blue`, `DUE <date>` in `red`,
12dp gap between when both are set, `set date…` `ink3` placeholder when neither is. Tapping
opens `PlanningDatesScreen` (unchanged) focused on whichever of the two is unset, or SCHEDULED
when both/neither are — the screen itself already edits both dates on one canvas, so one entry
point is enough.

**+ Add note**: PlexSans SemiBold 13sp `accent` text action below the planning row; opens the
same `NoteDialog` (`ui/screens/OutlineScreen.kt`, org's `C-c C-z`) as the Outline's "Note" swipe
action, logging free text into the edited heading's `:LOGBOOK:` drawer via the new
`EditorViewModel.addNote`.

---

### `StructureCommandBar`: `ui/screens/OutlineScreen.kt` (private)

"Move & indent" bar that replaces `GroveTopBar` while a row is focused (long-press):
56dp on `accentSoft` with a 1dp `line` bottom rule; ✕ 40dp circle (accent glyph),
13.5sp SemiBold accent title, 38dp/10dp-radius `surface` buttons ↑ ↓ ⇤ ⇥, ⌫ in `red`,
and a ✓ confirm on `accent`/`accentInk`. Every handler re-resolves the focused headline
from the current document at click time. Back gesture exits focus mode.

---

### `PlanningDatesScreen`: `ui/components/PlanningDatesScreen.kt`

Full-window SCHEDULED + DEADLINE editor ("Dates C: one canvas" in
`design/Grove.dc.html`). Opened by `MetadataSheet`, `OutlineScreen`'s and
`AgendaScreen`'s quick-schedule swipe actions, and `EditNoteScreen`'s reminder
"Reschedule" deep link. Presented as a `Dialog` with
`usePlatformDefaultWidth = false` + `decorFitsSystemWindows = false` over a
`fillMaxSize()` `bg` `Surface`; not a nav destination, so every entry point keeps
its own local state and ViewModel wiring. The content column carries
`safeDrawingPadding()` + `imePadding()` so the footer rides above the keyboard.

Both dates are edited together and committed together: `onConfirm` hands back the
pair and the caller writes them in one `OrgMutations.setPlanningDates` edit. The
`focus` argument (`PlanningKind`) only decides which section starts expanded and
which one calendar taps target.

Top to bottom:

| Region | Spec |
|---|---|
| Header | 8dp top / 10dp side. 40dp circular back button (`ArrowBack`, 21dp, `ink`), title 15.5sp SemiBold (1 line, ellipsized, `weight(1f)`), "Clear" 12.5sp SemiBold `ink2` in a 9dp-radius 10×8dp hit area: clears both dates and flips the footer button to read "Clear Dates" until either date is set again |
| Shorthand box | 12dp radius, `bg` fill, 1dp border (`accent` of the focused section while non-empty, else `line`). `›` prefix `PlexMono` 14sp `ink3`, `BasicTextField` `PlexMono` 14sp with 11dp vertical padding, placeholder `d: aug 5 ++1m`. Trailing "Set" chip: 9dp radius, filled `accent` with `surface` text when the line parses, else `surface2`/`ink3` |
| Echo line | `PlexMono` 11.5sp, `green` when parsed (`Mon, Aug 3 · in 5 days  ·  10:00–11:00  ·  → SCHEDULED`), `red` when not. Hidden while the box is empty. The `→ SCHEDULED`/`→ DEADLINE` target always shows, falling back to whichever section is currently expanded when the line has no explicit `s:`/`d:` prefix |
| Hint chips | `FlowRow`, 5dp gaps. `PlexMono` 11.5sp `ink3`, `surface2`, 8dp radius, 9×5dp padding. `fri` `+2w` `aug 3` `10-11am` `++1w` `d: mon`: each appends to the box |
| Calendar card | 16dp radius `surface` + 1dp `line`, 11dp padding. Month header: 30dp circular `‹`/`›` (`ink2` 16sp) around a centered 13.5sp SemiBold `MMM yyyy`. Sunday-first 7-column grid, 2dp gaps, 37dp cells, 10dp radius, `PlexMono` 13sp. Scheduled cell = solid `blue`, deadline = solid `red` (deadline wins on a shared day), both with `surface` text and SemiBold; days strictly between them = `accentSoft` band; today = 1dp `line2` outline plus a 4dp `accent` dot 5dp from the bottom when unselected. Legend row: 11dp `blue`/`red` swatches (4dp radius) + `PlexMono` 11sp `taps set SCHEDULED`/`DEADLINE` right-aligned |
| Lead-time note | 11.5sp `ink2`, shown when both dates are set and ordered ("7 days of lead time before it is due.") |
| Clash warning | Shown instead when scheduled falls after deadline: 12dp radius `redSoft` + 1dp `red`, `!` glyph `red` 14sp, 12sp `ink` copy |
| Section rows | 13dp radius, 12×11dp padding. Expanded = `blueSoft`/`redSoft` + 1dp `blue`/`red`; collapsed = `surface2` + 1dp `line`. `◷` (scheduled) / `⚑` (deadline) 15sp in the section color, then `PlexMono` 10sp Bold 0.8sp-tracked label over a 13sp SemiBold summary (`ink`, or `ink3` reading "Not set") |
| Section body | Preset chips (`Today · Jul 29`, `Tomorrow · …`, `This weekend · …`, `Next week · …`: relative name plus its absolute `MMM d`), then the Time row, then Repeat |
| Time row | `**Time** · All day`: label SemiBold `ink`, value Normal `ink2`. `GroveSwitch` on the right. When on: two 74dp `PlexMono` fields (`surface2`, 10dp radius, centered, committed only when the text parses as `HH:mm`) around a 13sp `ink3` "to", plus `30m`/`1h`/`2h` mini-chips right-aligned (SCHEDULED only) |
| Repeat card | 14dp radius `surface` + 1dp `line`. Sentence "Every _week_, and if I am late, _keep the original rhythm_." at 14sp / 26sp line height, with the two underlined words cycling their options on tap. Below it the org cookie chip (`PlexMono` 11.5sp on the section's soft fill) top-aligned beside an 11.5sp `ink2` explanation of `+` / `++` / `.+` (top-aligned, not centered, since the explanation can wrap to two lines and centering would float the pill below its first line), then a 34dp `−` / count / `+` stepper with Day/Week/Month/Year chips right-aligned |
| Suggestion | Dashed 12dp-radius `line2` row "Start 3 days earlier, …", shown only when a deadline is set and the scheduled date is not; tapping it fills SCHEDULED in |
| Footer | 1dp `line` divider over a `surface` block: two `PlexMono` 12sp raw org lines (`SCHEDULED:` in `synTs`, `DEADLINE:` in `red`, `ink3` and `-` when unset), then a full-width 13dp-radius button (14.5sp SemiBold, 13dp padding) that is disabled (`surface2`/`ink3`) until at least one date is set, then reads "Apply Scheduled Date", "Apply Deadline", or "Apply Both Dates" depending on which are set (`accent`/`accentInk` once enabled); or "Clear Dates" right after the header's Clear action, until a date is set again |

Chips inside the sections use one shared spec: 10dp radius, 12×8dp padding,
12.5sp Medium; selected = section `accent` fill with `accentInk` text and no
border, unselected = `surface2` with a 1dp `line` border and `ink2` text.
`GroveSwitch` is a 40×23dp pill (12dp radius, `accent` when on else `surface3`)
with an 18dp `surface` knob inset 2.5dp.

The shorthand grammar itself lives in `org/DateShorthand.kt`
(`DateShorthandParser`), which is pure JVM and unit-tested.

---

### Unified diff view: `ui/screens/ConflictScreen.kt` (private)

Replaces the old side-by-side raw-text panels with a single git-diff-style column
(`java-diff-utils`, 5 lines of context per hunk). Container: `surface` bg, 1dp
`line` border, 13dp radius, `heightIn(max = 460.dp)` with its own vertical scroll
(nested inside the screen's outer scroll, same pattern the old `DiffCard` used).
Header caption above it reads "CURRENT VERSION → CONFLICT COPY · {label}" (PlexSans
SemiBold 12sp, 0.8sp tracking, `ink3`) so the diff direction is explicit. Hunk
boundaries render as a centered label ("Line N") between two `HorizontalDivider`s
in `line`. Each diff line is a row with a 14dp PlexMono-bold gutter glyph
(`+`/`-`/blank) followed by the line text (PlexMono 12.5sp): added lines use
`greenSoft` row background with `green` text, removed lines use `redSoft`/`red`,
unchanged context lines have no background tint and render in `ink`. Identical
files show a plain "No textual differences between the two copies." message
instead of an empty box.

---

### `CustomDateRangePicker`: `ui/components/PlanningDatePicker.kt`

Start + end date picker for Search → Filters' "Custom range" chip (Scheduled/Deadline). This does
*not* use `DatePickerDialog`: that chrome is sized for the
compact single-month `DatePicker` and clips/squeezes `DateRangePicker`'s wider default title,
headline, and two-month calendar. Instead: a plain `Dialog` (`usePlatformDefaultWidth = false`)
wrapping a 28dp-radius `surface` `Surface` sized `fillMaxWidth(0.95f)` × `fillMaxHeight(0.85f)`,
giving `DateRangePicker` its own expected room. Below it, a `line` divider and a bottom row:
"Cancel" (`ink2`) + "Set" (`accent` SemiBold, disabled until both a start and end date are
picked), both right-aligned.

```kotlin
CustomDateRangePicker(
    initialStart = filters.scheduledRange?.start,
    initialEnd = filters.scheduledRange?.end,
    onDismiss = { rangeTarget = null },
    onConfirm = { start, end -> onSetScheduledRange(start, end) },
)
```

**When to use**: picking an inclusive start/end date pair. For a single date (optionally with
time) on a heading, use `PlanningDatesScreen` instead.

---

### `SimpleTimePicker`: `ui/components/PlanningDatePicker.kt`

Standalone time-of-day picker (no date step); currently used by Settings › Reminders ›
"Default reminder time". Shares the `TimePickerDialog` chrome in the same file (a plain
`Dialog` wrapping a 28dp-radius `surface` `Surface`, 24dp content padding) rather than
duplicating it: "Cancel" (`ink2`) in the dismiss slot, "Set" (`accent` SemiBold) in the
confirm slot.

```kotlin
SimpleTimePicker(
    initial = settings.defaultReminderTime,
    onDismiss = { showPicker = false },
    onConfirm = { onSetDefaultReminderTime(it) },
)
```

**When to use**: picking a bare time of day with no associated date. For a date (optionally
with time) on a heading, use `PlanningDatesScreen` instead.

---

### `ReminderPermissionBanner`: `ui/components/ReminderPermissionBanner.kt`

Dismissible-by-resolution amber banner shown when one or more reminders couldn't be
scheduled for lack of `POST_NOTIFICATIONS`/exact-alarm access; reconciliation always
persists what *should* be scheduled regardless of permission state; this is the surface
that lets the user grant access so it actually gets armed. Renders nothing when there's
nothing pending (`pendingCount <= 0`).

```kotlin
ReminderPermissionBanner(pendingCount = pendingReminderCount, modifier = Modifier.fillMaxWidth())
```

Internally: `amberSoft` bg, 1dp `amber` border, 11dp corner radius, 10dp content padding,
14dp/6dp outer padding. Body text 12.5sp `ink` ("N reminder(s) need permission to notify
you"), trailing tappable "Grant" label (12.5sp SemiBold `accent`, 7dp-radius tap target).
Tapping "Grant" requests whichever permission is still missing (notification runtime
prompt, then the exact-alarm system settings screen), then re-reconciles.

**When to use**: anywhere a user is likely to notice pending reminders: currently shown on
both the Settings and Notebooks screens. Not a general-purpose warning banner (see the
Conflict screen's own warning banner for that pattern).

---

### Agenda screen components: `ui/agenda/AgendaScreen.kt` (all private)

The Agenda screen follows the "Agenda A · focus" variant in `design/Grove.dc.html`
(markup at `:703`, logic in `agVals()`/`agRow()` at `:2565`). Agenda used to be a
search mode (`ad.N`) on the Search screen; it's now its own screen (route `agenda`,
drawer shortcut); Search answers "find a specific note", Agenda answers "what's
upcoming or overdue".

| Component | Shape |
|---|---|
| `AgendaHeader` | 36dp circular back glyph, day headline (21sp SemiBold, -0.21sp tracking) over a `PlexMono` 12.5sp `ink2` sub-line ("July 30 · 6 scheduled"), and a 36dp `surface2` r11 ⇅ levers button |
| `AgendaTabs` | Today/Upcoming switch: `surface2` r11 track, 3dp inset, active option raised on a `surface` r8 card with a 2dp shadow |
| `LeversPanel` | `surface` card, 1dp `line` border, r15: "GROUP BY" and "SHOW" chip rows plus two toggles |
| `LeverChip` | 12sp SemiBold, 12dp/7dp padding, r9; active = `accent`/`accentInk` fill, inactive = transparent with a 1dp `line` border. The "Show" row wraps in a `FlowRow`, since its middle chips are the vault's own TODO keywords and there can be any number of them |
| `LeverToggle` | 36×21 r11 track (`accent` on / `surface3` off) with a 16dp `surface` knob animated between 2.5dp and 17.5dp |
| `OverdueCard` | `redSoft` fill, 1dp `red` border, r15; collapsible ▸/▾ header with the count and a `surface` r9 "Move to today" button |
| `GroupHeader` | Uppercased 11sp Bold key (0.77sp tracking, `ink2`), `PlexMono` 11sp count, then a 1dp `line` rule to the right edge |
| `AgendaRowContent` | 20dp circular checkbox, state chip + title, `PlexMono` 11.5sp meta strip, 17dp r5 priority badge |
| `AgendaCheckbox` | 1.5dp ring tinted by priority (`line2` when unprioritised), filled `green` with a 12dp ✓ once done |
| `StateChip` | 9.5sp Bold `PlexMono`, r4: `NEXT`/done → `green`/`greenSoft`, `WAITING` → `ink3`/`surface2`, anything else → `synTodo`/`amberSoft` |

Two deliberate departures from the shared design system, both taken from the
prototype:

- **`agendaPriorityColor`** paints priority C **blue**, not the `green` that
  `GroveColors.priorityColor` uses. Green reads as "done" next to this screen's
  green checkboxes. Everywhere else (Search, `ResultRowContent`) keeps the shared
  scale.
- **`AgendaTabs`** is not `SegmentedControl`. The shared control fills its active
  option with `accent`; the prototype's agenda tabs raise it on a `surface` card.
  The card carries a **1dp `accent` border** and its label is `ink`, against
  `ink3` for the inactive option: the elevation shadow alone has almost nothing
  to cast against `surface2` on the dark themes, so the border is what actually
  marks the selection. Keep both levers if you restyle it: dropping either one
  puts the tabs back to reading as unselected in dark mode.

A completed row is faded wholesale via `Modifier.alpha(0.55f)` (checkbox, chip,
title, and meta together) rather than only muting the title color.

Section headers scroll with the list; the prototype has no pinned headers, so the
`stickyHeader` treatment this screen previously used was dropped.

**When to use**: Agenda-only. These are tuned to one prototype screen; reach for
`SegmentedControl`, `Pill`, and `ResultRowContent` elsewhere.

---

## Screen Inventory

| Screen | Route | Key components used |
|---|---|---|
| Onboarding | `onboarding` | `BrandMark`, `Pill` ("Recommended"), primary button |
| Notebooks | `notebooks` | `GroveTopBar`, `Pill` (sync badges), icon glyph tiles, FAB, `ReminderPermissionBanner` |
| Nav Drawer | (overlay) | `BrandMark`, plain `Text` rows, `★` favorites glyph |
| Outline | `outline/{notebookId}` | `GroveTopBar`, `annotateOrgInline`, keyword chips, `starColor()`, `FavoriteStar` |
| Read Note | `note/{noteId}?mode=read` | `GroveTopBar`, `SegmentedControl`, `annotateOrgInline`, tag chips, `FavoriteStar` |
| Edit Note | `note/{noteId}?mode=edit` | `GroveTopBar`, `SegmentedControl`, `OrgVisualTransformation`, formatting toolbar, `MetadataSheet` |
| Capture Picker | (bottom sheet) | `ModalBottomSheet`, icon glyph tiles, `PlexMono` |
| Capture Editor | `capture/{templateId}` | `GroveTopBar`, `monoBody()`, formatting toolbar |
| Search | `search` | `ResultRowContent`-based, file-grouped results (collapsible sticky headers, `line`-divided rows, `annotateOrgInline` title/snippet rendering with match highlighting layered on top, inline `priorityColor`-coded `[#P]` next to the title matching Agenda, 2-line-max snippet) wrapped in `SwipeCommitRow` (Agenda-style swipe-to-commit, not a reveal panel: swipe left-to-right fires "Cycle state" immediately on release, opening the shared `StatePickerSheet`; swipe right-to-left fires "Schedule" immediately on release, opening `PlanningDatesScreen` focused on SCHEDULED, applied across files via `SearchViewModel.setState`/`setPlanningDates`, since each row has exactly one action per direction), `Pill` (TODO pill), quick-start cards + Saved Searches (blank state, long-press → Rename/Delete `DropdownMenu`), Advanced expression preview + operator chips, `FilterPanel` (`ModalBottomSheet`) with faceted chip sections (Notebook/Tags/TODO state/Scheduled/Deadline/Priority + `CustomDateRangePicker` "Custom range" chip) |
| Agenda | `agenda` | "Agenda A · focus" prototype variant: `AgendaHeader` (day headline + ⇅), `AgendaTabs` (Today/Upcoming), collapsible `LeversPanel` (Group by: Date/Priority/Tag/File · Show: Open, one chip per configured todo-type keyword, Everything · tags and source-file toggles, all persisted in settings), `OverdueCard` (collapsible, bulk "Move to today"), scrolling `GroupHeader`s, `AgendaRowContent` rows (checkbox → toggle done, tap → open note) wrapped in `SwipeCommitRow` (swipe-left/right per Settings § Agenda: set scheduled, set deadline, or mark done). Every mutation is undoable via `GroveUndoSnackbar`; "Move to today" restores all files it touched. Infinite scroll on the Upcoming tab |
| Dates (SCHEDULED + DEADLINE) | (full-window dialog) | `PlanningDatesScreen`: shorthand box (`DateShorthandParser`), two-date calendar with lead-time band, per-section presets / time range / org repeater, raw org preview footer |
| Reschedule (from a reminder notification) | (own activity, `RescheduleActivity`) | The same `PlanningDatesScreen` over a transparent window in its own task. Confirming writes the dates, toasts "Task rescheduled to …", and finishes back to whatever app the user came from; it never enters Grove's own navigation |
| Conflict | `conflict/{notebookId}` | `GroveTopBar`, warning banner, unified diff view, action buttons |
| Settings (hub) | `settings` | `GroveTopBar`, a single `SettingsGroup` list of eight section pages (Look and Feel, Capture Templates, Sync, Notes, Agenda, Reminders, Sharing, Backup), each row a `SettingsRow` with a one-line description and `›` chevron; app version footer |
| Settings › Look and Feel | `settings/appearance` | `ThemeDropdownPicker` (theme, ordered light-then-dark alphabetically within each), sync-app-icon-with-theme preview tile, `SegmentedControl` (font size, default note mode) |
| Settings › Capture Templates | `settings/templates` | Reorderable template list, "＋ New template" row, capture-from-notification toggle |
| Settings › Sync | `settings/sync` | Folder picker, auto-sync mode list, periodic interval `SegmentedControl`, "View sync log" row |
| Settings › Notes | `settings/notes` | TODO keywords text field + "Apply" action, `SegmentedControl` (default priority, notebook display name, checklist states), Add ID/Add CREATED toggles |
| Settings › Agenda | `settings/agenda` | `DropdownPicker` (agenda swipe-left/swipe-right actions) |
| Settings › Reminders | `settings/reminders` | `ReminderPermissionBanner`, enable-reminders toggle, `SimpleTimePicker` (default reminder time) |
| Settings › Sharing | `settings/sharing` | Shared-content-target text field |
| Settings › Backup | `settings/backup` | Explanatory copy, side-by-side button pair (Export/Import settings) |

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

There is no "follow system" option: the app always uses the explicitly selected `ThemePreference`,
picked via `ThemeDropdownPicker` on the Settings screen and persisted through `SettingsRepository`.

Inside any composable: `MaterialTheme.grove` → full `GroveColors` token set.
