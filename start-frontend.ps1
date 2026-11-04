# VaahanSetu - Start Frontend Service
# Serves the Passenger Portal and Admin Portal on port 3000

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " Starting VaahanSetu Web Frontend (3000) " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

$serverScript = Join-Path $PSScriptRoot "frontend\server.js"

# Open default browser to Passenger Portal if desktop environment is available
try { Start-Process "http://localhost:3000/index.html" -ErrorAction SilentlyContinue } catch {}

# Run Node server
node $serverScript
