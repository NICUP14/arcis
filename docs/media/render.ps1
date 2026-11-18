# Renders the README and social images in docs/media from the HTML templates in docs/media/src,
# using Microsoft Edge in headless mode. No other tools needed.
#
#   pwsh docs/media/render.ps1            # all images
#   pwsh docs/media/render.ps1 hero       # only images whose name starts with "hero"
#
# To refresh after new screenshots: replace the PNGs in docs/media/shots/ and run this again.

param([string]$Only = "")

$ErrorActionPreference = "Stop"
$here = $PSScriptRoot
$src = Join-Path $here "src"

$edge = @(
    "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
    "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $edge) { throw "Microsoft Edge not found." }

# name = output file (without .png), page = template, query = template options, w/h = image size
$jobs = @(
    @{ name = "hero";             page = "hero.html";   query = "";                                   w = 1600; h = 800 }
    @{ name = "social-preview";   page = "social.html"; query = "";                                   w = 1280; h = 640 }
    @{ name = "feature-apps";      page = "feature.html"; query = "shot=glasses-home&x=0&y=30&zoom=1";   w = 800;  h = 450 }
    @{ name = "feature-pc";        page = "feature.html"; query = "shot=switcher&x=524&y=309&zoom=2.2";  w = 800;  h = 450 }
    @{ name = "feature-messages";  page = "feature.html"; query = "shot=banner&x=591&y=-54&zoom=2.6";     w = 800;  h = 450 }
    @{ name = "feature-shortcuts"; page = "feature.html"; query = "shot=gestures&x=800&y=225&zoom=1.82"; w = 800;  h = 450 }
    @{ name = "gestures";          page = "feature.html"; query = "shot=gestures&x=190&y=140&zoom=1.25&pad=40"; w = 1600; h = 900 }
)

$profile = Join-Path ([IO.Path]::GetTempPath()) "arcis-media-render"
New-Item -ItemType Directory -Force $profile | Out-Null

foreach ($j in $jobs) {
    if ($Only -and -not $j.name.StartsWith($Only)) { continue }
    $out = Join-Path $here ($j.name + ".png")
    $url = ([Uri](Join-Path $src $j.page)).AbsoluteUri
    $url += "?w=$($j.w)&h=$($j.h)"
    if ($j.query) { $url += "&" + $j.query }
    if (Test-Path $out) { Remove-Item $out }
    # Start-Process: Edge logs harmless warnings to stderr, which Windows PowerShell would treat as errors.
    $edgeArgs = @(
        "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
        "--allow-file-access-from-files", "--user-data-dir=`"$profile`"",
        "--window-size=$($j.w),$($j.h)", "--virtual-time-budget=2000",
        "--screenshot=`"$out`"", "`"$url`""
    )
    Start-Process -FilePath $edge -ArgumentList $edgeArgs -Wait -WindowStyle Hidden
    # Edge returns before the file is flushed on some machines.
    for ($i = 0; $i -lt 50 -and -not (Test-Path $out); $i++) { Start-Sleep -Milliseconds 100 }
    if (-not (Test-Path $out)) { throw "Render failed: $($j.name)" }
    "{0,-20} {1,5} KB" -f $j.name, [math]::Round((Get-Item $out).Length / 1KB)
}
