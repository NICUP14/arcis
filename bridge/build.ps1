# Builds the Windows Arcis Bridge and its installer:
#   <OutDir>\arcis-bridge.exe            (no console window; lives in the tray)
#   <OutDir>\ArcisBridge-<version>.msi   (unsigned)
#
# Needs Go and the WiX tool (once: dotnet tool install --global wix).
# Usage: .\build.ps1 [-Version 0.2.0] [-OutDir dist]
param(
    [string]$Version = "",
    [string]$OutDir = "dist"
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not $Version) {
    $m = Select-String -Path cmd\arcis-bridge\main.go -Pattern 'var Version = "([^"]+)"'
    $Version = $m.Matches[0].Groups[1].Value
}
$out = (New-Item -ItemType Directory -Force $OutDir).FullName
$exe = Join-Path $out "arcis-bridge.exe"
$msi = Join-Path $out "ArcisBridge-$Version.msi"

Write-Host "Building arcis-bridge.exe $Version"
$env:CGO_ENABLED = "0"
$env:GOOS = "windows"
$env:GOARCH = "amd64"
go build -trimpath -ldflags "-s -w -H=windowsgui -X main.Version=$Version" -o $exe ./cmd/arcis-bridge
if ($LASTEXITCODE -ne 0) { throw "go build failed" }

$wix = Get-Command wix -ErrorAction SilentlyContinue
if ($wix) {
    $wix = $wix.Source
} else {
    $wix = Join-Path $env:USERPROFILE ".dotnet\tools\wix.exe"
    if (-not (Test-Path $wix)) { throw "WiX not found: dotnet tool install --global wix" }
}
$wixVersion = ((& $wix --version) -split '\+')[0]
foreach ($ext in "WixToolset.Firewall.wixext", "WixToolset.Util.wixext") {
    & $wix extension add -g "$ext/$wixVersion" | Out-Null
}

Write-Host "Building $(Split-Path $msi -Leaf)"
& $wix build installer\arcis-bridge.wxs -arch x64 `
    -ext WixToolset.Firewall.wixext -ext WixToolset.Util.wixext `
    -d Version=$Version -d BridgeExe=$exe -o $msi
if ($LASTEXITCODE -ne 0) { throw "wix build failed" }

# Windows Installer's own consistency checks. ICE57 is skipped: it can't tell that the
# Start Menu folder is the all-users one in this per-machine package.
& $wix msi validate $msi -sice ICE57
if ($LASTEXITCODE -ne 0) { throw "MSI validation failed" }
Remove-Item (Join-Path $out "*.wixpdb") -ErrorAction SilentlyContinue
Write-Host "Done: $msi"
