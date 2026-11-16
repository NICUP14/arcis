#!/usr/bin/env bash
# Save the current frame of the simulated screen to ./sim.png
export MSYS_NO_PATHCONV=1
adb shell am start-foreground-service -n app.arcis/.debug.SimScreenService -a dump >/dev/null
sleep 1
adb pull /sdcard/Android/data/app.arcis/files/sim.png "${1:-sim.png}"
