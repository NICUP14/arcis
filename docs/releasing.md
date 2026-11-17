# Making a release

Pushing a version tag for a commit on `main` publishes a GitHub release automatically
(`.github/workflows/release.yml`):

| File | Built on | What |
|---|---|---|
| `Arcis-<version>.apk` | Ubuntu | The phone app, signed with the release key |
| `ArcisBridge-<version>.msi` | Windows | The Arcis Bridge installer (unsigned) |
| `SHA256SUMS.txt` | | Checksums of both |

Unit tests (`android/core`, `bridge`) run first; a failing test stops the release.

## Release a version

```sh
git checkout main
git merge dev                 # what you want to release
git push origin main
git tag v0.2.0                # vMAJOR.MINOR.PATCH, or v0.2.0-beta.1 for a pre-release
git push origin v0.2.0
```

The version comes from the tag: the APK gets `versionName 0.2.0` and `versionCode 200`
(`MAJOR*10000 + MINOR*100 + PATCH`, so every new version must be higher), and the MSI
gets version 0.2.0. A tag with a suffix (`-beta.1`) is published as a pre-release. A
tag on a commit that isn't on `main` is rejected.

## One-time setup: the signing key

Android only updates an app with a new version signed by **the same key**. Create the
key once, keep it safe (losing it means users must uninstall to update), and give it to
GitHub as secrets. It never goes into the repository.

1. Create the key (JDK's `keytool`):

   ```sh
   keytool -genkeypair -v -keystore arcis-release.jks -alias arcis \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Back up `arcis-release.jks` and its passwords somewhere safe (a password manager).

3. In the GitHub repository: **Settings › Secrets and variables › Actions › New
   repository secret**, add:

   | Secret | Value |
   |---|---|
   | `ARCIS_KEYSTORE_BASE64` | the keystore as base64: `base64 -w0 arcis-release.jks` (Linux), `[Convert]::ToBase64String([IO.File]::ReadAllBytes("arcis-release.jks"))` (PowerShell) |
   | `ARCIS_KEYSTORE_PASSWORD` | the keystore password |
   | `ARCIS_KEY_ALIAS` | `arcis` |
   | `ARCIS_KEY_PASSWORD` | the key password |

Without these secrets the release job stops with a message pointing here.

To build a signed APK locally, set the same values as environment variables
(`ARCIS_KEYSTORE` = path to the `.jks`, plus the three passwords/alias) and run
`./gradlew :app:assembleRelease`. Without them the release APK is left unsigned.

## The Windows installer

`bridge/build.ps1` builds the tray exe and the MSI (WiX **5**:
`dotnet tool install --global wix --version 5.0.2`). WiX 6+ requires accepting the Open
Source Maintenance Fee EULA, so the project stays on WiX 5 for now. The MSI isn't
code-signed: Windows SmartScreen shows "Windows protected your PC" (users click *More
info › Run anyway*); signing can be added later as one step in the workflow.

## Version in the bridge

The bridge reports its version (`arcis-bridge -version`, status page). `build.ps1`
sets it from the tag via `-ldflags -X main.Version=...`.
