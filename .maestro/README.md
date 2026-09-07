# Maestro end-to-end flows (test suite Layer 3 / M5)

Design: `internal/test-suite-03-e2e-maestro.md`. Status: **passing** — all
flows verified against a Pixel_9a AVD (API 35, AOSP keyboard) on 2026-09-07.

## What runs

| Flow | Journey |
|---|---|
| `flows/02-capture-to-confirmation.yaml` | Capture a note via the "Quick Note" template, confirm it lands in Search |
| `flows/03-search-open-note.yaml` | Search `photosynthesis`, open the result in Read mode |
| `flows/04-edit-note-save.yaml` | Open a note, edit it, reopen, confirm the edit persisted |
| `flows/05-follow-links.yaml` | Follow every org link form from Read mode (27 cases, ~2.5 min) — see below |

Journey 01 (onboarding + the SAF system folder picker) is **deferred** — see the
design doc. Flows 02–05 skip onboarding entirely via the debug hook.

### Flow 05: follow-links

Opens the "Link Hub" note (`links-hub.org`, anchor word `linkhub`) whose body
holds one of every org link form, taps each, and asserts the landing. The
resolver (`org/OrgLinkParser` + `DocumentViewModel.resolveOrgLink`) routes every
form to one of four outcomes, one parametrised subflow each:

| Subflow | Outcome | Asserts |
|---|---|---|
| `subflows/follow-link-to-read.yaml` | Read mode of a heading | a `REACHED-*` body marker, then `back` |
| `subflows/follow-link-to-outline.yaml` | a file's Outline | `outline_file_label` text, then `back` |
| `subflows/follow-link-to-external.yaml` | OS hand-off (`https:`) | Read screen gone + URL in the browser, then `back` |
| `subflows/follow-link-to-toast.yaml` | `grove_toast` (`mailto:` with no handler, or unresolved) | toast regex, no navigation |

Covered: `*Heading` / bare-fuzzy / `#custom-id` / `id:` heading (same file);
`id:` **file-level** and `file:this.org` (→ own outline); `id:` heading, and
`file:`/`./`/bare `other.org` with `::*Heading` / `::#custom-id` (other file);
`id:` file-level and three path spellings of a whole other file; `::*Missing` /
`::#missing` (file resolves, heading gone → outline fallback); `https:` labelled
/ bare / in prose, `mailto:`; and five unresolved forms (`id:`, `#`, `*`,
`file:`, cross-file fuzzy).

## The debug test-vault hook

Flows launch the **debug** build (`com.rrajath.grove.debug`) with two intent
extras:

```
--ez grove_test_direct_vault true    # vault = <app external files>/testvault, no SAF picker
--ez grove_test_seed true            # (re)write the .org fixtures into it on launch
```

`DebugTestVault` (`app/src/main/java/com/rrajath/grove/debug/`) reads them in
`MainActivity.onCreate`, points `GroveApplication.fileStore` at a `JvmFileStore`
via `TestVaultHook`, and marks onboarding skipped. Everything is a no-op unless
`BuildConfig.DEBUG`; the release APK carries none of it and none of the fixture
assets.

### Fixtures — single source of truth

The `.org` bodies live in `app/src/testFixtures/resources/fixtures/`. They are:

- read by `OrgFixtures` on the JVM/UI test classpath, and
- copied into the debug APK's `assets/fixtures/` by the `copyDebugTestFixtures`
  Gradle task, which is what the hook seeds from.

Edit the files in `src/testFixtures/resources/fixtures/` and both layers follow.

## Running locally

```bash
curl -Ls "https://get.maestro.mobile.dev" | bash   # first time; adds ~/.maestro/bin

./gradlew :app:installDebug
maestro test .maestro/flows                          # all
maestro test .maestro/flows/03-search-open-note.yaml # one
maestro test --include-tags e2e .maestro
```

Use an emulator, not the physical device that carries the real vault. Create
the AVD from a plain **AOSP** system image (`system-images;android-34;default;…`),
not a Google APIs / Play Store image. The Play images ship Gboard, whose
first-run "glide typing" popup and floating toolbar break input and back
navigation; the AOSP image (`com.android.inputmethod.latin`) has neither, and
matches what CI runs (`reactivecircus/android-emulator-runner` with no
`target:` defaults to `default`).

The flows call `hideKeyboard` after each `inputText` that is followed by a tap
or a `back`, so a stray IME can't hide the next target or absorb the first
`back`. Keep that pattern when adding flows.

## CI

`e2e-maestro` job in `.github/workflows/build.yml` — manual "Run workflow" only
(`workflow_dispatch`). Boots an API 34 emulator, installs the debug APK, runs
`maestro test .maestro/flows`. A Maestro Cloud variant is noted in a comment
there.

## Known fragile points (verify first)

- **Capture template pick** (flow 02): assumes >1 template so the picker sheet
  shows and "Quick Note" is tappable; the tap is marked `optional` for a
  single-template vault.
- **Index latency** (flows 02–04): Search waits use `extendedWaitUntil` with 15 s
  timeouts to absorb the reindex after a file write. May need tuning. (`assertVisible`
  itself has no `timeout` property — only `extendedWaitUntil` does.)
- **Re-open after edit** (flow 04): `edit_note_save` keeps you in the editor, so
  the flow does a single `back` to the Search screen (query + result still
  there), `hideKeyboard` (that screen re-focuses the field), then taps the
  result row again to re-open the note in Read mode. Deliberately not a fixed
  `back` chain to Notebooks — a second `back` on the Search screen clears the
  query instead of leaving, so the count was unstable.
- **Link taps** (flow 05): each link is its own list item so its rendered text
  is the whole node — Maestro's `text:` selector is a full-match regex, so a
  link that shares a line with other prose won't match. Keep one link per line
  in `links-hub.org`. External returns use a single `back` (not `launchApp`,
  which starts MainActivity fresh and drops the Read-mode back stack). The
  `scrollUntilVisible` steps run at the default speed on purpose — `speed: 100`
  overshoots and intermittently misses a mid-list link.
- **Verified** against a Pixel_9a AVD (API 35, AOSP keyboard) on 2026-09-07:
  all flows pass. Re-check selectors if the Search, editor, or Read screens
  change.
