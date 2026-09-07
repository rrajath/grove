# Maestro end-to-end flows (test suite Layer 3 / M5)

Design: `internal/test-suite-03-e2e-maestro.md`. Status: **not yet run on a
device** — the flows and the debug hook they depend on are in place; verifying
and tuning selectors against a real emulator is the next step.

## What runs

| Flow | Journey |
|---|---|
| `flows/02-capture-to-confirmation.yaml` | Capture a note via the "Quick Note" template, confirm it lands in Search |
| `flows/03-search-open-note.yaml` | Search `photosynthesis`, open the result in Read mode |
| `flows/04-edit-note-save.yaml` | Open a note, edit it, reopen, confirm the edit persisted |

Journey 01 (onboarding + the SAF system folder picker) is **deferred** — see the
design doc. Flows 02–04 skip onboarding entirely via the debug hook.

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

Use an emulator, not the physical device that carries the real vault. Pin
API 34 (AOSP) for determinism.

## CI

`e2e-maestro` job in `.github/workflows/build.yml` — manual "Run workflow" only
(`workflow_dispatch`). Boots an API 34 emulator, installs the debug APK, runs
`maestro test .maestro/flows`. A Maestro Cloud variant is noted in a comment
there.

## Known fragile points (verify first)

- **Capture template pick** (flow 02): assumes >1 template so the picker sheet
  shows and "Quick Note" is tappable; the tap is marked `optional` for a
  single-template vault.
- **Index latency** (flows 02–04): Search asserts use 15 s timeouts to absorb the
  reindex after a file write. May need tuning.
- **`back` count** (flow 04): two `back`s to leave the editor + read screen; the
  editor's discard prompt could intercept if the save didn't register.
