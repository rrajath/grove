# Architecture

See `docs/plan-grove-launch-site.md` for the original implementation plan and the trade-offs considered before building. This document describes what was actually built and why, for future maintainers.

## Stack

Astro 7 with the `@astrojs/starlight` integration. No UI framework (React/Vue/etc.) — every interactive piece (folding, theme toggle) is a small vanilla `<script>` module, which is all Astro's islands architecture needs for this scope.

## Routing split

Starlight only claims routes that exist inside its content collection (`src/content/docs/`) — it does not reserve `/` unless a file is placed there. This site uses that to run two independent sections side by side:

- **`/`** — `src/pages/index.astro`, a fully custom Astro page entirely outside Starlight's collection. Hand-built to match `design/reference/Grove Landing 1a.dc.html`.
- **`/features/...`** — the docs section, built on Starlight. Content lives at `src/content/docs/features/*.mdx`, nested one level under `features/` specifically so the resulting URLs (`/features/<slug>`) match every internal cross-link inside those `.mdx` files verbatim. This was a deliberate trade-off: URL convention (`/docs/`) was sacrificed for link fidelity (zero rewriting across the corpus, zero risk of a missed link). `src/content/docs/features/index.mdx` is the docs landing page at `/features/`.

  The feature docs began as a copy of a hand-authored `.mdx` set that lived in `docs-site/resources/`. That staging folder has since been deleted: the two copies had diverged (the built set gained `<ThemeShot>` components, light/dark screenshot pairs, and copy edits), only `src/content/docs/` is ever built, and keeping a second copy in sync bought nothing. `src/content/docs/features/` is now the sole source — edit it directly.

## Design tokens — one source of truth

`src/styles/tokens.css` holds the `:root` (light) and `[data-theme="dark"]` custom-property blocks, copied verbatim from `design/README.md` and cross-checked against the reference HTML's own inline `<style>` block. This file is:

- imported directly by `src/layouts/LandingLayout.astro` for the landing page, and
- passed into Starlight via `customCss` in `astro.config.mjs` for the docs section.

`src/styles/starlight-overrides.css` (also in `customCss`) remaps Starlight's own `--sl-color-*`, `--sl-font`, and `--sl-font-mono` custom properties onto Grove's tokens (verified against Starlight's `packages/starlight/style/props.css`, not guessed). Because those tokens are themselves theme-reactive, one mapping block is enough — light/dark follows automatically.

## Shared theme state

Both sections read/write the same `localStorage` key (`grove-theme`) and the same `data-theme` attribute on `<html>`, via:

- `src/scripts/theme-boot.js` — a blocking inline script (precedence: localStorage → OS `prefers-color-scheme` → light), inlined into `<head>` on both the landing page (`LandingLayout.astro`) and the docs section (`src/components/starlight-overrides/ThemeProvider.astro`, a **full replacement** of Starlight's default `ThemeProvider`, not an extension of it).
- `src/scripts/theme-toggle.js` — `getGroveTheme`/`setGroveTheme`/`toggleGroveTheme`, plus `wireThemeTogglePills()` which wires up every `[data-grove-theme-toggle]` button on the page. Used by both `BufferBar.astro` (landing) and `src/components/starlight-overrides/ThemeSelect.astro` (docs — also a full replacement of Starlight's default 3-way select, since Grove's toggle is a 2-state pill, not a dark/light/auto picker).

**Known gap:** Starlight's own `Page.astro` (its top-level page orchestrator, not exposed as an overridable component) hardcodes `data-theme="dark"` as its server-rendered fallback, independent of the `ThemeProvider` override. The shared boot script corrects this client-side before paint, so it's invisible to any JS-enabled visitor. A visitor with JavaScript fully disabled will see the docs section render in dark mode initially — inconsistent with the landing page's light default in that same scenario. Fixing this would require overriding Starlight's entire `Page` component, which Starlight's own docs describe as high-complexity and "a last resort." Left as a disclosed, low-impact gap.

## Docs reskin (Starlight component overrides)

All three overrides are registered in `astro.config.mjs`'s `components` option and verified against Starlight's public [overrides reference](https://starlight.astro.build/reference/overrides/) — not assumed:

- **`PageTitle`** — wraps Starlight's built-in `PageTitle` (reusing it via `import Default from '@astrojs/starlight/components/PageTitle.astro'`, so the `<h1>` keeps its default id/styling) and appends a rendered `:PROPERTIES:` drawer showing the page's `description` frontmatter in `--ink4`, per the design handoff's suggestion to carry the org-drawer idea into the docs.
- **`ThemeProvider`** / **`ThemeSelect`** — see "Shared theme state" above.

`src/styles/starlight-overrides.css` also applies scoped typography to `.sl-markdown-content` (Source Serif 4 body copy, JetBrains Mono for headings/code) so docs prose reads in the landing page's own type scale rather than Starlight's defaults.

## Content sourcing

`src/content.config.ts` defines the `docs` collection using Starlight's own `docsLoader()`/`docsSchema()` — no custom frontmatter fields, since every source file uses only `title`/`description`.

Images are colocated at `src/content/docs/assets/` — a sibling of `src/content/docs/features/`. This means every `../assets/*.png` reference inside the `.mdx` files resolves unchanged; Astro's content-collection Markdown pipeline auto-optimizes these relative image references without any frontmatter `image()` schema needed (confirmed in the built output: each renders as a responsive `.webp` with explicit `width`/`height`). Light/dark screenshot pairs live under `assets/light/` and `assets/dark/` and are mounted through the shared `<ThemeShot>` component.

Landing-page screenshots are a separate, smaller set (`src/assets/landing/`), imported explicitly and rendered via `astro:assets`' `<Image>` component in `.astro` files — a different mechanism from the docs' auto-optimized Markdown images, appropriate to where each is used.

## The Widget page

The design handoff explicitly excludes the not-yet-shipped Widget feature from navigation. This is implemented as: the file `src/content/docs/features/widget.mdx` exists and is fully reachable at `/features/widget`, but it has no entry in `astro.config.mjs`'s hand-written `sidebar` array and no link from the docs index page (`features/index.mdx`). Other pages' existing inline cross-references to it (e.g. `capture.mdx`'s "Entry points" list) were left untouched and continue to resolve correctly.

## Landing page component structure

One component per band in `src/components/landing/`: `BufferBar`, `Hero`, `FiletagsBand`, `FeaturesHeading`, `FeatureRow` (reused 5×, one per feature), `CtaBand`. `FeatureRow` owns the shared interactive/structural markup (the `<button>` headline, `aria-expanded`/`aria-controls`, the fold/keyboard `<script>`); each row's actual prose, bullets, and screenshots are passed in via named slots (`prose`, `bullets`, `shots`) from `src/pages/index.astro`, since that content is unique per row and abstracting it further would have hidden more than it clarified.
