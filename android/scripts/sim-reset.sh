#!/usr/bin/env bash
# After (re)installing: recreate the simulated screen, put Home on it, open the controller.
export MSYS_NO_PATHCONV=1
dir=$(dirname "$0")
adb shell input keyevent KEYCODE_WAKEUP
bash "$dir/sim-start.sh" >/dev/null
bash "$dir/sim-home.sh" >/dev/null
sleep 2
adb shell am start --display 0 -n app.arcis/.MainActivity >/dev/null
sleep 3
echo "accessibility bound: $(adb shell dumpsys accessibility | grep -c 'Arcis controller, id')"
