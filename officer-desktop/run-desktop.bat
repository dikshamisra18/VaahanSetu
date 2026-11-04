@echo off
setlocal
cd /d "%~dp0"

REM If an argument is passed (e.g. https://vaahansetu-api.onrender.com/api), use it
set TARGET_URL=%~1
if "%TARGET_URL%"=="" (
    if not "%VAAHAN_API_URL%"=="" (
        set TARGET_URL=%VAAHAN_API_URL%
    ) else (
        set TARGET_URL=http://localhost:8080/api
    )
)

echo ========================================================
echo  VaahanSetu Officer Desktop Console
echo  Connecting to: %TARGET_URL%
echo ========================================================

java -jar vaahansetu-desktop.jar "%TARGET_URL%"
if errorlevel 1 (
    echo.
    echo Desktop application closed with an exit code %errorlevel%.
    pause
)
