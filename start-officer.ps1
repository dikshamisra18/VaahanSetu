# VaahanSetu - Start Officer Desktop Console
# Compiles and launches the Java Swing Officer Application

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " Starting VaahanSetu Officer Console     " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

$officerDir = Join-Path $PSScriptRoot "officer-desktop"
Set-Location $officerDir

Write-Host "[INFO] Compiling Officer Desktop..." -ForegroundColor Cyan
& .\compile.bat

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Failed to compile Officer Desktop." -ForegroundColor Red
    Exit 1
}

Write-Host "[INFO] Launching Java Swing Officer Desktop..." -ForegroundColor Green
java -cp "out;lib/*" vaahansetu.desktop.ui.Main
