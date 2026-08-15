# Grove Design System

Generated from the implemented code (`src/styles/`, `src/components/`) after the initial site build. This is the reference for any future task that adds, modifies, or styles a UI element — don't invent colors, spacing, or components outside what's documented here; extend this file when the design changes.

## Design tokens

Defined once in `src/styles/tokens.css` as CSS custom properties on `:root`, with a `[data-theme="dark"]` override block. Both the landing page and the Starlight docs (via `customCss`) read from this single file.

### Color

| Token | Light | Dark | Used for |
|---|---|---|---|
| `--bg` | `#faf6ee` | `#12100e` | Page background |
| `--bg2` | `#f3ecdf` | `#1c1915` | Raised panels, code blocks, footer/CTA band |
| `--bg3` | `#ebe2d1` | `#262119` | Inset chips, input wells, theme-toggle pill fill |
| `--ink` | `#2b2620` | `#f0ebe1` | Primary text, headings |
| `--ink2` | `#655b4e` | `#b0a89b` | Body text, secondary |
| `--ink3` | `#756a5b` | `#8d8376` | Muted text, tag labels, comment lines |
| `--ink4` | `#9a8d7b` | `#6a6157` | Decorative only — org drawer lines (`:PROPERTIES:`), never load-bearing text |
| `--line` | `#e2d8c5` | `#302a22` | All borders and rules |
| `--accent` | `#8f5c1c` | `#dfae70` | Links, primary buttons, `TODO` keyword, org keywords |
| `--accent-soft` | `#f2e0c2` | `#3a2f1b` | Accent chip backgrounds |
| `--green` | `#55752f` | `#94c06e` | Org headline stars `*`, `:tags:`, list bullets |
| `--red` | `#b04f34` | `#de8a6b` | `[#A]` priority, `DEADLINE` |
| `--blue` | `#3c6a8c` | `#74a5c6` | `SCHEDULED:` and timestamps |
| `--violet` | `#7a5a95` | `#a98bc4` | `%^{prompt}` template placeholders |
| `--shadow` | `0 1px 2px rgba(60,45,25,.05), 0 12px 32px -12px rgba(60,45,25,.16)` | `0 1px 2px rgba(0,0,0,.4), 0 16px 40px -14px rgba(0,0,0,.6)` | Screenshot cards |

**Rule:** `--ink4` is decorative-only (WCAG AA is not guaranteed) — never use it for text the reader needs. Every other `--ink*`/`--accent`/`--green` value clears AA (4.5:1) against `--bg`/`--bg2`; don't lighten them.

Every screenshot is now a light/dark capture **pair** (`resources/screenshots/{light,dark}/`), mounted via the shared `<ThemeShot>` component (`src/components/ThemeShot.astro`) and shown/hidden with a pure-CSS `[data-theme='dark']` toggle — no JS. The card itself uses theme tokens (`--bg2` background, `--line` 1px border, `--shadow`, `10px` radius / `8px` padding / `5px` inner image radius) rather than a fixed color, so a light capture never blends into a light page and a dark capture never blends into a dark one. This reconciles what used to be two divergent treatments (`.shot-card` for feature rows vs. a separate hardcoded-dark `.hero__shot-card` for the hero) into the one shared component/class, used identically everywhere. `ThemeShot` styles itself in its own scoped `<style>` block (not `global.css` or `starlight-overrides.css`) since it's shared between the landing page and the Starlight docs, which load different, non-overlapping stylesheets. A `dark` prop is optional, for the rare screen with no dark capture yet (currently just `onboarding`).

### Typography

Three font families, one Google Fonts request (see `head`/`customCss` config), exposed as tokens:

| Token | Family | Role |
|---|---|---|
| `--font-serif` | Source Serif 4 (400/600/700) | Display and long-form prose — the site's voice |
| `--font-sans` | Public Sans (400/500/600/700) | UI text, feature body copy, buttons |
| `--font-mono` | JetBrains Mono (400/500/700) | All org syntax, metadata, filenames, code, eyebrow labels |

Type scale as used (add new sizes here if introduced):

| Role | Family | Size | Weight | Notes |
|---|---|---|---|---|
| Hero title | mono | 52px (36px <700px) | 700 | `letter-spacing: -.02em` |
| Hero prose | serif | 22px | 400 | `max-width: 52ch` |
| Section heading (`* Features`) | mono | 26px | 700 | |
| Feature row title | mono | 21px | 500 | |
| Feature prose | serif | 19px | 400 | `.feature-prose` utility |
| Org source / metadata | mono | 15px | 400 | Hero keyword lines |
| Code block / org buffer | mono | 12.5px | 400 | |
| Feature bullet list | mono | 13px | 400 | `.feature-bullets` utility, `line-height: 2` |
| Button label | mono | 14px | 500 | |
| CTA prose | serif | 20px | 400 | `max-width: 60ch` |
| Docs body (`.sl-markdown-content p/li`) | serif | 17px | 400 | Starlight reskin |
| Docs headings | mono | inherit | inherit | Starlight reskin |

Apply `text-wrap: pretty` to prose blocks (already done on `.hero__prose`, `.feature-prose`, `.cta-band__prose`).

### Spacing & shape

- Section padding: `56px` horizontal, `64px` top on hero / `56px` on other bands → reduces to `24px` all around below 700px.
- Feature row headline: `22px 0` vertical padding. Body: `0 0 40px 40px` (40px left = org indent) → indent reduces to `16px` below 700px, bottom stays 40px.
- Border radius: `4px` chips/badges, `6px` buttons/inputs, `8px` panels/screenshot cards, `10px`/`5px` inner screenshot corner (outer card / inner image).
- Rules: `1px solid var(--line)` everywhere, except `.hero__shot-card`'s `1px solid #140e06` edge border (see screenshot-card note above).

### Motion

- `grove-blink` keyframe (defined once in `tokens.css`, reused by the hero's cursor `▌`): `1.1s step-end infinite`.
- Folding has no transition (instant, matching real org-mode) — if ever added, keep under 150ms per the original design intent.

## Reusable CSS utilities (`src/styles/global.css`)

Landing-page-only (not loaded into Starlight — docs use `starlight-overrides.css` instead).

- **Semantic color spans** — `.tok-ink`, `.tok-ink2`, `.tok-ink3`, `.tok-ink4`, `.tok-green`, `.tok-accent`, `.tok-accent-bold` (accent + 700 weight), `.tok-red`, `.tok-blue`, `.tok-violet`. Use these instead of inline `style="color:..."` whenever marking up org-syntax-colored text (matches the org buffer's own token coloring).
- **`.feature-prose`** — serif 19px paragraph styling for a feature row's lead copy.
- **`.feature-bullets`** — mono 13px bullet list; child `.dash` spans color the `-` marker `--green`.
- **`.shot-card`** — the dark (`#12100e`) screenshot mount, `10px` radius, `8px` padding, inner `img` gets `5px` radius.
- **`.button` / `.button--primary` / `.button--secondary`** — the two button styles used everywhere (`★ Star on GitHub`, `Read the docs →`). Primary = accent fill; secondary = bordered, border turns accent on hover.

`src/styles/theme-pill.css` — the `.grove-theme-pill` / `.grove-theme-pill__label` toggle button, shared verbatim between the landing buffer bar and the Starlight docs header override. Don't restyle one without the other; they must look identical.

## Components (`src/components/landing/`)

| Component | Purpose | Reused? |
|---|---|---|
| `BufferBar.astro` | Top strip: wordmark, nav, theme toggle. Owns the mobile nav-collapse (`☰` menu below 700px). | 1× |
| `Hero.astro` | Org keyword lines, prose, CTA, source/rendered card pair. | 1× |
| `FiletagsBand.astro` | Three-column `:plaintext:` / `:offline:` / `:yours:` band, data-driven from a small array — add a fourth tag by extending the `cells` array, not by copy-pasting markup. | 1× |
| `FeaturesHeading.astro` | `* Features` heading + TAB hint. | 1× |
| `FeatureRow.astro` | Owns the fold/keyboard interactive shell (button, `aria-expanded`/`aria-controls`, arrow glyph, fold script). Content (prose/bullets/screenshots) passed in via named slots (`prose`, `bullets`, `shots`) from `index.astro`, since each row's content is unique. | 5× |
| `CtaBand.astro` | Closing CTA + `#+END` footer. | 1× |

**Pattern:** when a piece of UI repeats with identical *structure* but unique *content* (like the five feature rows), build one component owning the structure/behavior and pass content through named slots — don't duplicate the interactive shell, and don't over-abstract content that's genuinely one-off per instance.

### Shared component (`src/components/`)

Unlike the table above, `ThemeShot.astro` lives outside `landing/` and isn't landing-only — it's imported from both the custom landing page (`.astro` files) and the Starlight docs (`.mdx` files under `src/content/docs/features/`), which is why its visual styling is self-contained in its own scoped `<style>` block rather than living in either page's separate stylesheet. Props: `light` and `dark` (both `ImageMetadata`, `dark` optional), `alt`, `widths`, `sizes` — passed straight through to two stacked `astro:assets` `<Image>`s, toggled by `[data-theme]`. See the screenshot-card note above for the full rationale.

## Starlight overrides (`src/components/starlight-overrides/`)

Full replacements (not extensions) of three Starlight components, registered in `astro.config.mjs`'s `components` option:

- **`PageTitle.astro`** — wraps Starlight's built-in title, adds the `:PROPERTIES:` drawer (`.grove-drawer`, `--ink4`, mono 13px) rendering the page's `description` frontmatter.
- **`ThemeProvider.astro`** — runs the shared boot script (`src/scripts/theme-boot.js`) instead of Starlight's own theme init, so state is shared with the landing page.
- **`ThemeSelect.astro`** — the same `.grove-theme-pill` used on the landing page, instead of Starlight's default 3-way select.

`src/styles/starlight-overrides.css` remaps Starlight's own `--sl-color-*`/`--sl-font*` variables onto Grove's tokens and applies the type scale to `.sl-markdown-content`. It also caps any plain markdown `<img>` (feature screenshots in `src/content/docs/assets/` are full device-resolution captures, 1280px+ wide, with no per-image sizing in the `.mdx`) at a fixed `340px` max-width plus the same border/radius/shadow treatment as `.shot-card`, so legacy single-theme screenshots (no light/dark pair captured yet) still read consistently with the ones mounted via `<ThemeShot>`. The rule excludes `.shot-card__img` (`:not(.shot-card__img)`) so `ThemeShot`'s own card isn't double-bordered.

## Responsive breakpoints

Two breakpoints, consistent across every component:

- **`max-width: 1000px`** — hero collapses to one column (source/rendered pair moves below the text, side by side); feature-row bodies drop to a single prose column with screenshots in a horizontal scroller (`.feature-shots` → `display:flex; overflow-x:auto`).
- **`max-width: 700px`** — section padding `56px → 24px`; hero title `52px → 36px`; feature body indent `40px → 16px`; filetags band stacks (border-right → border-bottom); buffer bar nav collapses into a `☰` dropdown menu; hero cards drop back to single column.

When adding a new section, reuse these two breakpoints rather than introducing a third — the current set covers every case identified in the design handoff.

## What's deliberately *not* in this system

- No SVG illustrations anywhere (matches the design handoff's explicit "by design" note).
- No logo/icon beyond the `✳` (U+2733) glyph — treat any future logo work as a separate design task, not something to improvise here.
- No component library / UI framework — everything is hand-authored `.astro` + scoped CSS, intentionally, to keep 1:1 fidelity with the reference HTML.
