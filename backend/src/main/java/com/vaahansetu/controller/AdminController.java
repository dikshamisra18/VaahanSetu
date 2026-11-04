package com.vaahansetu.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.vaahansetu.service.AuthService;
import com.vaahansetu.service.OfficerService;

/**
 * Admin-specific controller implementing admin-restricted actions
 * including simulation steps.
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final OfficerService officerService;
    private final AuthService authService;

    public AdminController(OfficerService officerService, AuthService authService) {
        this.officerService = officerService;
        this.authService = authService;
    }

    @PutMapping("/sessions/{sessionId}/selection")
    public ResponseEntity<?> saveSelection(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable long sessionId,
            @RequestBody Map<String, Object> body) {

        Map<String, Object> session = authService.validate(AuthController.extractToken(auth));
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required. Please log in."));
        }
        String role = (String) session.get("role");
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Only administrators can configure session selections."));
        }

        @SuppressWarnings("unchecked")
        List<Number> rawRoutes = (List<Number>) body.get("routeIds");
        @SuppressWarnings("unchecked")
        List<Number> rawBuses = (List<Number>) body.get("busIds");

        if (rawRoutes == null || rawBuses == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "routeIds and busIds are required."));
        }

        List<Long> routeIds = rawRoutes.stream().map(Number::longValue).toList();
        List<Long> busIds = rawBuses.stream().map(Number::longValue).toList();

        officerService.saveSelection(sessionId, routeIds, busIds);
        return ResponseEntity.ok(Map.of("message", "Session selection saved successfully."));
    }

    @PostMapping("/simulation/step")
    public ResponseEntity<?> simulateStep(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> body) {

        Map<String, Object> session = authService.validate(AuthController.extractToken(auth));
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required. Please log in."));
        }
        String role = (String) session.get("role");
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Only administrators can run simulations."));
        }

        if (body != null && "ADVANCE_CLOCK".equalsIgnoreCase(String.valueOf(body.get("action")))) {
            int minutes = body.get("minutes") != null ? ((Number) body.get("minutes")).intValue() : 2;
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

        long tripId = ((Number) body.get("tripId")).longValue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> segments = (List<Map<String, Object>>) body.get("segments");

        if ((segments == null || segments.isEmpty()) && body.get("demandDelta") != null) {
            int delta = ((Number) body.get("demandDelta")).intValue();
            List<Map<String, Object>> existingSegments = officerService.getSegmentDemand(tripId);
            if (existingSegments.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Trip " + tripId + " not found or has no stops."));
            }
            List<Map<String, Object>> builtSegments = new ArrayList<>();
            for (Map<String, Object> seg : existingSegments) {
                long startStopId = ((Number) seg.get("startStopId")).longValue();
                long endStopId = ((Number) seg.get("endStopId")).longValue();
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
}
