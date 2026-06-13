# Benchmarking & Baseline Profiles

Performance is measured with the `:macrobenchmark` module (Macrobenchmark +
UiAutomator). Run benchmarks on a **physical device (API 34+)**, not an
emulator — emulator timings are noise.

## Build types

`:app` has a `benchmark` build type that mirrors `release` (R8 + resource
shrinking on) but is debug-signed, non-debuggable and profileable, so
Macrobenchmark can capture traces without root. The benchmark module targets it.

## Running the benchmarks

```bash
# Startup (no setup needed). Compares JIT vs. baseline-profile vs. full AOT.
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -P android.testInstrumentationRunnerArguments.class=com.rrajath.grove.macrobenchmark.StartupBenchmark

# Outline scroll frame timing (needs a seeded vault — see below).
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -P android.testInstrumentationRunnerArguments.class=com.rrajath.grove.macrobenchmark.ScrollBenchmark

# Everything
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

Results print to the console and are written as JSON + `.perfetto-trace` under
`macrobenchmark/build/outputs/connected_android_test_additional_output/`.

Key metrics: `timeToInitialDisplayMs` (startup), `frameDurationCpuMs` /
`frameOverrunMs` P50/P90/P99 (scroll).

If an unlocked-clocks / emulator check blocks an experimental run, add
`-P android.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR,UNLOCKED`
— but treat suppressed results as directional only.

## Seeding a vault (for ScrollBenchmark)

`ScrollBenchmark` opens the first notebook and flings the outline, so the device
needs a vault with a notebook big enough to scroll. One-time setup:

```bash
awk 'BEGIN{for(i=1;i<=500;i++){print "* TODO Heading "i" :tag"i":";
  print "  Body for note "i" with a [[https://example.com][link]] and *bold* text.";
  if(i%5==0) print "** Sub-heading "i".1"}}' > notes.org
adb shell mkdir -p /sdcard/Documents/grove-bench
adb push notes.org /sdcard/Documents/grove-bench/notes.org
```

Then in Grove: onboarding → **Choose folder** → pick `Documents/grove-bench`.
The SAF grant + vault config persist across benchmark runs.

## Before/after workflow

1. Run the relevant benchmark on the base commit; record the numbers.
2. Apply one change.
3. Re-run the *same* benchmark with the *same* device and seeded vault; compare.

## Baseline profile (manual, AGP 9)

The `androidx.baselineprofile` Gradle plugin (which automates this) does **not**
support AGP 9.0.1 yet — applying it fails with "not a supported android module".
Until it does, generate and install the profile manually:

1. Generate it on a device (API 33+):
   ```bash
   ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
     -P android.testInstrumentationRunnerArguments.class=com.rrajath.grove.macrobenchmark.BaselineProfileGenerator
   ```
2. Find the produced `*-baseline-prof.txt` in the test's additional-output dir
   (path is printed in the test output).
3. Copy it to `app/src/main/baseline-prof.txt`.

AGP's ART-profile pipeline compiles `app/src/main/baseline-prof.txt` into the
APK and `androidx.profileinstaller` (already a dependency) installs it on first
run. Verify the win with `StartupBenchmark.startupBaselineProfile` vs.
`startupCompilationNone`.

Re-check the plugin's AGP-9 support periodically; once supported it replaces the
manual steps above with `./gradlew :app:generateBaselineProfile`.
