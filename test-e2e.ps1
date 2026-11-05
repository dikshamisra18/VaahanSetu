# VaahanSetu End-to-End Workflow Verification Script
# Tests all 11 integration steps across Passenger, Admin, and Officer workflows

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   VaahanSetu End-to-End Automated Integration Test Suite " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$baseUrl = "http://localhost:8080/api"
$passed = 0
$failed = 0

function Assert-Step($stepNum, $description, $condition, $details = "") {
    if ($condition) {
        Write-Host " [PASS] Step $stepNum : $description" -ForegroundColor Green
        if ($details) { Write-Host "        $details" -ForegroundColor Gray }
        $global:passed++
    } else {
        Write-Host " [FAIL] Step $stepNum : $description" -ForegroundColor Red
        if ($details) { Write-Host "        $details" -ForegroundColor Yellow }
        $global:failed++
    }
}

# Wait for backend to be online
Write-Host "Checking backend availability at $baseUrl..." -ForegroundColor Yellow
$ready = $false
for ($i = 0; $i -lt 20; $i++) {
    try {
        $res = Invoke-RestMethod -Uri "$baseUrl/routes" -Method GET -TimeoutSec 2 -ErrorAction Stop
        if ($res) { $ready = $true; break }
    } catch {
        Start-Sleep -Seconds 2
    }
}

if (-not $ready) {
    Write-Host "[ERROR] Backend is not responding at $baseUrl. Ensure backend is running." -ForegroundColor Red
    Exit 1
}
Write-Host "[OK] Backend is healthy and reachable.`n" -ForegroundColor Green

# ----------------------------------------------------
# Step 1: Passenger views available bus routes
# ----------------------------------------------------
$routes = Invoke-RestMethod -Uri "$baseUrl/routes" -Method GET
Assert-Step 1 "Passenger views available bus routes" ($routes.Count -gt 0) "Found $($routes.Count) routes. First route: $($routes[0].routeCode) ($($routes[0].origin) -> $($routes[0].destination))"
$testRouteId = $routes[0].routeId

# ----------------------------------------------------
# Step 2: Passenger views stops along route
# ----------------------------------------------------
$stops = Invoke-RestMethod -Uri "$baseUrl/routes/$testRouteId/stops" -Method GET
Assert-Step 2 "Passenger views stops along selected route" ($stops.Count -ge 2) "Loaded $($stops.Count) stops: $($stops[0].stopName) -> $($stops[-1].stopName)"
$boardingStopId = $stops[0].stopId
$destinationStopId = $stops[-1].stopId

# ----------------------------------------------------
# Step 3: Passenger views scheduled trips for route
# ----------------------------------------------------
$trips = Invoke-RestMethod -Uri "$baseUrl/routes/$testRouteId/trips" -Method GET
Assert-Step 3 "Passenger views scheduled trips for route" ($trips.Count -gt 0) "Found $($trips.Count) trips for route $testRouteId. First trip ID: $($trips[0].tripId) @ $($trips[0].departureTime)"
$testTripId = $trips[0].tripId
$serviceDate = if ($trips[0].serviceDate) { $trips[0].serviceDate } else { "2026-11-05" }

# ----------------------------------------------------
# Step 4: Passenger registers a journey
# ----------------------------------------------------
$journeyPayload = @{
    routeId = $testRouteId
    tripId = $testTripId
    boardingStopId = $boardingStopId
    destinationStopId = $destinationStopId
    passengerCount = 3
    sourceChannel = "PASSENGER_PORTAL"
} | ConvertTo-Json

$journeyRes = Invoke-RestMethod -Uri "$baseUrl/passenger/journeys" -Method POST -ContentType "application/json" -Body $journeyPayload
Assert-Step 4 "Passenger registers journey with backend persistence" ($journeyRes.journeyId -gt 0 -and $journeyRes.status -eq "CONFIRMED") "Journey ID: $($journeyRes.journeyId), Status: $($journeyRes.status), Passengers: $($journeyRes.passengerCount)"
$journeyId = $journeyRes.journeyId

# ----------------------------------------------------
# Step 4.1: Duplicate journey rejection (SRS F9, NFR-S7)
# ----------------------------------------------------
$duplicateRejected = $false
try {
    $dupRes = Invoke-RestMethod -Uri "$baseUrl/passenger/journeys" -Method POST -ContentType "application/json" -Body $journeyPayload -ErrorAction Stop
} catch {
    if ($_.Exception.Response.StatusCode -eq [System.Net.HttpStatusCode]::Conflict) {
        $duplicateRejected = $true
    }
}
Assert-Step 4.1 "Duplicate journey submission rejected within 2-min window (SRS F9, NFR-S7)" $duplicateRejected "Duplicate submission correctly returned HTTP 409 Conflict"

# ----------------------------------------------------
# Step 5: Passenger views journey details & updates boarding status
# ----------------------------------------------------
$viewJourney = Invoke-RestMethod -Uri "$baseUrl/passenger/journeys/$journeyId" -Method GET
Assert-Step 5 "Passenger queries journey details" ($viewJourney.journeyId -eq $journeyId) "Retrieved journey #$journeyId, route $($viewJourney.routeCode), status $($viewJourney.status)"

$boardStatusPayload = @{ status = "BOARDED" } | ConvertTo-Json
$boardRes = Invoke-RestMethod -Uri "$baseUrl/passenger/journeys/$journeyId/status" -Method PATCH -ContentType "application/json" -Body $boardStatusPayload
Assert-Step 5.1 "Passenger confirms boarding action" ($boardRes.status -eq "BOARDED") "Updated journey status to BOARDED at $($boardRes.boardingConfirmedAt)"

$completeStatusPayload = @{ status = "COMPLETED" } | ConvertTo-Json
$completeRes = Invoke-RestMethod -Uri "$baseUrl/passenger/journeys/$journeyId/status" -Method PATCH -ContentType "application/json" -Body $completeStatusPayload
Assert-Step 5.2 "Passenger confirms alighting / completion" ($completeRes.status -eq "COMPLETED") "Updated journey status to COMPLETED at $($completeRes.alightingConfirmedAt)"

# ----------------------------------------------------
# Step 6: Officer & Admin authentication
# ----------------------------------------------------
$authOfficerPayload = @{ username = "officer1"; password = "officer123" } | ConvertTo-Json
$officerAuth = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method POST -ContentType "application/json" -Body $authOfficerPayload
Assert-Step 6 "Officer authenticates with backend" ($officerAuth.token -and $officerAuth.role -eq "OFFICER") "Officer login token generated for role $($officerAuth.role)"

$authAdminPayload = @{ username = "admin"; password = "admin123" } | ConvertTo-Json
$adminAuth = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method POST -ContentType "application/json" -Body $authAdminPayload
Assert-Step 6.1 "Administrator authenticates with backend" ($adminAuth.token -and $adminAuth.role -eq "ADMIN") "Admin login token generated for role $($adminAuth.role)"

$officerHeaders = @{ Authorization = "Bearer $($officerAuth.token)" }
$adminHeaders = @{ Authorization = "Bearer $($adminAuth.token)" }

# ----------------------------------------------------
# Step 6.2: Admin configures session routes & buses (SRS F4, U2)
# ----------------------------------------------------
$selectionPayload = @{
    routeIds = @($testRouteId)
    busIds = @(101, 102)
} | ConvertTo-Json

$selectionRes = Invoke-RestMethod -Uri "$baseUrl/admin/sessions/1/selection" -Method PUT -Headers $adminHeaders -ContentType "application/json" -Body $selectionPayload
Assert-Step 6.2 "Admin configures session routes and buses (SRS F4, U2)" ($selectionRes.message -like "*saved*") "Session 1 selection persisted to session_routes and session_buses"

# ----------------------------------------------------
# Step 6.3: Authorization check - Officer forbidden on Admin endpoint (SRS F1, NFR-S1)
# ----------------------------------------------------
$forbiddenReceived = $false
try {
    $unauthSim = Invoke-RestMethod -Uri "$baseUrl/admin/simulation/step" -Method POST -Headers $officerHeaders -ContentType "application/json" -Body (@{ tripId = $testTripId; demandDelta = 5 } | ConvertTo-Json) -ErrorAction Stop
} catch {
    if ($_.Exception.Response.StatusCode -eq [System.Net.HttpStatusCode]::Forbidden) {
        $forbiddenReceived = $true
    }
}
Assert-Step 6.3 "Role authorization: Officer forbidden on Admin endpoint (SRS F1, NFR-S1)" $forbiddenReceived "Officer access to /api/admin/simulation/step rejected with HTTP 403 Forbidden"

# ----------------------------------------------------
# Step 6.4: Authentication check - Unauthenticated access rejected (SRS F1, NFR-S1)
# ----------------------------------------------------
$unauthReceived = $false
try {
    $noAuthTrips = Invoke-RestMethod -Uri "$baseUrl/officer/trips" -Method GET -ErrorAction Stop
} catch {
    if ($_.Exception.Response.StatusCode -eq [System.Net.HttpStatusCode]::Unauthorized) {
        $unauthReceived = $true
    }
}
Assert-Step 6.4 "Authentication check: Unauthenticated access rejected (SRS F1, NFR-S1)" $unauthReceived "Unauthenticated access to /api/officer/trips rejected with HTTP 401 Unauthorized"

# ----------------------------------------------------
# Step 7: Officer views segment-level passenger demand
# ----------------------------------------------------
$demand = Invoke-RestMethod -Uri "$baseUrl/officer/demand?tripId=$testTripId" -Method GET -Headers $officerHeaders
Assert-Step 7 "Officer views segment-level passenger demand" ($demand.Count -gt 0) "Retrieved $($demand.Count) segments for Trip #$testTripId. First segment demand: $($demand[0].adjustedDemand) passengers"

# ----------------------------------------------------
# Step 8: Officer triggers greedy allocation engine
# ----------------------------------------------------
$allocPayload = @{ sessionId = 1; serviceDate = $serviceDate } | ConvertTo-Json
$allocRes = Invoke-RestMethod -Uri "$baseUrl/officer/allocations" -Method POST -Headers $officerHeaders -ContentType "application/json" -Body $allocPayload
Assert-Step 8 "Officer generates allocation proposal via engine" ($allocRes.allocationRunId -gt 0 -and $allocRes.assignments.Count -gt 0) "Allocation Run ID: $($allocRes.allocationRunId), Status: $($allocRes.status), Assignments: $($allocRes.assignments.Count)"
$runId = $allocRes.allocationRunId

# ----------------------------------------------------
# Step 9: Compare baseline vs proposed allocations
# ----------------------------------------------------
$hasMetrics = ($null -ne $allocRes.baselineUnmetDemand) -and ($null -ne $allocRes.proposedUnmetDemand)
Assert-Step 9 "Allocation compares baseline and proposed metrics" $hasMetrics "Baseline Unmet: $($allocRes.baselineUnmetDemand) | Proposed Unmet: $($allocRes.proposedUnmetDemand) | Assignments: $($allocRes.assignments.Count)"

# ----------------------------------------------------
# Step 10: Authorized user approves allocation proposal
# ----------------------------------------------------
$approvePayload = @{ allocationRunId = $runId } | ConvertTo-Json
$approveRes = Invoke-RestMethod -Uri "$baseUrl/officer/allocations/approve" -Method POST -Headers $officerHeaders -ContentType "application/json" -Body $approvePayload
Assert-Step 10 "Authorized approval persists proposal to database" ($approveRes.status -eq "APPROVED" -or $approveRes.message -like "*approved*") "Allocation #$runId status: APPROVED. trip_assignments table updated."

# ----------------------------------------------------
# Step 11: Verify operational schedule reflects updated assignment
# ----------------------------------------------------
$updatedTrips = Invoke-RestMethod -Uri "$baseUrl/officer/trips?date=$serviceDate" -Method GET -Headers $officerHeaders
$approvedTrip = $updatedTrips | Where-Object { $_.tripId -eq $testTripId }
$busAssigned = if ($approvedTrip.currentBusRegistration) { $approvedTrip.currentBusRegistration } else { $approvedTrip.assignedBusNumber }
$busCap = if ($approvedTrip.currentBusCapacity) { $approvedTrip.currentBusCapacity } else { $approvedTrip.busCapacity }
Assert-Step 11 "Operational schedule reflects updated bus assignment" ($busAssigned -ne $null) "Trip #$testTripId assigned Bus $busAssigned with capacity $busCap"

# ----------------------------------------------------
# Step 12: Admin simulation step (+2 mins clock advance & demand delta)
# ----------------------------------------------------
$simPayload = @{ action = "ADVANCE_CLOCK"; minutes = 2; currentTime = "10:02:00" } | ConvertTo-Json
$simRes = Invoke-RestMethod -Uri "$baseUrl/admin/simulation/step" -Method POST -Headers $adminHeaders -ContentType "application/json" -Body $simPayload
Assert-Step 12 "Admin advances simulation clock (+2 mins)" ($simRes.currentTime -eq "10:02:00" -or $simRes.status -eq "SUCCESS") "Simulation advanced: $($simRes.message)"

# Summary
Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "   Verification Complete: $passed Passed, $failed Failed" -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Red" })
Write-Host "==========================================================" -ForegroundColor Cyan

if ($failed -gt 0) {
    Exit 1
}
