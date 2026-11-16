#!/usr/bin/env bash
# Start the Arcis debug simulated screen and accept the screen-capture consent dialog.
set -e
export MSYS_NO_PATHCONV=1
adb shell am start -n app.arcis/.debug.SimScreenActivity >/dev/null
sleep 2
for i in 1 2 3; do
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  b=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep -m1 'text="Start now"' | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/')
  if [ -n "$b" ]; then
    set -- $b
    adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
    sleep 2
    adb logcat -d -s ArcisSim:I | tail -1
    exit 0
  fi
  # Button may be off-screen in landscape: scroll the dialog.
  adb shell input swipe 1170 800 1170 300 300
  sleep 1
done
echo "Start now button not found" >&2
exit 1
