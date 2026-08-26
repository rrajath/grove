# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Primary users are existing Emacs org-mode users who want their `.org` files on Android — people who already keep notes and tasks in plain-text org syntax on desktop and are evaluating (or have adopted) Grove to read, capture, and edit the same files on their phone. The site is not written to onboard newcomers to org-mode; tone and copy assume that fluency (the brief is "playful, in on the Emacs joke").

A secondary audience is open-source contributors and early adopters landing on the repo/docs to evaluate or contribute to the project pre-release.

## Product Purpose

This repo is the public-facing launch site for **Grove**, a native Android app for notes and tasks on top of plain `.org` files. Grove itself has no web presence yet; this site gives it one: a single-page marketing landing page ("The Buffer" design direction — the site reads as an org buffer) plus a full documentation section covering every implemented feature. Success before public release means early adopters and contributors have a credible, on-brand place to land, understand the pitch, read the docs, and find the GitHub repo.

## Positioning

"Your vault is a folder of `.org` files." Grove never introduces a proprietary format — every note is a plain `.org` file on disk, and the app's local index is fully rebuildable from those files at any time. It renders org-mode properly on Android (folding, TODO/priority/tags, planning dates, drawers) without requiring a Lisp interpreter, and syncs via existing file-sync tools (e.g. Syncthing) rather than a proprietary cloud service. This plain-text, no-lock-in mechanism is the thing a bundled-format or cloud-sync competitor could not truthfully copy.

## Operating Context

- Grove (the app) is native Android; this site is a standalone web project (Astro + Starlight) that markets it and hosts its docs.
- The feature docs began as a transcription of the app team's own `.mdx` notes rather than web-original copy, and should stay close to that: describe real app behavior, don't editorialize. They now live only in `src/content/docs/features/` (the earlier `resources/` staging copy was deleted once the two diverged).
- The org-mode vocabulary (headline stars, `TODO`/priority keywords, `:tags:`, `SCHEDULED:`/`DEADLINE:`, `:PROPERTIES:` drawers, `%^{prompt}` placeholders) is literal UI language on the landing page, not decoration — it must stay syntactically accurate to real org-mode.
- Shared light/dark theme across landing page and docs, one `localStorage` key, no flash of wrong theme.
- Landing page must remain readable and functional with JavaScript disabled (progressive enhancement); folding is a JS enhancement over content that renders open by default.

## Capabilities and Constraints

- Built with Astro + Starlight (docs on Starlight with a full visual reskin; landing page is a standalone route outside the Starlight collection).
- Landing page: hero, source-vs-rendered comparison, filetags band, five foldable/keyboard-accessible feature sections, closing CTA band.
- Docs section at `/features/` (not `/docs/`), preserving internal links from the source `.mdx` verbatim.
- No waitlist or email capture exists or is planned — deliberately removed. The only calls to action are "Star on GitHub" and "Read the docs."
- Grove (the app) is **pre-release**: no Play Store listing. Distribution is APKs via GitHub Releases, with F-Droid planned once ready.
- Open source; repo is `https://github.com/rrajath/grove` (confirmed real and current).
- **Open/undecided:** production domain for this site is not yet chosen — no `site` option is set in `astro.config.mjs` yet.

## Brand Commitments

- Product name: **Grove**.
- Voice: playful, in on the Emacs joke — written for readers who already get the references. Copy is a first draft per the design handoff and may still be revised, but the insider tone itself is a confirmed brand commitment, not open for softening toward newcomers.
- Both the site's light and dark themes are derived from the Grove Android app's own themes, so the site and app read as the same product (light is the site's default even though the app itself ships dark).

## Evidence on Hand

- Real screenshots from the app itself, one per documented feature, in `src/content/docs/assets/` (docs) and `src/assets/landing/` (landing page).
- Every currently implemented app feature has a corresponding `.mdx` doc in `src/content/docs/features/`.
- No testimonials, case studies, press, pricing, or usage benchmarks exist — none should be fabricated.
- A high-fidelity design reference prototype exists at `design/reference/Grove Landing 1a.dc.html` (with `design/README.md` as its written spec) — the source of truth the current landing page was built to match pixel-for-pixel.

## Product Principles

1. Plain text over proprietary formats — every product and copy decision should reinforce that the user's data is theirs, in files they already control.
2. Faithful to org-mode syntax — the org vocabulary used as UI language must stay real and accurate, not approximated for effect.
3. Docs are transcribed, not reinvented — the docs section's job is to carry the team's existing `.mdx` content to the web faithfully, not to editorialize it.
4. Insider tone, not a broad funnel — this site is written for people who already know what org-mode is; it is not optimizing to convert org-mode newcomers.
5. Honest about pre-release status — no CTA, claim, or asset should imply availability (Play Store, polish, scale) the app doesn't yet have.

## Accessibility & Inclusion

- Keyboard-accessible folding is a functional requirement: feature sections are real `<button>`s with `aria-expanded`/`aria-controls`; `TAB` toggles a focused headline, `Shift+TAB` cycles global fold state (mirrors org-mode's own `TAB` behavior).
- Color tokens must clear WCAG AA (4.5:1) against their background — confirmed and already enforced in the current token set, with one deliberate, disclosed exception (`--ink4`, used only for decorative faded drawer lines, never for information the reader needs).
- Page must remain fully readable and navigable with JavaScript disabled.
