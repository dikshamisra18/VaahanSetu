@echo off
setlocal
cd /d "%~dp0"
if exist test-out rmdir /s /q test-out
mkdir test-out
dir /s /b src\*.java > test-sources.txt
javac -encoding UTF-8 -d test-out @test-sources.txt
del test-sources.txt
if errorlevel 1 (
  echo Compilation failed.
  pause
  exit /b 1
)
java -cp test-out vaahansetu.desktop.Member2SmokeCheck
if errorlevel 1 (
  echo Smoke checks failed.
  pause
  exit /b 1
)
java -cp test-out vaahansetu.desktop.ApiServicesSmokeCheck
