# Handoff: Grove — Android Org-Mode Notes App

## Overview

Grove is a native Android note-taking app for `.org` plain-text files. It provides a first-class mobile companion for Emacs/org-mode users, with automatic sync (Syncthing / WebDAV / Dropbox), capture templates, a full outline view, read mode, and full-text search.

Package name: `com.yourname.grove`  
Platform: Android (Kotlin, Jetpack Compose, Material 3)  
PRD: `prd-android-orgmode-app.md`

---

## About the Design Files

`Grove.dc.html` is a **high-fidelity interactive prototype** built in HTML — not production code. It demonstrates pixel-accurate layouts, colors, typography, interactions, and navigation flows for 9 screens plus an app icon spec.

**Your task:** Recreate these designs in Kotlin + Jetpack Compose, following the design tokens, typography, and interaction specs below. Use Compose's `MaterialTheme` as the foundation and override it with the custom token set. Do not ship the HTML prototype.

---

## Fidelity

**High-fidelity.** Colors, typography, spacing, corner radii, shadows, icon marks, org-syntax colors, and micro-interactions (bottom sheet, slide-in drawer, FAB, etc.) are all specified to production precision. Implement these pixel-accurately.

---

## Design Tokens

### Color System — Light Theme

| Token | Hex | Usage |
|---|---|---|
| `--bg` | `#f3ede1` | App background (scaffold, screen bg) |
| `--surface` | `#fbf8f1` | Cards, elevated surfaces |
| `--surface-2` | `#ece4d5` | Inset backgrounds, secondary surfaces |
| `--surface-3` | `#e3d9c6` | Pressed state, tertiary inset |
| `--ink` | `#2a251f` | Primary text |
| `--ink-2` | `#6c6356` | Secondary text, subtitles |
| `--ink-3` | `#9c9384` | Tertiary text, placeholders, hints |
| `--line` | `#e3dbcb` | Dividers, borders |
| `--line-2` | `#d3c9b6` | Secondary dividers |
| `--accent` | `#8a5a2b` | Primary actions, FAB, active nav, links |
| `--accent-ink` | `#fffaf2` | Text on accent backgrounds |
| `--accent-soft` | `rgba(138,90,43,0.12)` | Tinted chip/badge backgrounds |
| `--green` | `#4f7a3a` | DONE state, synced badge, success |
| `--green-soft` | `rgba(79,122,58,0.14)` | DONE chip background |
| `--amber` | `#a9761d` | TODO state, Modified badge, warnings |
| `--amber-soft` | `rgba(169,118,29,0.16)` | TODO chip background |
| `--red` | `#a5462f` | Priority A, DEADLINE overdue |
| `--red-soft` | `rgba(165,70,47,0.13)` | Priority/deadline background |
| `--blue` | `#3f6f86` | Timestamps, keywords, IN-PROGRESS state |
| `--blue-soft` | `rgba(63,111,134,0.13)` | Blue chip background |

### Color System — Dark Theme

| Token | Hex |
|---|---|
| `--bg` | `#16130e` |
| `--surface` | `#201c15` |
| `--surface-2` | `#2a251b` |
| `--surface-3` | `#352f23` |
| `--ink` | `#ece4d5` |
| `--ink-2` | `#b4a98f` |
| `--ink-3` | `#7c7460` |
| `--line` | `#322c20` |
| `--line-2` | `#433a2b` |
| `--accent` | `#cb9d62` |
| `--accent-ink` | `#1a160d` |
| `--accent-soft` | `rgba(203,157,98,0.18)` |
| `--green` | `#8fb46a` |
| `--green-soft` | `rgba(143,180,106,0.17)` |
| `--amber` | `#d7a64f` |
| `--amber-soft` | `rgba(215,166,79,0.17)` |
| `--red` | `#d2856a` |
| `--red-soft` | `rgba(210,133,106,0.16)` |
| `--blue` | `#7fb0c4` |
| `--blue-soft` | `rgba(127,176,196,0.16)` |

### Org Syntax Highlighting Colors

| Token | Light | Dark | Used for |
|---|---|---|---|
| `syn-star` | `#4f7a3a` | `#8fb46a` | Heading `*` stars |
| `syn-todo` | `#a9761d` | `#d7a64f` | TODO/active keywords |
| `syn-done` | `#4f7a3a` | `#8fb46a` | DONE/closed keywords |
| `syn-kw` | `#7d7466` | `#b4a98f` | :PROPERTIES:, :CREATED:, etc. |
| `syn-ts` | `#3f6f86` | `#7fb0c4` | Timestamps `<...>` `[...]` |
| `syn-tag` | `#8a5a2b` | `#cb9d62` | Tags `:tag:` |
| `syn-link` | `#3f6f86` | `#7fb0c4` | Links `[[url][label]]` |
| `syn-prop` | `#a39a89` | `#7c7460` | Property values, IDs |

### Typography

| Role | Family | Weight | Size | Usage |
|---|---|---|---|---|
| Display | IBM Plex Sans | 600 | 30sp | App name on onboarding |
| Title Large | IBM Plex Sans | 600 | 19sp | Screen titles in app bars |
| Title Medium | IBM Plex Sans | 600 | 17sp | Notebook/file names |
| Body Large | IBM Plex Serif | 400 | 16sp | Read mode body text |
| Body Medium | IBM Plex Sans | 400/500 | 14.5–15sp | List items, settings rows |
| Body Small | IBM Plex Sans | 400 | 13–13.5sp | Subtitles, descriptions |
| Caption | IBM Plex Sans | 500 | 11–12sp | Badges, chips, timestamps |
| Mono Body | IBM Plex Mono | 400 | 13.5sp | Raw org editor, file names, timestamps |
| Mono Bold | IBM Plex Mono | 600 | 14–24sp | Heading stars, org keywords, icon marks |

All three IBM Plex families (Sans, Serif, Mono) are available as downloadable fonts via Google Fonts or bundled as assets.

### Spacing Scale

| Name | Value | Usage |
|---|---|---|
| xs | 4dp | Gap between inline elements |
| sm | 8dp | Icon-to-label gaps, badge padding |
| md | 12–14dp | List item internal padding |
| lg | 16–18dp | Screen horizontal padding, card padding |
| xl | 22–26dp | Section top padding |
| Row height (standard) | 56dp | App bars |
| Row height (list item) | 56–64dp | Notebook rows, setting rows |
| FAB height | 54dp | Extended FAB |
| Bottom sheet handle | 38px wide, 4px tall, radius 3px | Sheet drag handle |

### Corner Radii

| Component | Radius |
|---|---|
| Screen / phone | 32dp (outer bezel only) |
| Cards / list tiles | 13–14dp |
| Large cards / sheets | 18dp |
| Bottom sheet top corners | 24dp |
| Chips / badges | 20dp (pill) or 5–9dp (square) |
| FAB (extended) | 17dp |
| FAB (icon-only) | 18dp |
| Icon tiles (notebook) | 12dp |
| App icon tile | ~28dp (squircle, ~27% of 104dp) |

### Shadows / Elevation

Light: `0 2px 10px rgba(60,45,25,0.10), 0 1px 2px rgba(60,45,25,0.06)`  
Dark: `0 6px 22px rgba(0,0,0,0.45), 0 1px 3px rgba(0,0,0,0.35)`

Use these as `elevation` overrides in Compose; avoid the default Material shadow tint system.

---

## Screens

### 1. Onboarding — Setup Sync

**Purpose:** First-run welcome, explains Syncthing setup, lets user pick a local folder or skip.

**Layout:**
- Scroll container, padded 26dp all sides
- Top section (flex-center, centered): brand mark (74×74dp squircle, `accent-soft` bg), app name (30sp 600), subtitle (15sp, `ink-2`, max 240dp)
- Syncthing setup card (`surface` bg, 18dp radius, 18dp padding):
  - Header row: "Recommended" green pill badge + "Sync with Syncthing" (15sp 600)
  - 3 numbered steps: circular badge (22×22dp, `accent-soft` / `accent` text) + step text (13.5sp `ink-2`)
  - Phone↔Laptop illustration placeholder (104dp tall, dashed `line-2` border, `surface-2` striped background, monospace labels)
- Bottom CTA area: Primary button "Choose a local folder" (50dp height, 14dp radius, `accent` bg, `accent-ink` text, 15sp 600) + Skip link "I'll set this up later" (46dp, transparent, `ink-2`)

**App icon mark (brand mark):**  
Three rounded bars each `3.4px × 18px` (`border-radius 2px`), rotated 0°/60°/120°, color `accent`. Container: `18×18dp`, centered in `34×34dp` squircle tile.

**Interactions:**  
Both buttons → navigate to Notebook List (Home).

---

### 2. Notebook List (Home)

**Purpose:** Default home screen. Shows all `.org` files with sync status.

**App Bar (56dp):**
- Leading: hamburger icon (3 bars, 18dp wide, 2px height, 3.5dp gap) → opens nav drawer
- Title: "Notebooks" (19sp 600)
- Trailing: magnifier icon → Search, ↻ sync icon (`green`, 18sp 700) → Conflict screen, ⋮ overflow

**Sync Status Strip** (below app bar):
- `surface` bg, `line` border, 11dp radius, 9–13dp padding, horizontal flex
- ✓ green checkmark + "Synced 2 min ago · Local folder · On open/close" (`ink-2` 12.5sp)
- Right: amber "1 conflict" pill badge → Conflict screen
- Tappable row, cursor pointer

**Notebook List Rows** (each ~64dp tall):
Each row: `border-radius 14dp`, hover `surface` bg. Internal layout:
- Icon tile 42×42dp, `border-radius 12dp`, colored soft bg, monospace glyph (✦✸✺❋✷) 17sp 600 in matching foreground color
- Name (15sp 600) + subtitle "N notes · time ago" (12.5sp `ink-2`)
- Right: sync badge (`Modified` = amber pill, `Conflict` = amber pill with orange dot, `✓` = green text)

Notebook icon colors:
| Notebook | Tile bg | Glyph color | Glyph |
|---|---|---|---|
| travel.org | `green-soft` | `green` | ✦ |
| journal.org | `accent-soft` | `accent` | ✶ |
| ideas.org | `blue-soft` | `blue` | ✸ |
| recipes.org | `red-soft` | `red` | ✺ |
| reading.org | `green-soft` | `green` | ❋ |
| inbox.org | `accent-soft` | `accent` | ✷ |

**FAB** (Extended, bottom-right, 18dp from edge):
- 54dp height, 17dp radius, `accent` bg, `accent-ink` text
- "+ Capture" label (15sp 600), plus sign 21sp
- `box-shadow: 0 6px 18px rgba(138,90,43,0.4)`
- Tap → Capture Picker

---

### 3. Navigation Drawer

**Trigger:** Hamburger tap from Home.  
**Overlay:** Scrim `rgba(20,14,6,0.42)` over full screen; tap scrim → dismiss.  
**Drawer panel:** 300dp wide, `surface` bg, slides in from left (animate translateX -100%→0, 240ms cubic-bezier(0.2,0.8,0.2,1)).

**Header** (padded 22dp, border-bottom `line`):
- Brand mark 42×42dp squircle (same 3-bar asterisk construction, `accent`)
- "Grove" (18sp 600)
- "~/org · 461 notes" (`ink-2` 12sp, `IBM Plex Mono`)

**Nav Items** (11–14dp padding, 11dp radius):
- All Notes (≡ icon) → Search
- **Notebooks** (active, `accent-soft` bg, `accent` text, ✦ icon, 600 weight)
- Section label "SEARCHES" (11sp uppercase `ink-3`)
- Scheduled Today (⌖ icon) → Search
- All TODO (⌖ icon) → Search
- This Week (⌖ icon) → Search
- Divider 1px `line`
- Agenda (▤ icon) → Search
- Settings (⚙ icon) → Settings

---

### 4. Outline View (travel.org)

**Purpose:** Heading tree of a single notebook file.

**App Bar (56dp):** ← back (21sp) → Home | mono title "travel.org" (17sp 600) + "18 notes · synced ✓" (11.5sp `ink-2`) | magnifier | ⋮

**Outline Nodes:**

Each node row: caret (▾ expanded / ▸ collapsed / invisible for leaf, 12sp `ink-3`), stars (`*`/`**`/`***` in `syn-star`, `IBM Plex Mono` 600), optional TODO chip, heading text, tags right-aligned.

Child nodes: `margin-left 22dp`.

Fold indicator: child count ("… 3") in `ink-3` 11sp.

Scheduled/deadline shown as a small chip below heading text:
- Scheduled: `◷ Wed, Apr 9` in `blue` 11sp mono, or
- DEADLINE: `DEADLINE: Fri Jun 27` `red-soft` bg, `red` text, 5dp radius

TODO chips:
- `TODO`: amber soft bg, amber text, 5dp radius, 11sp 700 mono
- `DONE`: green soft bg, green text, strikethrough on heading
- `IN-PROGRESS`: blue soft bg, blue text

Priority `[#A]`: `red` `IBM Plex Mono` 11sp 700

Tag `":admin:"`: `syn-tag` color, 11sp mono, right edge

**Mock content (travel.org):**
```
▾ * Japan — Spring 2025                                    :trip:
    ○ ** TODO  Book Kyoto ryokan               :booking:
         ◷ Wed, Apr 9
    ▸ ** Kyoto — Day 2  … 3                    :kyoto:
    ○ ** DONE  ~~Order JR Rail Pass~~
――――――――――――――――――――――――――――――――――――
▸ * Portugal — Summer  … 6                               :trip:
▸ * Weekend trips — ideas  … 4                           :ideas:
○ * TODO [#A]  Renew passport                            :admin:
     DEADLINE: Fri, Jun 27
```

**FAB:** 54×54dp icon-only, `accent` bg, "+" 26sp. Tap → Capture Editor (datetree).

---

### 5. Read Mode

**Purpose:** Rendered (non-raw) view of a note. Default view when opening a note.

**App Bar (56dp):** ← back | flex spacer | Read/Edit segmented toggle (`surface-2` bg, 10dp radius container, 3dp padding, 8dp radius pills, `accent`/`accent-ink` active) | ⋮

**Content** (padded 24dp, scroll):
1. Tag chips row: "trip", "kyoto" → `accent-soft` bg, `accent` text, 11sp 600, 20dp radius, 3×9dp padding, gap 7dp
2. H1: "Kyoto — Day 2" → `IBM Plex Serif` 25sp 600, letter-spacing -0.01em
3. Created date: "Created Mon, Apr 14 2025" → `ink-3` 12.5sp `IBM Plex Mono`
4. Body paragraph: `IBM Plex Serif` 16sp 400, 1.65 line-height. **Bold** = 600, *italic* = italic
5. H2: "Lunch" → `IBM Plex Serif` 19sp 600, margin-top 24dp
6. Inline code: `IBM Plex Mono` 13.5sp, `surface-2` bg, 5dp radius
7. Links: `syn-link` color, underline `line-2`
8. TODO note card: `surface` bg, `line` border, 14dp radius, 15–16dp padding. Contains TODO chip + heading + scheduled row
9. Unordered list: `IBM Plex Serif` 16sp, 1.7 line-height, 20dp left inset

**Read ↔ Edit toggle:**  
"Read" active → `accent` bg, `accent-ink` text, 13sp 600.  
"Edit" → transparent bg, `ink-2` text.  
Tapping "Edit" → navigate to Editor screen (same note).

---

### 6. Raw Org Editor

**Purpose:** Edit the raw `.org` source with syntax highlighting.

**App Bar (56dp):** ← back | spacer | Read/Edit toggle (Edit active) | ↻ sync icon (`green` 700)

**Editor body** (padded 18dp, scroll, `IBM Plex Mono` 13.5sp, 1.85 line-height):

Syntax highlighting applied to each token inline:

| Token type | Color token | Style |
|---|---|---|
| `*` heading stars | `syn-star` | 600 |
| Heading text | `ink` | 600 |
| `:tags:` | `syn-tag` | normal |
| `:PROPERTIES:` / `:END:` | `syn-prop` | normal |
| `:KEY:` property keys | `syn-kw` | normal |
| Property values | `syn-prop` | normal |
| `[timestamps]` `<timestamps>` | `syn-ts` | normal |
| `*bold*` | `syn-tag` | 600 |
| `/italic/` | `green` | italic |
| `=verbatim=` | `accent`, `surface-2` bg, 3dp radius | normal |
| `[[url][label]]` | `syn-link` | underline |
| `TODO` keyword | `amber` | 700 |
| Body text | `ink` | normal |

Blinking cursor: 2×18dp rect in `accent` color, vertical in text flow.

**Formatting Toolbar** (48dp height, `surface` bg, `line` border-top):
Left-to-right: **B** (700), *I* (italic), U̲ (underline), `</>` (mono 13sp), divider, `[[]]` (mono link, 12sp 600, `syn-link`), ◷ (timestamp, `syn-ts`), `*` (mono 700, `syn-star`), flex spacer, ⌄ keyboard dismiss.  
Each button: 38×34dp, 8dp radius, hover `surface-2`.

---

### 7. Capture Template Picker

**Purpose:** Bottom sheet overlay for selecting a capture template. Triggered by Capture FAB.

**Presentation:**  
Full-screen scrim `rgba(20,14,6,0.5)` + bottom sheet. Sheet slides up from off-screen (translateY 100%→0, 260ms cubic-bezier(0.2,0.85,0.25,1)).

**Sheet structure** (`surface` bg, 24dp top radius, 10–26dp padding):
- Drag handle: 38dp wide, 4dp tall, `line-2` bg, radius 3dp, margin-top 6dp auto
- Header row: "Capture to…" (18sp 600) + "Manage" (`accent` 13sp 600) right-aligned → Settings
- Template rows (48dp min-height, 22dp horizontal padding, `surface-2` hover):
  - Icon tile 44×44dp, 13dp radius
  - Name (15.5sp 600) + target "notebook.org · location" (12.5sp `ink-2` mono)
  - Chevron "›" right

**Template rows:**
| Name | Icon tile bg | Glyph | Glyph color | Target |
|---|---|---|---|---|
| Journal Entry | `accent-soft` | ✶ | `accent` | journal.org · datetree |
| Quick Note | `green-soft` | ✷ | `green` | inbox.org · bottom of file |
| TODO | `amber-soft` | ✓ | `amber` | inbox.org · prompts for title |

Tap any row → Capture Editor.

---

### 8. Capture Editor (Datetree Journal)

**Purpose:** Pre-filled org editor for a specific template. After save, inserts entry into the file at the configured datetree location.

**App Bar (56dp):** × dismiss (22sp) | "Journal Entry" (17sp 600) | **Save** button (38dp height, 11dp radius, `accent` bg, 14sp 600) → Read mode

**Datetree Breadcrumb** (11dp vertical padding, 18dp horizontal, `surface` bg, `line` border-bottom):
Horizontal flex: "inserts under" (`ink-3`) · `journal.org` (`accent` 600 mono) › `2025` › `June` › `Jun 11 Wed` (`ink` bg `accent-soft` 5dp radius) · "auto-created" (green pill right)  
Font: `IBM Plex Mono` 11.5sp.

**Editor body** (mono 14sp 1.9 line-height, padded 20dp):
```
**** <2025-06-11 Wed 14:32>        ← syn-star + syn-ts
Ferry back from the island...      ← ink body text + blinking cursor
```

**Toolbar** (same structure as Editor, but simplified): B, I, ◷, spacer. Plus "%T expanded · %cursor here" hint (`ink-2` 11.5sp mono, left side).

---

### 9. Full-text Search

**Purpose:** Search notes by text. Supports both plain and advanced structured queries.

**Layout** (no separate app bar — inline search):
- Top row: ← back + search field. Field: `surface` bg, `line` border, 13dp radius, 42dp height, 12dp padding. Leading: magnifier 14dp (ring 1.8dp + diagonal bar). Trailing: × clear. Content: query "ramen" (15sp `ink`).
- Results meta row: "⚑ Advanced" chip toggle + "3 results across 3 notebooks" (`ink-2` 12.5sp)

**Advanced chip (toggle):**  
When inactive: `surface` bg, `line` border, `ink-2` text, 9dp radius.  
When active: `accent` bg, `accent-ink` text.

**Advanced Panel** (animated in, 200ms):
`surface` bg, `line` border, 13dp radius, 13–14dp padding, `margin 0 14dp 6dp`.  
Query input row: 36dp, `bg` background, `line` border, 9dp radius, mono 13sp.  
Below: grid of operator reference chips (`ink-2`, `surface-2` bg, 6dp radius, 8dp padding 3dp vertical, 11sp mono): `t.TAG`, `i.STATE`, `s.PERIOD`, `b.NOTEBOOK`, `p.PRIORITY`, `ad.DAYS`

**Results list** (padded 4dp top, 14dp sides):  
Each result: 13dp radius, `surface` hover bg.
- Heading (15sp 600)
- Optional metadata chip (e.g. "recipe" `accent-soft` pill)
- Snippet (13.5sp `ink-2`, 1.5 line-height). Matched term: `amber-soft` bg, `amber` text, 600, 3dp radius
- Breadcrumb: "notebook.org › Heading" (`ink-3` 11.5sp mono)

Divider: 1px `line` at 2dp–14dp margin.

---

### 10. Sync Conflict Picker

**Purpose:** Resolve a conflict where both phone and laptop edited the same file.

**App Bar (56dp):** ← back | "Resolve conflict" (17sp 600) + "journal.org" (11.5sp `ink-2` mono)

**Content** (padded 16dp, scroll):

**Warning banner** (`amber-soft` bg, `amber` border, 13dp radius, 13–14dp padding):
- Left: amber circle "!" badge (20dp, `amber` bg, `surface` text, 13sp 700)
- Text: "Both your phone and laptop changed…" (13sp, 1.5 line-height)

**Diff cards** (`surface` bg, `line` border, 13dp radius, `IBM Plex Mono` 12.5sp 1.7 line-height):
- Section label: uppercase `ink-3` 12sp letter-spacing 0.06em "ON THIS PHONE · 14:02"
- Diff content: org syntax colored + added lines in `green-soft` bg with `+` prefix

**Action buttons:**
- "Keep both (merge under CONFLICT)": 48dp, 13dp radius, `accent` bg, `accent-ink` text, 14.5sp 600 (full width)
- "Keep phone" + "Keep laptop": side-by-side, 46dp, 13dp radius, `surface` bg, `line` border, 14sp 600 (each flex:1)

**Footer note:** "Syncthing also keeps a `.sync-conflict` copy…" (`ink-3` 11.5sp centered)

---

### 11. Settings

**Purpose:** Configure appearance, capture templates, sync, and notes.

**App Bar (56dp):** ← back | "Settings" (19sp 600)

**Section groups** — each section:
- Label: 12sp uppercase letter-spacing 0.07em `accent` 600, `margin 0 4dp 10dp`
- Group container: `surface` bg, `line` border, 15dp radius, overflow hidden, margin-bottom 24dp
- Rows: 14dp vertical padding, 15dp horizontal, `line` border-bottom between rows (except last)

**Appearance group:**
- **Theme row**: label "Theme" (14.5sp 500) + segmented `Light / Dark` (`surface-2` bg container, 10dp radius, 3dp padding, `accent`/`accent-ink` active pill, `ink-2` inactive, 13sp 600, flex:1)
- **Font size row**: label + segmented `Small / Medium / Large` (same pattern)
- **Default note mode row**: label + chevron disclosure "Read ›"

**Capture Templates group:**
- Template rows: icon tile 36×36dp (10dp radius) + name (14.5sp 500) + mono target (12sp `ink-2`) + ⋮⋮ drag handle right
- "＋ New template" row: `accent` 14sp 600, 18sp + glyph

**Sync group:**
- Repo row: icon 36×36dp `blue-soft`/`blue` ▣ + name + "default" green pill badge + path (12sp mono `ink-2`) + ✓ green
- Auto-sync row: "On open/close ›" disclosure
- "＋ Add repository" action row

**Notes group:**
- TODO keywords row: shows keyword chips (TODO amber, IN-PROGRESS blue, | separator, DONE green, CANCELLED `surface-2`)
- Toggle rows (with switch): "Add ID to new notes", "Add CREATED timestamp"  
  Switch: 42×25dp, 14dp radius, `accent` bg when on; thumb 19dp white circle, right-aligned

**Footer:** `com.yourname.grove` monospace `ink-3` 11.5sp centered.

---

## App Icon

**Design:** A single org-mode asterisk mark — three rounded bars rotated 0°/60°/120°, color `accent` (light: `#8a5a2b`, dark: adapts), on an `accent-soft` squircle tile.

**Construction (vector):**
- Tile: adaptive icon squircle, `accent-soft` fill, subtle 1dp `line` border
- Three bars: each `11dp wide × 64dp tall`, `border-radius 6dp`, `accent` fill
- Rotation: 0°, 60°, 120° around center

**Sizes to export:** 108×108 (adaptive icon foreground), 192×192, 144×144, 96×96, 72×72, 48×48, 36×36dp baseline.

**Adaptive icon:** Foreground layer = asterisk mark centered in 108×108 canvas. Background layer = solid `#efe4cf` (or `accent-soft` using tinted background).

This exact mark is reused as the brand icon throughout the app (top bar, nav drawer header, onboarding hero, notification icon).

---

## Interactions & Animations

| Interaction | Animation |
|---|---|
| Nav drawer open | TranslateX -100%→0, 240ms, cubic-bezier(0.2,0.8,0.2,1); scrim fade 0→0.42, 200ms |
| Nav drawer close | Reverse |
| Bottom sheet up | TranslateY 100%→0, 260ms, cubic-bezier(0.2,0.85,0.25,1) |
| Scrim appear | Opacity 0→1, 200ms |
| Advanced panel expand | Opacity 0→1 + translateY 6dp→0, 200ms ease |
| Screen navigation | Standard Compose NavHost transitions (slide) |
| FAB tap | 0.92 alpha flash (active state) |
| Blinking cursor | 1-second steps(1) alternating opacity (1s blink interval) |
| Theme switch | Instant (no transition — CSS var transitions are unreliable; use `remember` + `derivedStateOf`) |
| List row hover/press | Background changes to `surface` / `surface-2` |

---

## State Management

```kotlin
// Key ViewModel state
data class AppState(
    val theme: ThemePreference,          // SYSTEM / LIGHT / DARK
    val currentScreen: Screen,
    val drawerOpen: Boolean,
    val currentNotebookId: String?,
    val currentNoteId: String?,
    val noteOpenMode: NoteMode,          // READ / EDIT
    val searchQuery: String,
    val searchAdvanced: Boolean,
    val fontSize: FontSizePreference,    // SMALL / MEDIUM / LARGE
)
```

Navigation: use `NavHost` with routes: `onboarding`, `notebooks`, `outline/{notebookId}`, `note/{noteId}?mode=read`, `note/{noteId}?mode=edit`, `capture`, `capture/{templateId}`, `search`, `conflict/{notebookId}`, `settings`.

---

## Files in This Package

| File | Description |
|---|---|
| `README.md` | This document — complete design spec |
| `Grove.dc.html` | Interactive high-fidelity prototype (open in Chrome/Edge) |

Open `Grove.dc.html` directly in a browser to interact with all 9 screens. Use the "Jump to" chips to navigate between screens and the Light/Dark toggle to switch themes.

---

## Implementation Notes (Kotlin / Compose)

- Use `BasicTextField` with custom `VisualTransformation` for the org syntax editor. The `VisualTransformation` should tokenize the org line and apply `SpanStyle` per token type using the syn-* color tokens above.
- For read mode, build a custom `AnnotatedString` renderer that maps org AST nodes to Compose composables (`Text` with `SpanStyle`, `Column` for blocks, custom `OrgTable` composable deferred to v2).
- `MaterialTheme.colorScheme` — override `background`, `surface`, `primary`, `onPrimary`, `onBackground`, `secondary` etc. with the custom token values. Add extension properties for the non-Material tokens (`accent`, `ink`, `syn-star`, etc.).
- Font loading: add IBM Plex Sans, Serif, Mono to `res/font/` as downloadable fonts or bundled TTFs. Define a custom `Typography` object.
- Navigation drawer: use `ModalNavigationDrawer` (Material 3) with custom drawer content matching the spec.
- Bottom sheets: use `ModalBottomSheet` (Material 3).
- Sync status in app bar: use a foreground `Service` notification icon + `WorkManager` for background sync jobs.
