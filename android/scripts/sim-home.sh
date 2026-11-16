#!/usr/bin/env bash
# Put Arcis Home on the simulated screen (first launch must come from the shell).
export MSYS_NO_PATHCONV=1
id=$(adb shell dumpsys display | grep -oE 'displayId [0-9]+", displayGroupId [0-9]+, FLAG_PRESENTATION' | grep -oE '^displayId [0-9]+' | awk '{print $2}' | tail -1)
echo "simulated display: $id"
adb shell am start --display "$id" -n app.arcis/.glasses.GlassesHomeActivity -f 0x10000000
