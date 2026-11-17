<p align="center">
  <img src="docs/assets/logo.svg" width="112" alt="Arcis logo">
</p>

<h1 align="center">Arcis</h1>

<p align="center">
  <b>Your phone as the remote for your AR glasses.</b><br>
  Watch, browse and work on a big screen in your glasses, and control it all from your phone without looking at it.
</p>

<p align="center">
  <a href="https://github.com/NICUP14/arcis/releases/latest"><b>Download</b></a> ·
  <a href="docs/getting-started.md">Getting started</a> ·
  <a href="docs/using-arcis.md">How to use it</a> ·
  <a href="docs/troubleshooting.md">Help</a>
</p>

---

Display glasses such as XREAL, VITURE, RayNeo or Rokid show a big virtual screen, but
controlling what's on it is awkward: you look down at the phone, find the button, look
up again. **Arcis** turns your phone into a trackpad and keyboard for the glasses, like
the one on a laptop. One full-screen app at a time, switched like on a phone.

- **Phone apps on the big screen.** Netflix, YouTube, the browser, WhatsApp… open
  full screen in the glasses.
- **Your Windows PC on the big screen.** Your desktop appears in the glasses through the free
  [Moonlight](https://moonlight-stream.org) app, and the phone controls it. At home or
  anywhere else.
- **Any Bluetooth device.** Your phone becomes its mouse and keyboard.
- **Made to be used blind.** Swipe to move the pointer, tap to click, swipe along the
  bottom to switch apps. Draw a letter for a shortcut (C copy, V paste, Z undo…). Hold
  the mic button and speak to type.
- **Movie friendly.** When a video plays, tap to pause and swipe to skip, no
  aiming. Messages pop up as small banners you can open or dismiss.

Works with any glasses or external screen your phone can show a picture on over USB-C.
No root, no special phone, no account.

## What you need

| | |
|---|---|
| **Glasses** | Display glasses or any external screen that connects over **USB-C** (XREAL, VITURE, RayNeo, Rokid, portable monitors…). |
| **Phone** | **Android 11 or newer** with **video output over USB-C** (DisplayPort). Most Samsung Galaxy S, Note, Z Fold and Tab models do; many other flagships too. Your glasses maker usually has a compatibility list. |
| **For your PC (optional)** | A **Windows 10/11** PC, plus the free [Moonlight](https://moonlight-stream.org) app on the phone. Arcis installs the rest for you. |

> Tip: glasses take power from the phone. For long sessions, use a USB-C hub or adapter
> that charges the phone while the glasses are connected.

## Quick start

1. **Download** `Arcis-x.y.z.apk` from the [latest release](https://github.com/NICUP14/arcis/releases/latest)
   on your phone and install it ([how?](docs/getting-started.md#2-install-the-app)).
2. **Open Arcis** and follow the short checklist. It explains each permission and takes
   you to the right settings screen.
3. **Plug in your glasses.** Arcis Home appears in them.
4. **Use the phone as a trackpad**: slide a finger to move the pointer, tap to open.
5. **Want your PC too?** Install `ArcisBridge-x.y.z.msi` on the PC and scan the code
   it shows ([guide](docs/computer.md)).

The [getting started guide](docs/getting-started.md) walks through every step.

## Documentation

**Using Arcis**

- [Getting started](docs/getting-started.md): install, set up, first steps
- [How to use Arcis](docs/using-arcis.md): gestures, switching apps, typing, videos, settings
- [Your PC on the glasses](docs/computer.md): Arcis Bridge, Moonlight and Sunshine
- [Away from home](docs/away-from-home.md): reach your PC over the internet with Tailscale
- [Troubleshooting](docs/troubleshooting.md): when something doesn't work

**Technical**

- [Arcis Bridge reference](docs/bridge.md): options, Linux/macOS (untested), security
- [Bridge protocol](docs/protocol.md)
- [Testing without glasses](docs/testing.md)
- [Making a release](docs/releasing.md)

## Status

Arcis is young. It is used daily on a Galaxy S20 FE with XREAL 1S glasses and a
Windows 11 PC. Other phones, glasses and the Linux / macOS bridge haven't been tested
yet: reports (good or bad) are very welcome in
[Issues](https://github.com/NICUP14/arcis/issues).

## Privacy

Arcis has no account, no ads, no analytics and no servers. Everything stays between
your phone, your glasses and your own PC. Notification text is shown in the glasses
only and never sent anywhere. The connection to your PC is encrypted and needs a
one-time pairing.

## Build from source

```sh
# Phone app (JDK 17, Android SDK 37)
cd android
./gradlew test assembleDebug        # APK in app/build/outputs/apk/debug/

# Arcis Bridge (Go 1.26)
cd bridge
go test ./...
./build.ps1                         # Windows: tray app + installer in dist/ (needs WiX 5)
```

| Folder | What's inside |
|---|---|
| `android/` | The phone app (Kotlin, Jetpack Compose). `core` holds the logic with unit tests. |
| `bridge/` | The Arcis Bridge for the PC (Go). Windows is tested; Linux and macOS build but are untested. |
| `docs/` | Guides. |

## Thanks

Arcis stands on the shoulders of [Moonlight](https://moonlight-stream.org) and
[Sunshine](https://github.com/LizardByte/Sunshine), which do the hard work of streaming
your PC. Arcis is not affiliated with them or with any glasses maker.

## License

[MIT](LICENSE): free to use, change and share.
