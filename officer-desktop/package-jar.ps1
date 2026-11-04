# Package Officer Desktop JAR
$jdk = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot\bin"
if (-not (Test-Path "$jdk\jar.exe")) {
    $jdk = "C:\Program Files\Java\jdk-26.0.2\bin"
}

$desktopDir = Join-Path $PSScriptRoot "out"
if (-not (Test-Path $desktopDir)) {
    New-Item -ItemType Directory -Path $desktopDir | Out-Null
}

$sources = Get-ChildItem -Path (Join-Path $PSScriptRoot "src\main\java") -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
Write-Host "Compiling $($sources.Count) Java classes..." -ForegroundColor Cyan
& "$jdk\javac.exe" -encoding UTF-8 -d $desktopDir $sources

$jarFile = Join-Path $PSScriptRoot "vaahansetu-desktop.jar"
Write-Host "Creating executable JAR: $jarFile..." -ForegroundColor Cyan
& "$jdk\jar.exe" cvfe $jarFile vaahansetu.desktop.ui.Main -C $desktopDir .

if (Test-Path $jarFile) {
    $size = (Get-Item $jarFile).Length / 1KB
    Write-Host "[SUCCESS] Packaged $jarFile ($([Math]::Round($size, 1)) KB)" -ForegroundColor Green
} else {
    Write-Host "[ERROR] Failed to create JAR file." -ForegroundColor Red
}
