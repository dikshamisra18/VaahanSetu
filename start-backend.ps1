# VaahanSetu - Start Backend Service
# Starts the Spring Boot backend with Neon cloud database (or -Local for Docker)

param(
    [switch]$Local
)

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " Starting VaahanSetu Spring Boot Backend " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# Check if port 8080 is already occupied by a healthy backend
try {
    $health = Invoke-WebRequest -Uri "http://localhost:8080/api/routes" -Method GET -TimeoutSec 2 -UseBasicParsing -ErrorAction SilentlyContinue
    if ($health -and $health.StatusCode -eq 200) {
        Write-Host "[OK] Spring Boot Backend is already running and healthy on port 8080." -ForegroundColor Green
        Exit 0
    }
} catch {
    # Not running, proceed with startup
}

# Locate Maven
$mvnCmd = "mvn"
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    if (Test-Path "C:\Program Files\apache-maven-3.10.0\bin\mvn.cmd") {
        $mvnCmd = "C:\Program Files\apache-maven-3.10.0\bin\mvn.cmd"
    } elseif (Test-Path "C:\apache-maven-3.10.0\bin\mvn.cmd") {
        $mvnCmd = "C:\apache-maven-3.10.0\bin\mvn.cmd"
    }
}

if ($Local) {
    # Ensure Docker PostgreSQL container is running
    $containerName = "vaahansetu-postgres"
    $dockerCheck = docker ps --filter "name=$containerName" --format "{{.Names}}" 2>$null
    if (-not $dockerCheck) {
        Write-Host "[INFO] Starting local PostgreSQL container '$containerName'..." -ForegroundColor Yellow
        docker start $containerName | Out-Null
        Start-Sleep -Seconds 3
    }
    Write-Host "[OK] Using local PostgreSQL container on port 5433." -ForegroundColor Green
    $profileArg = "-Dspring-boot.run.profiles=local"
} else {
    Write-Host "[OK] Connecting to shared Neon Cloud Database..." -ForegroundColor Green
    $profileArg = "-Dspring-boot.run.profiles=default"
}

# Navigate to backend directory
$backendDir = Join-Path $PSScriptRoot "backend"
Set-Location $backendDir

$logFile = Join-Path $backendDir "backend.log"
Write-Host "[INFO] Launching Spring Boot with profile '$($profileArg)' (UTC timezone)..." -ForegroundColor Cyan
Write-Host "[INFO] Logging to $logFile and console..." -ForegroundColor Gray
& $mvnCmd spring-boot:run $profileArg "-Dspring-boot.run.jvmArguments=-Duser.timezone=UTC" *>&1 | Tee-Object -FilePath $logFile
