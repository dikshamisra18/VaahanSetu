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
        List<?> rawRoutes = (List<?>) body.get("routeIds");
        @SuppressWarnings("unchecked")
        List<?> rawBuses = (List<?>) body.get("busIds");

        if (rawRoutes == null || rawBuses == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "routeIds and busIds are required."));
        }

        List<Long> routeIds = rawRoutes.stream()
                .filter(v -> v != null)
                .map(v -> Long.parseLong(v.toString().trim()))
                .toList();
        List<Long> busIds = rawBuses.stream()
                .filter(v -> v != null)
                .map(v -> Long.parseLong(v.toString().trim()))
                .toList();

        officerService.saveSelection(sessionId, routeIds, busIds);
        return ResponseEntity.ok(Map.of("message", "Session selection saved successfully."));
    }

    @PostMapping({"/demand/adjust", "/simulation/step"})
    public ResponseEntity<?> adjustDemand(
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
                    .body(Map.of("message", "Only administrators can configure operational demand."));
        }

        if (body != null && "ADVANCE_CLOCK".equalsIgnoreCase(String.valueOf(body.get("action")))) {
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "action", "ADVANCE_CLOCK",
                    "message", "Simulation clock advance disabled per operational directive."
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

        if (body.get("demandDelta") != null) {
            int delta;
            try {
                delta = Integer.parseInt(body.get("demandDelta").toString().trim());
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid demandDelta: " + body.get("demandDelta")));
            }
            try {
                Map<String, Object> result = officerService.adjustDemand(tripId, delta);
                return ResponseEntity.ok(result);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("message", "Demand adjustment failed: " + e.getMessage()));
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> segments = (List<Map<String, Object>>) body.get("segments");
        if (segments == null || segments.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "demandDelta or segments are required."));
        }

        try {
            Map<String, Object> result = officerService.simulateStep(tripId, segments);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Adjustment failed: " + e.getMessage()));
        }
    }
}
