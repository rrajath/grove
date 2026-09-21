#!/usr/bin/env bash
# Ignore-list settings (Settings § Notebooks) — 4 scenarios covering the
# .orgzlyignore one-shot import and Settings § Notebooks' "Ignore list" row.
#
# Why a script instead of plain Maestro flows: the app's .orgzlyignore import
# (GroveApplication.kt) is a one-shot check that runs on the very first
# process start after install, before any UI interaction is possible. To
# control what it sees, the file has to exist in the vault *before* that
# first launch — and Maestro's YAML has no filesystem-injection primitive
# (see .maestro/README.md § Flow 06, which hits the same wall for a
# different reason). So each scenario here: `pm clear` for a truly fresh
# install, `adb shell` writes the vault directory's contents directly, then
# a `maestro test` run launches the app (via subflows/launch-direct-vault.yaml,
# which skips the bundled .org fixture seed so the adb-pushed files survive)
# and drives the actual UI assertions.
#
# Requires exactly one connected device/emulator (use an AVD — never the
# physical device with the real vault) and the debug APK already installed:
#   ./gradlew :app:installDebug
#   .maestro/scripts/ignore-list-settings.sh
set -euo pipefail

PKG=com.rrajath.grove.debug
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VAULT="/storage/emulated/0/Android/data/$PKG/files/testvault"

pass=0
fail=0

run_maestro() {
  # run_maestro <label> <flow-path>
  local label="$1" flow="$2"
  if maestro test "$flow" >/tmp/maestro-ignore-list.log 2>&1; then
    echo "PASS: $label"
    pass=$((pass + 1))
  else
    echo "FAIL: $label"
    cat /tmp/maestro-ignore-list.log
    fail=$((fail + 1))
  fi
}

fresh_vault() {
  adb shell pm clear "$PKG" >/dev/null
  adb shell "mkdir -p '$VAULT'"
}

echo "== Ignore-list settings =="

# --- Scenario 1: no .orgzlyignore file ---
fresh_vault
run_maestro "scenario 1: no .orgzlyignore file" "$ROOT/subflows/ignore-list-scenario-1-no-file.yaml"

# --- Scenario 2: empty .orgzlyignore file ---
fresh_vault
adb shell "printf '' > '$VAULT/.orgzlyignore'"
run_maestro "scenario 2: empty .orgzlyignore file" "$ROOT/subflows/ignore-list-scenario-2-empty-file.yaml"

# --- Scenario 3: non-empty .orgzlyignore with patterns + comments ---
fresh_vault
adb shell "cat > '$VAULT/.orgzlyignore'" <<'EOF'
# Legacy Orgzly ignore file
archive/
*.bak
old-notes.org
# trailing comment
EOF
run_maestro "scenario 3: imported .orgzlyignore, deduped, comments dropped" "$ROOT/subflows/ignore-list-scenario-3-imported.yaml"

# --- Scenario 4: import, then add a pattern by hand and confirm it applies ---
fresh_vault
adb shell "cat > '$VAULT/.orgzlyignore'" <<'EOF'
# Legacy Orgzly ignore file
archive/
*.bak
old-notes.org
# trailing comment
EOF
adb shell "cat > '$VAULT/keep.org'" <<'EOF'
#+TITLE: Keep Me

* Heading
  Body.
EOF
adb shell "cat > '$VAULT/hide-me.org'" <<'EOF'
#+TITLE: Hide Me

* Heading
  Body.
EOF
run_maestro "scenario 4: add a pattern by hand, confirm it takes effect" "$ROOT/subflows/ignore-list-scenario-4-add-pattern.yaml"

echo
echo "== $pass passed, $fail failed =="
[ "$fail" -eq 0 ]
