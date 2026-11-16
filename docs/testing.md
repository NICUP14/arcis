# Testing Arcis without glasses

The glasses only display the picture, so a debug build can simulate them.

## Simulated screen (debug builds only)

```sh
cd android
./gradlew installDebug
scripts/sim-start.sh   # creates a 1920x1080 public presentation display (accepts the capture prompt)
scripts/sim-home.sh    # puts Arcis Home on it (first launch must come from adb on a virtual display)
adb shell am start --display 0 -n app.arcis/.MainActivity
scripts/sim-dump.sh    # saves the current frame of the simulated screen to sim.png
```

Why not the developer option "Simulate secondary displays" (`overlay_display_devices`)?
Android's accessibility system ignores overlay displays, so injected taps are cancelled
there. The debug simulated screen is a public virtual display, which accessibility accepts.
Third-party apps cannot be launched onto it (untrusted virtual display); test phone apps on
a real screen or on the overlay display (launch only).

## Accessibility service during automated tests

Android suspends all accessibility services while a UiAutomation session is connected
(uiautomator, Appium, mobile-mcp's `mobilecli` device server…). If the Arcis controller
stops reacting during automation, stop the automation helper; the service rebinds on its own.

## What needs real hardware

- The glasses / a monitor over USB-C DisplayPort (display routing, refresh rate)
- Netflix / DRM playback quality
- Bluetooth HID pairing
- Moonlight + Sunshine streaming, Tailscale remote use
