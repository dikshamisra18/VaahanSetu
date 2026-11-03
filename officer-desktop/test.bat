@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
if exist test-out rmdir /s /q test-out
mkdir test-out
set "SOURCES="
for /r "src" %%f in (*.java) do set "SOURCES=!SOURCES! "%%f""
javac -encoding UTF-8 -d test-out !SOURCES!
if errorlevel 1 (
  echo Compilation failed.
  pause
  exit /b 1
)
java -cp test-out vaahansetu.desktop.Member2SmokeCheck
if errorlevel 1 pause
