# Grove launch site — Astro + Starlight

## Context

Grove needs a marketing landing page plus a documentation section, built with Astro + Starlight. Two source folders already exist and are treated as ground truth:

- `design/` — a high-fidelity handoff for the landing page ("The Buffer", variant 1a), including a working reference HTML file with exact inline CSS and copy, plus a README with design tokens, type scale, spacing, and interaction specs. The docs section has **no visual design yet**, only a requirements list.
- `resources/` — the actual doc content: `index.mdx` (nav/index) + 22 feature `.mdx` files + 16 screenshots, ready to import as-is.

The repo is currently empty (no package.json, not a git repo yet). This plan covers the full build: scaffold, landing page, docs section, shared theming, and the standing documentation your global CLAUDE.md requires (PROGRESS.md, README.md, docs/ARCHITECTURE.md, docs/DESIGN_SYSTEM.md offered at the end).

**Decisions already confirmed with you:**
- Feature-list rows start all open (matches the prototype and keeps the page readable with JS off).
- Docs get a full Grove reskin (fonts, color tokens, org `:PROPERTIES:` drawer for frontmatter), built on top of Starlight's own sidebar/TOC/search/prev-next rather than replacing them.
- Theme (light/dark) state is shared across landing + docs via one localStorage key.
- The hero email-capture field is **removed entirely** — no stub, no provider wiring.

**Assumptions I'm making that are easy to change and not worth a separate question:**
- Package manager: npm (swap to pnpm/yarn/bun trivially if you prefer).
- "★ Star on GitHub" links use a placeholder `href` (e.g. `https://github.com/rrajath/grove`) until you give me the real repo.

## Architecture

**Routing split.** Starlight only claims routes that exist inside its content collection — it does not reserve `/` unless a file is placed there. So:
- `/` is a fully custom Astro page (`src/pages/index.astro`), outside the docs collection entirely — hand-built to match the reference HTML pixel-for-pixel.
- Docs content lives at `src/content/docs/features/*.mdx`. I'm nesting it one level under `features/` (not directly in `content/docs/`) specifically so the resulting URLs are `/features/<slug>` — **the exact path every internal link inside the 23 source `.mdx` files already uses** (e.g. `resources/index.mdx` links to `/features/capture`). This means zero link-rewriting across the corpus and no risk of breaking cross-references. `src/content/docs/features/index.mdx` (from `resources/index.mdx`) becomes the docs landing page at `/features/`.
  - Trade-off worth flagging: the docs section's URL prefix is `/features/` rather than the more conventional `/docs/`. I'm choosing link-fidelity over URL convention since it's a straight copy from your source of truth; a `/docs` alias/redirect can be added later in five minutes if you'd rather have that.
- The landing page's "Read the docs →" button and buffer-bar "docs" nav link point at `/features/`.

**Shared design tokens.** `src/styles/tokens.css` holds the `:root` and `[data-theme="dark"]` custom-property blocks, copied verbatim from `design/README.md` (cross-checked against the reference HTML's own `<style>` block — they match exactly). This file is imported by both the custom landing layout and passed into Starlight via its `customCss` config option, so there's one source of truth for every color.

**Fonts.** One Google Fonts stylesheet link (Source Serif 4, Public Sans, JetBrains Mono) added to both the landing page's `<head>` and Starlight's `head` config option.

**Shared theme toggle.** One localStorage key and one blocking inline no-flash script (default light → localStorage → `prefers-color-scheme`, per the README's spec), used in two places:
- the landing page's own `<head>`.
- a Starlight `ThemeProvider` component override (a real, documented Starlight override point) running the identical script/key, paired with a `ThemeSelect` override styled as the `M-x grove-<label>-theme` pill instead of Starlight's default toggle icon — same interaction, same state, both sections of the site.

**Docs reskin (Starlight component overrides — verified against Starlight's own overrides reference, not guessed):**
- `PageTitle` override: wraps Starlight's built-in `PageTitle` component and adds a rendered `:PROPERTIES:` drawer below it showing the page's `title`/`description` frontmatter in `--ink4`, matching the org-drawer idea the design README explicitly suggests carrying over.
- Global CSS (via `customCss`) remaps Starlight's own `--sl-color-*`, font, and radius variables onto Grove's tokens, so the built-in sidebar / table of contents / search / prev-next controls read as Grove rather than generic Starlight.
- `MarkdownContent` gets scoped CSS (not a full override) for prose typography (Source Serif 4 body copy, JetBrains Mono for code) to match the landing page's type scale.

**Sidebar.** Hand-written in `astro.config.mjs` (not autogenerated), mirroring the exact category grouping already in `resources/index.mdx` (Getting started / Notes: reading and editing / Capture / Finding things / Sync and conflicts / Reminders / Settings). The Widget page is omitted from this sidebar array **and** from the copied docs-index page's own link list, per the design README's explicit instruction to exclude it from navigation until it ships — but the file itself (`features/widget.mdx`) still exists and is directly reachable, so the existing inline reference to it from `capture.mdx`'s "Entry points" list keeps working.

**Assets.** Screenshots referenced by the docs (`../assets/*.png` in the mdx, 16 files total) copied into `src/assets/features/`, referenced through Astro's built-in image optimization (the content collection's `image()` schema helper). Landing-page screenshots (9 files, all present in `design/reference/assets/`) copied into `src/assets/landing/`.

**Landing page build.** Hand-built `.astro` components per band — buffer bar, hero (source/rendered split), filetags band, features heading, five-row feature list, CTA band/footer — translating the reference HTML's inline styles into scoped `<style>` blocks using the shared tokens. Copy, colors, spacing, and markup structure are taken directly from the reference HTML I've already read in full (`design/reference/Grove Landing 1a.dc.html`), not re-derived from the README summary alone.

**Interactions.**
- Folding: real `<button>` elements (not clickable `<div>`s) with `aria-expanded`/`aria-controls`, vanilla `<script>` toggling `classList` + swapping the `▾`/`▸` arrow glyph, `TAB` toggles the focused headline, `Shift+TAB` cycles global fold state. All five rows start open. Sections render open in the HTML regardless of JS, so the page stays fully readable with JS disabled (progressive enhancement, per the README's build order).
- Theme toggle: the shared script described above.
- No email capture (removed per your instruction) — the hero CTA row is just the "★ Star on GitHub" button plus the "`# pre-1.0 · APKs land on GitHub Releases · F-Droid when it's ready`" note line.

**Responsive CSS.** Implements the breakpoints prescribed in the README ("Responsive behavior" section) at ~1000px and ~700px, since the reference is explicitly desktop-only and responsive behavior was left as the builder's job.

## Files

- `astro.config.mjs` — integrations (`@astrojs/starlight`), sidebar, `components` overrides, `customCss`, `head`.
- `src/content.config.ts` — docs collection using Starlight's `docsSchema()`.
- `src/content/docs/features/*.mdx` — verbatim copies of `resources/features/*.mdx` (22 files) + `index.mdx` (from `resources/index.mdx`, with the Widget line removed from the Capture list).
- `src/assets/features/*.png`, `src/assets/landing/*.png` — copied screenshots.
- `src/styles/tokens.css`, `src/styles/starlight-overrides.css`, `src/styles/global.css`.
- `src/components/landing/{BufferBar,Hero,FiletagsBand,FeaturesHeading,FeatureRow,CtaBand}.astro`.
- `src/components/starlight-overrides/{PageTitle,ThemeProvider,ThemeSelect}.astro`.
- `src/layouts/LandingLayout.astro` — no-flash boot script, font link, tokens import; used only by `src/pages/index.astro`.
- `src/pages/index.astro` — assembles the landing components.
- `.gitignore` — `node_modules`, `dist`, `.astro`, `PROGRESS.md` (per your global instructions).
- `PROGRESS.md` at project root.
- `README.md`, `docs/ARCHITECTURE.md` — written once the build is functional.
- `docs/DESIGN_SYSTEM.md` — offered after milestones are complete, not written up front.

## Build order

1. Scaffold Astro + Starlight; wire fonts, tokens, and the shared no-flash theme script.
2. Static landing page top-to-bottom, desktop width only — verified against the reference HTML.
3. Folding + keyboard support as progressive enhancement.
4. Responsive passes (1000px / 700px breakpoints).
5. Docs section: content collection, hand-written sidebar, `PageTitle` drawer override, Starlight token remap, Widget exclusion.
6. Sweep every internal `/features/...` link across all 23 mdx files to confirm nothing 404s.
7. `git init` + `jj git init`, then commit via `jj describe` per your global git/jj workflow, once the build is verified.
8. Generate `README.md` and `docs/ARCHITECTURE.md`; offer `docs/DESIGN_SYSTEM.md`.

## Verification

- `npm run dev`, then use the Chrome browser tools to:
  - Compare the built landing page against `design/reference/Grove Landing 1a.dc.html` opened side-by-side, in both light and dark.
  - Click through all 5 feature folds; test `TAB` / `Shift+TAB` on a focused headline.
  - Toggle theme on the landing page, navigate into a docs page, and confirm it's already in the same theme (and vice versa).
  - Open `/features/`, click every link, confirm none 404; confirm Widget is unlisted in nav but still reachable directly at `/features/widget`.
  - Resize below 1000px and below 700px and check the responsive rules described above.
- `npm run build` to catch any Astro/content-collection schema errors before considering the build done.
