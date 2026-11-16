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

### Moonlight on the overlay display

Moonlight (and the computer controls) can be tested on the overlay display:

```sh
adb shell settings put global overlay_display_devices 1920x1080/320
adb shell input -d <overlay id> tap <x> <y>   # taps on that display (find the id with dumpsys display)
adb shell input -d 0 swipe ...                # trackpad swipes on the phone
```

Accessibility lists no windows for overlay displays, so Arcis follows activity events to
see what's in front there. The overlay display is drawn as a floating window on the phone
that takes touches starting inside it (it moves/resizes): start trackpad swipes outside
it, and always pass `-d 0`, otherwise `input` may target the focused overlay display.

## Accessibility service during automated tests

Android suspends all accessibility services while a UiAutomation session is connected
(uiautomator, Appium, mobile-mcp's `mobilecli` device server…). If the Arcis controller
stops reacting during automation, stop the automation helper; the service rebinds on its own.

## What needs real hardware

- The glasses / a monitor over USB-C DisplayPort (display routing, refresh rate)
- Netflix / DRM playback quality
- Bluetooth HID pairing
- Tailscale remote use
- Moonlight + Sunshine on the real glasses (tested on the overlay display: pairing,
  streaming, trackpad → bridge while Moonlight is in front)
