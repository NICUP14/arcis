# Making a release

Releases are built on a Windows PC and published by hand on GitHub. Each release has:

| File | What |
|---|---|
| `Arcis-<version>.apk` | The phone app, signed with the release key |
| `ArcisBridge-<version>.msi` | The Arcis Bridge installer for Windows (unsigned) |
| `SHA256SUMS.txt` | Checksums of both |

## Steps

Pick the version, e.g. `0.2.0` (`MAJOR.MINOR.PATCH`). Then, from the repository root
in PowerShell:

```powershell
$v = "0.2.0"
$code = 200          # Android versionCode: MAJOR*10000 + MINOR*100 + PATCH; must grow every release

# 1. Tests and the signed phone app (signing variables: see below)
cd android
.\gradlew test :app:assembleRelease "-PversionName=$v" "-PversionCode=$code"
cd ..

# 2. Bridge tests and the Windows installer
cd bridge
go test ./...
.\build.ps1 -Version $v -OutDir ..\release
cd ..

# 3. Collect the files and checksums
Copy-Item android\app\build\outputs\apk\release\app-release.apk "release\Arcis-$v.apk"
Remove-Item release\arcis-bridge.exe
Get-FileHash release\* -Algorithm SHA256 | ForEach-Object { "$($_.Hash.ToLower())  $(Split-Path $_.Path -Leaf)" } |
    Set-Content release\SHA256SUMS.txt

# 4. Tag and publish (the GitHub CLI, or the Releases page on github.com)
git tag "v$v"
git push origin main "v$v"
gh release create "v$v" release\* --title "Arcis $v" --notes-file docs\release-notes.md
```

`release/` is ignored by git. `docs/release-notes.md` is the text shown on the release
page (the hero image, which file to download, the SmartScreen note); fill in "What's new"
before each release.

## The signing key

Android only installs an update if it is signed by **the same key** as the installed
app. Keep the key and its passwords safe and backed up (a password manager): losing it
means everyone has to uninstall Arcis to get updates. It never goes into the repository.

Create it once (JDK's `keytool`):

```powershell
keytool -genkeypair -v -keystore arcis-release.jks -alias arcis -keyalg RSA -keysize 4096 -validity 10000
```

Before building, point Gradle to it with environment variables (PowerShell):

```powershell
$env:ARCIS_KEYSTORE = "C:\path\to\arcis-release.jks"
$env:ARCIS_KEYSTORE_PASSWORD = "..."
$env:ARCIS_KEY_ALIAS = "arcis"
$env:ARCIS_KEY_PASSWORD = "..."
```

Without them, `assembleRelease` produces an unsigned `app-release-unsigned.apk`, which
phones won't install.

## The Windows installer

`bridge/build.ps1` builds the tray exe and the MSI with WiX **5**
(`dotnet tool install --global wix --version 5.0.2`). WiX 6+ requires accepting the Open
Source Maintenance Fee EULA, so the project stays on WiX 5 for now. The MSI isn't
code-signed: Windows SmartScreen shows "Windows protected your PC" (users click *More
info › Run anyway*).
