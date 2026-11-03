@echo off
setlocal
cd /d "%~dp0"

echo Checking Java compiler...
where javac >nul 2>nul
if errorlevel 1 (
  echo ERROR: Java JDK was not found. Install JDK 17 or newer and reopen VS Code.
  pause
  exit /b 1
)

if exist out rmdir /s /q out
mkdir out

dir /s /b src\main\java\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
del sources.txt
if errorlevel 1 (
  echo.
  echo Compilation failed. Copy the error messages and send them to me.
  pause
  exit /b 1
)

echo.
echo Compilation successful. Starting VaahanSetu Officer Desktop...
java -cp out vaahansetu.desktop.ui.Main
if errorlevel 1 (
  echo.
  echo The desktop application could not start. Copy the error messages and send them to me.
  pause
  exit /b 1
)
