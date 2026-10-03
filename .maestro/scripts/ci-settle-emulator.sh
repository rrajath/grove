#!/usr/bin/env bash
# Run before the Maestro flows on a freshly booted CI emulator.
#
# android-emulator-runner returns once sys.boot_completed is 1, but a cold
# emulator on a shared runner is still busy for a while after that: the
# launcher may not have drawn yet, package dexopt is still running, and a
# "System UI isn't responding" / "Launcher has stopped" dialog can pop up and
# swallow the first flow's taps. This waits for the launcher to hold focus,
# lets the device go quiet, then dismisses any system dialog left on screen.
set -uo pipefail

boot_deadline=$((SECONDS + 180))
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
  [ $SECONDS -ge $boot_deadline ] && { echo "settle: boot never completed" >&2; exit 1; }
  sleep 2
done

# Unlock and go home, then wait for a launcher window to have focus.
adb shell input keyevent 82 >/dev/null 2>&1
adb shell input keyevent 3 >/dev/null 2>&1
focus_deadline=$((SECONDS + 120))
until adb shell dumpsys window windows 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | grep -qi launcher; do
  if [ $SECONDS -ge $focus_deadline ]; then
    echo "settle: launcher never took focus; continuing" >&2
    break
  fi
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1
  adb shell input keyevent 3 >/dev/null 2>&1
  sleep 3
done

# Give post-boot background work (dexopt, media scan) a moment to quiet down.
sleep 15
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1
adb shell input keyevent 3 >/dev/null 2>&1
echo "settle: emulator ready"
