#!/usr/bin/env bash
# Flow 06: sync-conflict notification behavior (SyncManager.notifyConflicts /
# SyncEngine's .org-only filter, see commit "fix: scope sync-conflict
# notifications to .org files and dedupe them").
#
# Why a script instead of a plain Maestro flow: these scenarios hinge on
# *simulating* a Syncthing conflict copy landing in the vault between sync
# passes, and on inspecting the posted Notification's `when` field to prove it
# was or wasn't re-posted. Maestro's YAML has no filesystem-injection or
# NotificationManager-inspection primitive, so this script interleaves small
# Maestro flows (UI actions only) with `adb shell` vault writes and
# `adb shell dumpsys notification` assertions. See .maestro/README.md § Flow 06.
#
# Requires exactly one connected device/emulator (use an AVD — never the
# physical device with the real vault) and the debug APK already installed:
#   ./gradlew :app:installDebug
#   .maestro/scripts/sync-conflict-notifications.sh
set -euo pipefail

PKG=com.rrajath.grove.debug
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VAULT="/storage/emulated/0/Android/data/$PKG/files/testvault"

pass=0
fail=0

check() {
  local desc="$1" got="$2" want="$3"
  if [ "$got" = "$want" ]; then
    echo "PASS: $desc"
    pass=$((pass + 1))
  else
    echo "FAIL: $desc (expected [$want], got [$got])"
    fail=$((fail + 1))
  fi
}

notif_count() {
  adb shell dumpsys notification --noredact 2>/dev/null \
    | grep -c "NotificationRecord.*pkg=$PKG" || true
}

notif_text() {
  adb shell dumpsys notification --noredact 2>/dev/null \
    | grep -A 40 "pkg=$PKG" | grep "android.text=" | sed -E 's/.*android.text=String \((.*)\)/\1/' || true
}

notif_when() {
  adb shell dumpsys notification --noredact 2>/dev/null \
    | grep -A 40 "pkg=$PKG" | grep "when=" | head -1 || true
}

write_conflict() {
  # write_conflict <base-name-without-ext> <ext>
  adb shell "echo '* c' > '$VAULT/$1.sync-conflict-20260101-101010-DEVICEA.$2'"
}

run_maestro() {
  maestro test "$1" >/tmp/maestro-flow-06.log 2>&1 || {
    cat /tmp/maestro-flow-06.log
    exit 1
  }
}

echo "== Flow 06: sync-conflict notifications =="

run_maestro "$ROOT/subflows/launch-seeded.yaml"
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null

# --- Scenario 1: a non-.org conflict copy must not notify or show in-app ---
write_conflict "photo.png" "png"
run_maestro "$ROOT/subflows/trigger-sync.yaml"
check "non-.org conflict: no notification" "$(notif_count)" "0"

# --- Scenario 2: two .org conflicts land in one sync -> a single, edge-triggered notification ---
write_conflict "events" "org"
write_conflict "projects" "org"
run_maestro "$ROOT/subflows/trigger-sync.yaml"
check "two conflicts in one sync: exactly one notification" "$(notif_count)" "1"
check "notification is count-only, pluralized" "$(notif_text)" "2 sync conflicts found"

# --- Scenario 3: a 3rd conflict landing while one is already posted must NOT re-post or update it ---
before_when="$(notif_when)"
before_text="$(notif_text)"
write_conflict "inbox" "org"
run_maestro "$ROOT/subflows/trigger-sync.yaml"
check "still exactly one notification after a 3rd conflict" "$(notif_count)" "1"
check "notification text unchanged (edge-triggered, not re-posted)" "$(notif_text)" "$before_text"
check "notification 'when' unchanged (edge-triggered, not re-posted)" "$(notif_when)" "$before_when"

# --- Scenario 4: unchanged conflict set across repeated syncs must not re-post ---
stable_when="$(notif_when)"
run_maestro "$ROOT/subflows/trigger-sync.yaml"
run_maestro "$ROOT/subflows/trigger-sync.yaml"
check "notification count stays at one across repeat syncs" "$(notif_count)" "1"
check "notification 'when' unchanged (no re-post/re-alert)" "$(notif_when)" "$stable_when"

# --- Scenario 5: resolving every conflict cancels the notification ---
run_maestro "$ROOT/subflows/resolve-one-conflict.yaml"
run_maestro "$ROOT/subflows/resolve-one-conflict.yaml"
run_maestro "$ROOT/subflows/resolve-one-conflict.yaml"
check "notification cancelled once all conflicts are resolved" "$(notif_count)" "0"

# --- Scenario 6: all-clear resets the episode flag, so a fresh conflict re-notifies ---
pre_resolve_when="$stable_when"
write_conflict "notes" "org"
run_maestro "$ROOT/subflows/trigger-sync.yaml"
check "fresh conflict after all-clear: exactly one notification" "$(notif_count)" "1"
check "fresh conflict notification is singular" "$(notif_text)" "1 sync conflict found"
new_when="$(notif_when)"
if [ "$new_when" != "$pre_resolve_when" ]; then
  echo "PASS: fresh episode re-notified (when: $pre_resolve_when -> $new_when)"
  pass=$((pass + 1))
else
  echo "FAIL: fresh conflict after all-clear did not re-notify (when unchanged: $new_when)"
  fail=$((fail + 1))
fi

echo
echo "== $pass passed, $fail failed =="
[ "$fail" -eq 0 ]
