# Arcis — AR Companion Interface System

Turn any external screen your Android phone can drive over USB-C (AR glasses such as
XREAL / VITURE / RayNeo / Rokid, portable monitors, TVs) into a big screen you control
**with the phone, without looking at it**.

- **One full screen at a time, switched like apps on a phone.**
- **Phone apps** (Netflix, YouTube, browser, …) run directly on the external screen.
- **PCs / hosts**: video via the official **Moonlight** app (Sunshine/Apollo on the host),
  controls via the small **Arcis Bridge** running on the host.
- **Bluetooth devices**: the phone acts as a Bluetooth mouse + keyboard.
- **Two control modes**: *Upright* (phone keyboard + trackpad/gestures) and
  *Landscape* (full-screen trackpad + speech-to-text).
- No root, no Shizuku, no DeX.

**Tested on:** Galaxy S20 FE (Android 13) with XREAL 1S glasses; Arcis Bridge on
Windows 11. The Linux and macOS bridges build but have not been tested yet.

## Repository layout

| Path | What |
|---|---|
| `android/` | Android app (Kotlin, Jetpack Compose). `app` = Android code, `core` = pure-Kotlin logic with JVM unit tests |
| `bridge/` | Arcis Bridge (Go, pure Go): Windows (tested), Linux / macOS (builds, **untested**) |
| `docs/` | Setup guides |

## Build

```sh
# Android (JDK 17, Android SDK 37)
cd android
./gradlew assembleDebug      # APK in app/build/outputs/apk/debug/
./gradlew test               # unit tests

# Bridge (Go)
cd bridge
go test ./...
go build ./cmd/arcis-bridge
./build.ps1                  # Windows: tray exe + MSI installer in dist/ (needs WiX 5)
```

## License

MIT — see [LICENSE](LICENSE).
