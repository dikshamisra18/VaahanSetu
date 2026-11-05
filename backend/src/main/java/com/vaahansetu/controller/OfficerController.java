package com.vaahansetu.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.vaahansetu.service.AuthService;
import com.vaahansetu.service.OfficerService;

/**
 * Officer / Admin REST endpoints used by the Swing officer desktop.
 *
 * <p>All routes require a valid Bearer token (ADMIN or OFFICER role
 * unless otherwise noted).
 */
@RestController
@RequestMapping("/api/officer")
@CrossOrigin(origins = "*")
public class OfficerController {

    private final OfficerService officerService;
    private final AuthService authService;

    public OfficerController(OfficerService officerService, AuthService authService) {
        this.officerService = officerService;
        this.authService = authService;
    }

    // ----------------------------------------------------------------
    // Active planning session
    // ----------------------------------------------------------------

    @GetMapping("/sessions/active")
    public ResponseEntity<?> getActiveSession(
            @RequestHeader(value = "Authorization", required = false) String auth) {

        if (!isAuthenticated(auth)) {
            return unauthorized();
        }
        Map<String, Object> session = officerService.getActiveSession();
        if (session.containsKey("message")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(session);
        }
        return ResponseEntity.ok(session);
    }

    // ----------------------------------------------------------------
    // Buses
    // ----------------------------------------------------------------

    @GetMapping("/buses")
    public ResponseEntity<?> getBuses(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(required = false) String time) {

        if (!isAuthenticated(auth)) return unauthorized();
        java.time.LocalTime refTime = null;
        if (time != null && !time.isBlank()) {
            try {
                if (time.length() == 5) time = time + ":00";
                refTime = java.time.LocalTime.parse(time.trim());
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(officerService.getAllBuses(refTime));
    }

    // ----------------------------------------------------------------
    // Routes
    // ----------------------------------------------------------------

    @GetMapping("/routes")
    public ResponseEntity<?> getRoutes(
            @RequestHeader(value = "Authorization", required = false) String auth) {

        if (!isAuthenticated(auth)) return unauthorized();
        return ResponseEntity.ok(officerService.getAllRoutes());
    }

    // ----------------------------------------------------------------
    // Scheduled trips
    // ----------------------------------------------------------------

    @GetMapping("/trips")
    public ResponseEntity<?> getTrips(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        if (!isAuthenticated(auth)) return unauthorized();
        return ResponseEntity.ok(officerService.getTrips(date));
    }

    // ----------------------------------------------------------------
    // Segment demand for a trip
    // ----------------------------------------------------------------

    @GetMapping({"/trips/{tripId}/demand", "/demand"})
    public ResponseEntity<?> getTripDemand(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable(required = false) Long tripId,
            @RequestParam(required = false) Long tripIdParam,
            @RequestParam(name = "tripId", required = false) Long queryTripId) {

        if (!isAuthenticated(auth)) return unauthorized();
        Long targetTripId = tripId != null ? tripId : (queryTripId != null ? queryTripId : tripIdParam);
        if (targetTripId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "tripId is required."));
        }
        return ResponseEntity.ok(officerService.getSegmentDemand(targetTripId));
    }

    // ----------------------------------------------------------------
    // Session selection
    // ----------------------------------------------------------------

    @PutMapping("/sessions/{sessionId}/selection")
    public ResponseEntity<?> saveSelection(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable long sessionId,
            @RequestBody Map<String, Object> body) {

        if (!isAuthenticated(auth)) return unauthorized();

        @SuppressWarnings("unchecked")
        List<Long> routeIds = toLongList(body.get("routeIds"));
        @SuppressWarnings("unchecked")
        List<Long> busIds   = toLongList(body.get("busIds"));

        officerService.saveSelection(sessionId, routeIds, busIds);
        return ResponseEntity.ok(Map.of("message", "Selection saved successfully."));
    }

    // ----------------------------------------------------------------
    // Simulation step (Admin only)
    // ----------------------------------------------------------------

    @PostMapping("/simulation/step")
    public ResponseEntity<?> simulateStep(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> body) {

        Map<String, Object> session = authService.validate(AuthController.extractToken(auth));
        if (session == null) {
            return unauthorized();
        }
        String role = (String) session.get("role");
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Only administrators can run simulations."));
        }

        if (body != null && "ADVANCE_CLOCK".equalsIgnoreCase(String.valueOf(body.get("action")))) {
            int minutes = 2;
            if (body.get("minutes") != null) {
                try {
                    minutes = Integer.parseInt(body.get("minutes").toString().trim());
                } catch (Exception ignored) {}
            }
            int elapsed = officerService.advanceClock(minutes);
            String currentTime = body.get("currentTime") != null ? body.get("currentTime").toString() : "10:02:00";
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "action", "ADVANCE_CLOCK",
                    "minutes", minutes,
                    "currentTime", currentTime,
                    "simulationElapsedMinutes", elapsed,
                    "message", "Simulation clock advanced by " + minutes + " minutes (Elapsed: T+" + String.format("%02d:%02d", elapsed / 60, elapsed % 60) + ")"
            ));
        }

        if (body == null || body.get("tripId") == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "tripId is required."));
        }

        long tripId;
        try {
            tripId = Long.parseLong(body.get("tripId").toString().trim());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid tripId: " + body.get("tripId")));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> segments = (List<Map<String, Object>>) body.get("segments");

        if ((segments == null || segments.isEmpty()) && body.get("demandDelta") != null) {
            int delta;
            try {
                delta = Integer.parseInt(body.get("demandDelta").toString().trim());
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid demandDelta: " + body.get("demandDelta")));
            }
            List<Map<String, Object>> existingSegments = officerService.getSegmentDemand(tripId);
            if (existingSegments.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Trip " + tripId + " not found or has no stops."));
            }
            List<Map<String, Object>> builtSegments = new ArrayList<>();
            for (Map<String, Object> seg : existingSegments) {
                long startStopId = Long.parseLong(seg.get("startStopId").toString().trim());
                long endStopId = Long.parseLong(seg.get("endStopId").toString().trim());
                builtSegments.add(Map.of(
                        "startStopId", startStopId,
                        "endStopId", endStopId,
                        "delta", delta
                ));
            }
            segments = builtSegments;
        }

        if (segments == null || segments.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "At least one segment or demand delta is required."));
        }

        try {
            Map<String, Object> result = officerService.simulateStep(tripId, segments);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Simulation failed: " + e.getMessage()));
        }
    }

    // ----------------------------------------------------------------
    // Allocation
    // ----------------------------------------------------------------

    @PostMapping("/allocations")
    public ResponseEntity<?> runAllocation(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody(required = false) Map<String, Object> body) {

        if (!isAuthenticated(auth)) return unauthorized();

        Long sessionId = null;
        if (body != null && body.get("sessionId") != null) {
            try {
                sessionId = ((Number) body.get("sessionId")).longValue();
            } catch (Exception ignored) {}
        }

        LocalDate serviceDate = null;
        if (body != null && body.get("serviceDate") != null) {
            try {
                serviceDate = LocalDate.parse(body.get("serviceDate").toString().trim());
            } catch (Exception ignored) {}
        }

        LocalDateTime allocationTime = null;
        if (body != null && body.get("allocationTime") != null) {
            try {
                allocationTime = LocalDateTime.parse(body.get("allocationTime").toString().trim());
            } catch (Exception ignored) {}
        }

        try {
            Map<String, Object> result = officerService.runAllocation(sessionId, serviceDate, allocationTime);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Allocation failed: " + e.getMessage()));
        }
    }

    @GetMapping("/allocations")
    public ResponseEntity<?> listAllocations(
            @RequestHeader(value = "Authorization", required = false) String auth) {

        if (!isAuthenticated(auth)) return unauthorized();
        return ResponseEntity.ok(officerService.getAllocationRunsBySession());
    }

    @GetMapping("/allocations/{runId}")
    public ResponseEntity<?> getAllocation(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable long runId) {

        if (!isAuthenticated(auth)) return unauthorized();
        Map<String, Object> run = officerService.getAllocationRun(runId);
        if (run == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Allocation run " + runId + " not found."));
        }
        return ResponseEntity.ok(run);
    }

    @PostMapping({"/allocations/{runId}/approve", "/allocations/approve"})
    public ResponseEntity<?> approveAllocation(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable(required = false) Long runId,
            @RequestBody(required = false) Map<String, Object> body) {

        Map<String, Object> session = authService.validate(
                AuthController.extractToken(auth));
        if (session == null) return unauthorized();

        Long targetRunId = runId;
        if (targetRunId == null && body != null && body.get("allocationRunId") != null) {
            targetRunId = ((Number) body.get("allocationRunId")).longValue();
        }
        if (targetRunId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "allocationRunId is required."));
        }

        // Both ADMIN and OFFICER may approve
        Long userId = session.get("userId") == null ? null
                : ((Number) session.get("userId")).longValue();

        try {
            Map<String, Object> result = officerService.approveAllocation(targetRunId, userId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ----------------------------------------------------------------
    // Utilities
    // ----------------------------------------------------------------

    private boolean isAuthenticated(String authHeader) {
        return authService.validate(AuthController.extractToken(authHeader)) != null;
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Authentication required. Please log in."));
    }

    @SuppressWarnings("unchecked")
    private List<Long> toLongList(Object obj) {
        if (obj == null) return List.of();
        if (obj instanceof List<?> list) {
            return list.stream()
                    .filter(v -> v != null)
                    .map(v -> ((Number) v).longValue())
                    .toList();
        }
        return List.of();
    }
}
