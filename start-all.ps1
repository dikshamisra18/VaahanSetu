# VaahanSetu - Unified System Launcher
# Starts Database, Backend, Web Frontend, and Officer Desktop

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "       VaahanSetu - Complete Integrated System Launcher    " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$scriptRoot = $PSScriptRoot

# 1. Check & start Docker PostgreSQL container
$containerName = "vaahansetu-postgres"
Write-Host "[1/4] Checking PostgreSQL database container..." -ForegroundColor Yellow
$dockerCheck = docker ps --filter "name=$containerName" --format "{{.Names}}" 2>$null

if (-not $dockerCheck) {
    Write-Host "      Starting container '$containerName' on port 5433..." -ForegroundColor Cyan
    docker start $containerName | Out-Null
    Start-Sleep -Seconds 3
}
Write-Host "      [OK] PostgreSQL running on localhost:5433" -ForegroundColor Green

# 2. Launch Spring Boot Backend in an independent persistent window
Write-Host "[2/4] Checking/Starting Spring Boot Backend (Port 8080)..." -ForegroundColor Yellow

$backendReady = $false
try {
    $res = Invoke-WebRequest -Uri "http://localhost:8080/api/routes" -Method GET -TimeoutSec 2 -UseBasicParsing 2>$null
    if ($res.StatusCode -eq 200) {
        $backendReady = $true
    }
} catch {}

if ($backendReady) {
    Write-Host "      [OK] Backend is already active on http://localhost:8080" -ForegroundColor Green
} else {
    $backendScript = Join-Path $scriptRoot "start-backend.ps1"
    Start-Process powershell.exe -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", "`"$backendScript`""
    
    # Wait for Backend to become healthy
    Write-Host "      Waiting for backend to initialize..." -NoNewline -ForegroundColor Gray
    $retries = 30
    while ($retries -gt 0 -and -not $backendReady) {
        Start-Sleep -Seconds 2
        Write-Host "." -NoNewline -ForegroundColor Gray
        try {
            $res = Invoke-WebRequest -Uri "http://localhost:8080/api/routes" -Method GET -TimeoutSec 2 -UseBasicParsing 2>$null
            if ($res.StatusCode -eq 200) {
                $backendReady = $true
            }
        } catch {}
        $retries--
    }
    Write-Host ""

    if ($backendReady) {
        Write-Host "      [OK] Backend is UP and responding at http://localhost:8080" -ForegroundColor Green
    } else {
        Write-Host "      [WARN] Backend is taking longer than expected. Continuing with launch..." -ForegroundColor Yellow
    }
}

# 3. Launch Frontend Web Server & Browser
Write-Host "[3/4] Checking/Starting Web Frontend (Port 3000)..." -ForegroundColor Yellow
$frontendReady = $false
try {
    $fRes = Invoke-WebRequest -Uri "http://localhost:3000/index.html" -Method GET -TimeoutSec 2 -UseBasicParsing 2>$null
    if ($fRes.StatusCode -eq 200) { $frontendReady = $true }
} catch {}

if ($frontendReady) {
    Write-Host "      [OK] Web Frontend is already active on http://localhost:3000" -ForegroundColor Green
    try { Start-Process "http://localhost:3000/index.html" -ErrorAction SilentlyContinue } catch {}
} else {
    $frontendScript = Join-Path $scriptRoot "start-frontend.ps1"
    Start-Process powershell.exe -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", "`"$frontendScript`""
    Write-Host "      [OK] Passenger Portal: http://localhost:3000/index.html" -ForegroundColor Green
    Write-Host "      [OK] Admin Portal:     http://localhost:3000/admin.html" -ForegroundColor Green
}

# 4. Launch Officer Desktop Swing Application
Write-Host "[4/4] Starting Officer Desktop Interface..." -ForegroundColor Yellow
$officerScript = Join-Path $scriptRoot "start-officer.ps1"
Start-Process powershell.exe -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", "`"$officerScript`""
Write-Host "      [OK] Officer Swing Console launched." -ForegroundColor Green

Write-Host ""
Write-Host "All VaahanSetu services have been launched successfully!" -ForegroundColor Cyan
Write-Host "Default Credentials:" -ForegroundColor Gray
Write-Host " - Admin:   admin / admin123" -ForegroundColor Gray
Write-Host " - Officer: officer1 / officer123 (or officer / officer123)" -ForegroundColor Gray
