# Handoff: Grove Launch Site — "The Buffer" (variant 1a)

## Overview

Grove is a native Android app for note-taking and task management on top of plain `.org`
files stored locally on device. This handoff covers its **launch website**: a marketing
landing page plus a documentation section.

The chosen design direction is **"The Buffer" (variant 1a)** of three that were explored.
Its organizing conceit: *the website is itself an org file.* The page uses real org-mode
syntax as its layout vocabulary — `#+TITLE:` / `#+SUBTITLE:` keyword lines in the hero,
`* Features` and `** Capture` as section headings, `:tags:` in the right margin,
`#+END` to close the document. Feature sections **fold and unfold like org headlines**.

This bundle contains the landing page. The docs index, sidebar outline tree, and
individual doc pages are **not yet designed** — see "Not Yet Designed" at the end.

The site is intended to be built with **Astro**, because the documentation already exists
as `.mdx` files ready to be imported by a static site generator.

## About the Design Files

The files in `reference/` are **design references created in HTML** — a prototype showing
the intended look and behavior. They are **not production code to copy directly.**

The prototype is authored in a bespoke HTML component format that will not exist in your
codebase (a `<x-dc>` custom element, `{{ }}` template holes, `<sc-if>` conditionals, and a
`support.js` runtime). **Do not try to port that format.** Read it as a specification and
**recreate the design in Astro** using ordinary `.astro` components, plain CSS (or Tailwind,
if the project adopts it), and a few lines of vanilla JS for the two interactive behaviors.

All styling in the prototype is written as **inline `style` attributes**. This was a
constraint of the prototyping environment, not a recommendation. In the real build, move
these into component-scoped styles or utility classes.

The reference page can be opened directly in a browser:
`reference/Grove Landing 1a.dc.html` (keep `support.js` and `assets/` beside it).

## Fidelity

**High-fidelity.** Colors, typography, spacing, and interaction behavior are final and
intentional. Recreate the visual design faithfully. Every hex value, font size, and
spacing value in this README is the real one used in the prototype.

Two caveats where judgment is expected:

1. **The prototype is desktop-only.** It was authored at a fixed 1240px width with no
   media queries. Responsive behavior is specified in prose below and is your call to
   implement — do not infer that the site should be fixed-width.
2. **Copy is a first draft.** The tone brief was "playful, in on the Emacs joke." The
   client has not yet signed off on the jokes. Implement the copy verbatim as given, but
   expect revisions; keep all strings easy to edit (ideally in content files, not
   hardcoded in markup).

---

## Design Tokens

Implement as CSS custom properties on `:root`, with a `[data-theme="dark"]` override block.
This is exactly how the prototype does it and it should carry over unchanged.

Both themes are derived from the Grove Android app's own light and dark themes, so the
site and the app read as the same product. **Light is the default** — it is friendlier to
first-time visitors, even though the app itself ships dark.

### Light (default)

| Token | Hex | Used for |
|---|---|---|
| `--bg` | `#faf6ee` | Page background (warm paper) |
| `--bg2` | `#f3ecdf` | Raised panels, code blocks, footer band |
| `--bg3` | `#ebe2d1` | Inset chips, input wells |
| `--ink` | `#2b2620` | Primary text, headings |
| `--ink2` | `#655b4e` | Body text, secondary |
| `--ink3` | `#756a5b` | Muted text, `#` comment lines |
| `--ink4` | `#9a8d7b` | Deliberately faded: `:PROPERTIES:` drawer lines only |
| `--line` | `#e2d8c5` | All borders and rules |
| `--accent` | `#8f5c1c` | Links, primary buttons, `TODO` keyword, org keywords |
| `--accent-soft` | `#f2e0c2` | Accent chip backgrounds, modeline fill |
| `--green` | `#55752f` | Org headline stars `*`, `:tags:`, list bullets |
| `--green-soft` | `#e3ecd3` | (reserved) |
| `--red` | `#b04f34` | `[#A]` priority, `DEADLINE` |
| `--red-soft` | `#f7ded3` | (reserved) |
| `--blue` | `#3c6a8c` | `SCHEDULED:` and timestamps |
| `--blue-soft` | `#dce8f0` | (reserved) |
| `--violet` | `#7a5a95` | `%^{prompt}` template placeholders |
| `--shadow` | `0 1px 2px rgba(60,45,25,.05), 0 12px 32px -12px rgba(60,45,25,.16)` | Screenshot cards |

### Dark

| Token | Hex |
|---|---|
| `--bg` | `#12100e` |
| `--bg2` | `#1c1915` |
| `--bg3` | `#262119` |
| `--ink` | `#f0ebe1` |
| `--ink2` | `#b0a89b` |
| `--ink3` | `#8d8376` |
| `--ink4` | `#6a6157` |
| `--line` | `#302a22` |
| `--accent` | `#dfae70` |
| `--accent-soft` | `#3a2f1b` |
| `--green` | `#94c06e` |
| `--green-soft` | `#27351d` |
| `--red` | `#de8a6b` |
| `--red-soft` | `#3a241c` |
| `--blue` | `#74a5c6` |
| `--blue-soft` | `#1c2a33` |
| `--violet` | `#a98bc4` |
| `--shadow` | `0 1px 2px rgba(0,0,0,.4), 0 16px 40px -14px rgba(0,0,0,.6)` |

**Accessibility note:** the light-mode `--accent` (`#8f5c1c`), `--green` (`#55752f`),
`--ink2` and `--ink3` were each darkened during design review specifically to clear
WCAG AA (4.5:1) against `--bg` and `--bg2`. Do not lighten them back toward the app's
in-product values. `--ink4` is intentionally below AA — it is decorative only, used for
org drawer lines that mimic Emacs' faded drawer face, and never for information the
reader needs.

### Typography

Three families, loaded from Google Fonts:

- **`Source Serif 4`** (400/600/700, optical sizing 8–60) — display and long-form prose.
  This is the site's voice.
- **`Public Sans`** (400/500/600/700) — UI text, feature body copy, buttons.
- **`JetBrains Mono`** (400/500/700) — *all* org syntax, metadata, filenames, code, and
  eyebrow labels. This carries the entire org-mode theme; do not substitute it casually.

Single stylesheet link:

```
https://fonts.googleapis.com/css2?family=Public+Sans:ital,wght@0,400;0,500;0,600;0,700;1,400&family=Source+Serif+4:ital,opsz,wght@0,8..60,400;0,8..60,600;0,8..60,700;1,8..60,400&family=JetBrains+Mono:wght@400;500;700&display=swap
```

Type scale as used (all sizes px):

| Role | Family | Size | Weight | Line-height | Notes |
|---|---|---|---|---|---|
| Hero title (`Grove`) | JetBrains Mono | 52 | 700 | 1.1 | `letter-spacing: -.02em` |
| Hero prose | Source Serif 4 | 22 | 400 | 1.55 | `max-width: 52ch` |
| Section heading (`* Features`) | JetBrains Mono | 26 | 700 | — | |
| Headline (`** Capture`) | JetBrains Mono | 21 | 500 | — | |
| Feature prose | Source Serif 4 | 19 | 400 | 1.6 | |
| Org source / metadata | JetBrains Mono | 15 | 400 | 1.6 | Hero keyword lines |
| Code block | JetBrains Mono | 12.5 | 400 | 1.85 | |
| Feature bullet list | JetBrains Mono | 13 | 400 | 2.0 | |
| Tag column, comments | JetBrains Mono | 12–12.5 | 400 | 1.9 | |
| Button label | JetBrains Mono | 14 | 500 | — | |
| CTA prose | Source Serif 4 | 20 | 400 | 1.6 | `max-width: 60ch` |

Apply `text-wrap: pretty` to all prose blocks.

### Spacing & shape

- Page section padding: `56px` horizontal throughout; `64px` top on hero, `56px` on bands.
- Feature list rows: `22px` vertical padding on the clickable headline row; unfolded body
  gets `0 0 40px 40px` (the 40px left indent is the org indentation — keep it).
- Border radius: `4px` chips/badges, `6px` buttons and inputs, `8px` panels and
  screenshot cards, `10px` inner screenshot images.
- Rules: `1px solid var(--line)`. Feature rows are separated by `border-top`, and the
  last row also carries `border-bottom`.

---

## Screens / Views

### Landing page

Single scrolling page. Max content width `1240px`, centered. Nine bands top to bottom.

#### 1. Buffer bar (sticky candidate — currently static)

Full-width strip, `padding: 14px 28px`, `background: var(--bg2)`,
`border-bottom: 1px solid var(--line)`, all text JetBrains Mono 13px.

- **Left:** a green `✳` glyph (15px, `var(--green)`), then `grove.org` in `var(--ink)`
  weight 500, then `— 1 of 1 buffers` in `var(--ink3)`.
- **Right:** nav links `features`, `docs`, `source` (`var(--ink2)`, 20px gap), then the
  **theme toggle**, styled as an Emacs minibuffer command: a bordered pill
  (`var(--bg3)` fill, `4px 10px`, radius 4) reading `M-x ` in `var(--accent)` followed by
  `grove-dark-theme` in `var(--ink)`. The word `dark`/`light` is *the theme you will switch
  to*, not the current one. Border turns `var(--accent)` on hover.

#### 2. Hero — split source/rendered

Two columns: `minmax(0,1fr)` and `420px`, `gap: 56px`, `padding: 64px 56px 56px`,
`align-items: start`.

**Left column** (`gap: 26px`):

Four org keyword lines, JetBrains Mono 15px, keywords in `var(--ink3)`:

```
#+TITLE:    Grove                          ← value is 52px/700, the actual page H1
#+SUBTITLE: notes and tasks in org-mode, on Android    ← var(--ink2)
#+STARTUP:  overview                       ← var(--ink2)
#+FILETAGS: :plaintext:offline:yours:       ← var(--green)
```

`#+TITLE:` has no top margin; `#+SUBTITLE:` gets `margin-top: 10px`.

Then hero prose (Source Serif 4, 22px, `var(--ink2)`, `max-width: 52ch`), with `.org`
inline in mono at `var(--ink)` weight 600:

> Your vault is a folder of `.org` files. Grove reads it, renders it properly, and writes
> back plain text. No Lisp interpreter in your pocket required — though nobody's stopping you.

Then the CTA row (`flex`, `gap: 12px`, `flex-wrap: wrap`):

- **Primary:** `★ Star on GitHub` — `var(--accent)` fill, `var(--bg)` text, `13px 22px`,
  radius 6, mono 14/500. Hover `opacity: .88`.
- **Email capture:** a single bordered unit (`var(--bg2)`, radius 6, `overflow: hidden`,
  `flex-wrap: nowrap`) containing a `>` shell prompt glyph in `var(--ink3)`, a
  transparent borderless `<input>` (placeholder `you@example.com`, `flex: 1 1 auto`,
  `min-width: 0`), and a `notify me` action in `var(--accent)` separated by a
  `border-left`, with `white-space: nowrap`. The nowrap/min-width rules matter — without
  them the label wraps at narrow widths.

Closing note, mono 12px `var(--ink3)`:
`# pre-1.0 · APKs land on GitHub Releases · F-Droid when it's ready`

**Right column** — the product argument in one image. Two stacked cards under a pair of
11px uppercase labels (`var(--ink3)`, `letter-spacing: .1em`) reading **what you wrote**
(left) and **what Grove shows** (right), justified to opposite ends.

- **Top card:** a fake org buffer. `var(--bg2)` fill, `1px` border, radius 8,
  `padding: 18px 20px`, mono 12.5px, `line-height: 1.85`. Content, with each token in its
  semantic color:

  ```
  * Inbox :capture:
  ** TODO [#A] Finish Q3 roadmap deck
     :work:planning:
     SCHEDULED: <2026-07-31 Thu>
     :PROPERTIES:
     :CREATED: [2026-07-28 Mon]
     :END:
     Need to lock the slide order
     before the review.▌
  ```

  Stars `*`/`**` → `--green`. `TODO` → `--accent` weight 700. `[#A]` → `--red`.
  Headline text → `--ink`. `:work:planning:` and `:capture:` → `--ink3`.
  `SCHEDULED:` and its timestamp → `--blue`. Drawer lines → `--ink4`. Body → `--ink2`.
  The trailing `▌` is a blinking cursor: `var(--accent)`,
  `animation: blink 1.1s step-end infinite` with `@keyframes blink {0%,49%{opacity:1} 50%,100%{opacity:0}}`.

- **Bottom card:** `assets/read-mode.png` — the same note as Grove actually renders it.
  Wrapped in a `#12100e` card, `10px` padding, radius 8, `var(--shadow)`, image radius 4.

The pairing is the point: identical content, raw on top, rendered below. Preserve it.

#### 3. Filetags band

Three equal columns, no gap, separated by `border-right`, bounded top and bottom by
`1px solid var(--line)`, `background: var(--bg2)`, each cell `padding: 22px 28px`.
Each cell: a mono 12px tag in `var(--green)`, then 14.5px body in `var(--ink2)`.

| Tag | Copy |
|---|---|
| `:plaintext:` | Files on disk, not rows in a database. The index rebuilds from them. |
| `:offline:` | No account, no server, no telemetry. Pair it with Syncthing and forget about it. |
| `:yours:` | Open source. Delete Grove tomorrow and every note still opens in Emacs. |

These three tags are the same ones listed in the hero's `#+FILETAGS:` line. That echo is
deliberate — if copy changes, change both.

#### 4. Features heading

`padding: 56px 56px 20px`. Mono 26px/700 `var(--ink)`, with the leading `*` in
`var(--green)`: `* Features`. Below it, mono 12.5px `var(--ink3)`:
`# TAB folds a headline. It works here too — go on.`

#### 5. Feature list — five foldable org headlines

This is the interactive centerpiece. Five rows in `padding: 0 56px 40px`.

Each row's **headline** is a clickable flex row (`align-items: baseline`, `gap: 12px`,
`padding: 22px 0`, `cursor: pointer`, all mono):

1. Fold arrow — `▾` when open, `▸` when closed. `var(--ink3)`, 12px, fixed `width: 14px`
   so text doesn't shift between states.
2. `**` in `var(--green)` 18px.
3. Title, 21px weight 500, `var(--ink)`.
4. Spacer (`flex: 1`).
5. Right-aligned `:tags:` in `var(--ink3)` 12.5px.

The **body** below is conditionally rendered, indented `40px` on the left, `40px` bottom
padding, laid out as a grid: prose column `minmax(0,1fr)` plus one or two `300px`
screenshot columns, `gap: 36px`, `align-items: start`. Prose is Source Serif 4 19px
`var(--ink2)`; bullet lists are mono 13px with `-` markers in `var(--green)`.
Screenshots sit in `#12100e` cards, `8px` padding, radius 10, inner image radius 5.

| # | Title | Tags | Screenshots |
|---|---|---|---|
| 1 | Capture | `:capture:templates:` | `capture-picker.png`, `capture-editor-datetree.png` |
| 2 | Agenda | `:agenda:planning:` | `agenda-overdue.png` |
| 3 | Search | `:search:facets:` | `search-results.png`, `search-filters.png` |
| 4 | Sync & conflicts | `:sync:syncthing:` | `notebooks-list.png` |
| 5 | Rendered, not dumped | `:reading:themes:` | `outline-view.png`, `edit-mode.png` |

Full copy for each is in the reference HTML; a few details worth calling out because they
carry specific colors:

- **Capture** bullets include `grove://capture` in `var(--accent)` and `%^{prompt}` in
  `var(--violet)`.
- **Agenda** prose sets `SCHEDULED` in `var(--blue)` and `DEADLINE` in `var(--red)`, both
  in mono 15px inline within serif prose.
- **Search** shows query examples as mono with facets in `var(--accent)` and the
  explanation in `var(--ink3)`: `t.work + s.7d → scheduled this week, tagged work`.

#### 6. CTA band

`padding: 56px`, `background: var(--bg2)`, `border-top: 1px solid var(--line)`,
left-aligned (`align-items: flex-start`), `gap: 22px`.

Mono 26px/700 `* Get Grove` (star green). Then Source Serif 4 20px `var(--ink2)`,
`max-width: 60ch`:

> Not released yet. The code is open, the builds are coming, and the mailing list is one
> line long.

Then two buttons: `★ Star on GitHub` (accent fill, as in hero) and `Read the docs →`
(transparent, `1px solid var(--line)`, `var(--ink)` text, border → accent on hover).

#### 7. Footer

Inside the CTA band, separated by `border-top: 1px solid var(--line)`,
`padding-top: 14px`, mono 12.5px `var(--ink3)`. Contains a single line: `#+END`.

An earlier draft included two `#` comment lines here explaining the name ("a group of
trees", "the first three letters, backwards"). **The client removed them.** Do not
reinstate. The name is not explained anywhere on the page.

---

## Interactions & Behavior

### 1. Headline folding

The five feature rows fold and unfold on click, mimicking org-mode's `TAB`.

- State: one boolean per row. **All five start open** (the prototype default). Note this
  contradicts the hero's `#+STARTUP: overview` line, which in real org means *everything
  folded* — a deliberate choice so first-time visitors see content rather than five
  collapsed lines. If you'd rather honor the metaphor, that is a design decision to raise,
  not to make silently.
- Clicking the headline row toggles that row only.
- The arrow glyph swaps `▾` ⇄ `▸`.
- The prototype has **no transition** — content appears and disappears instantly, which
  is what Emacs does. If you add animation, keep it under 150ms.

**Keyboard support was requested but is not in the prototype. Please implement it:**

- `TAB` on a focused headline toggles that headline.
- `Shift+TAB` cycles global fold state (all open → all closed).
- Headline rows must be real focusable controls — use `<button>` elements with
  `aria-expanded` and `aria-controls`, not clickable `<div>`s as the prototype has.
  This also gets you screen-reader support for free.

### 2. Theme toggle

Clicking the `M-x grove-…-theme` pill in the buffer bar flips light ⇄ dark.

- Implemented by setting `data-theme="dark"` on the page root; every color resolves
  through custom properties, so nothing else needs to change.
- The label always names the *destination* theme.
- Default is **light**.
- **Not in the prototype, needed in production:** persist the choice to `localStorage`,
  and on first visit respect `prefers-color-scheme` while still treating light as the
  fallback. Set the attribute in a blocking inline script in `<head>` to avoid a
  flash of the wrong theme.

### 3. Email capture

Currently non-functional markup. Wire to whatever list provider is chosen. Needs: email
validation, a submitting state, a success state that replaces the field, and an error
state. Keep the shell-prompt `>` styling in all states.

### 4. Responsive behavior (to be implemented — prototype is desktop-only)

Recommended, in the spirit of the design:

- **Below ~1000px:** hero collapses to one column, the source/rendered pair moving below
  the text and sitting side by side. Feature bodies drop to prose-over-screenshots, with
  screenshots in a horizontal scroller.
- **Below ~700px:** the three-column filetags band stacks; `border-right` becomes
  `border-bottom`. Reduce section padding `56px → 24px`. Reduce hero title `52px → 36px`.
  Feature body indent `40px → 16px` (keep some indent — it is the org metaphor).
- **Buffer bar:** on narrow screens keep `grove.org` and the theme toggle; collapse the
  nav links into a menu. Drop `— 1 of 1 buffers` first, it is pure flavor.
- Never let the fake org buffer in the hero wrap — it is preformatted text and wrapping
  destroys the illusion. Allow horizontal scroll or scale it down instead.

---

## State Management

Trivial; no data fetching on the landing page.

| State | Type | Initial | Trigger |
|---|---|---|---|
| `theme` | `'light' \| 'dark'` | `'light'` (then localStorage, then OS pref) | Toggle click |
| `open.cap` … `open.rd` | `boolean` ×5 | all `true` | Headline click / `TAB` |
| `email` | `string` | `''` | Input |
| `submitState` | `'idle' \| 'sending' \| 'ok' \| 'error'` | `'idle'` | Form submit |

In Astro this is small enough for a single inline `<script>` with no framework — folding is
a `classList` toggle plus an `aria-expanded` flip, and theming is one attribute on
`<html>`.

---

## Assets

Ten PNG screenshots in `reference/assets/`, all captured from the Grove Android app:

`agenda-overdue.png`, `capture-editor-datetree.png`, `capture-picker.png`, `edit-mode.png`,
`notebooks-list.png`, `outline-view.png`, `read-mode.png`, `search-filters.png`,
`search-results.png`

**Important — all screenshots are of the app's DARK theme.** In light mode the page
therefore shows dark phone screens. The design compensates by always mounting screenshots
on explicit dark cards (`#12100e`), so they read as devices rather than as broken light-mode
images. This works, but **light-theme captures of at least the hero `read-mode.png` shot
would materially improve light mode.** Flagged with the client; not yet provided. If they
arrive, swap per theme with `<picture>` or two `<img>`s toggled by the theme attribute.

Screenshots are portrait phone captures. Displayed at `300px` wide in feature rows and
`420px` in the hero column, always `height: auto`.

No logo or icon exists yet beyond the `✳` glyph (a text asterisk-operator character,
U+2733, in `var(--green)`) used as the wordmark. Treat it as a placeholder.

No SVG illustrations are used anywhere, by design.

---

## Documentation Section

**Not yet designed.** The client has 22 `.mdx` feature docs ready to import
(`agenda.mdx`, `capture.mdx`, `themes-appearance.mdx`, and so on), each with `title` and
`description` frontmatter. Requirements gathered but not yet visualized:

- Collapsible sections with keyboard shortcuts (same org-fold behavior as the landing page)
- Sticky left sidebar rendered as an **org outline tree**
- Right-hand table of contents
- Client-side search across docs
- Prev/next links in the page footer

One idea worth carrying over from the landing page: render each doc's frontmatter as a real
org `:PROPERTIES:` drawer at the top of the page, using `--ink4` for the drawer lines.

Note: the docs mention a **Widget** feature that is **not implemented yet** — exclude it
from navigation until it ships.

---

## Files

```
design_handoff_grove_launch_site/
├── README.md                          ← this file
└── reference/
    ├── Grove Landing 1a.dc.html       ← the design reference; open in a browser
    ├── support.js                     ← prototyping runtime; NOT for production
    └── assets/*.png                   ← 10 app screenshots
```

The full three-variant exploration (including the two directions not chosen — an
"Emacs frame" treatment with a modeline, and a restrained editorial treatment) lives in
`Grove Site.dc.html` in the design project, if you want to see what was rejected and why.

## Suggested Build Order

1. Astro project, fonts, the two token blocks, and the theme toggle with no-flash script.
2. Static landing page top to bottom, desktop width only.
3. Folding, as progressive enhancement — the page must be fully readable with JS disabled
   (render all sections open, since the content is the product pitch).
4. Responsive passes.
5. Docs section, once designed.
