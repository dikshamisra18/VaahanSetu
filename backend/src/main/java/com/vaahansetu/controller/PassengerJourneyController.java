package com.vaahansetu.controller;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vaahansetu.repository.JourneyRepository;

@RestController
@RequestMapping({"/api/passenger-journeys", "/api/passenger/journeys"})
@CrossOrigin(origins = "*")
public class PassengerJourneyController {

    private final JourneyRepository journeyRepository;

    public PassengerJourneyController(JourneyRepository journeyRepository) {
        this.journeyRepository = journeyRepository;
    }

    // Register a passenger journey
    @PostMapping
    public ResponseEntity<Map<String, Object>> registerJourney(
            @RequestBody Map<String, Object> request) {

        if (request == null) {
            return error(
                    HttpStatus.BAD_REQUEST,
                    "Please provide journey details."
            );
        }

        Long routeId = toLong(request.get("routeId"));
        Long boardingStopId = toLong(request.get("boardingStopId"));
        Long destinationStopId = toLong(request.get("destinationStopId"));
        Integer passengerCount = toInteger(request.get("passengerCount"));
        Long requestedTripId = toLong(request.get("tripId"));

        // Validate required fields
        if (routeId == null
                || boardingStopId == null
                || destinationStopId == null
                || passengerCount == null) {

            return error(
                    HttpStatus.BAD_REQUEST,
                    "Please provide all journey details."
            );
        }

        // Validate passenger count
        if (passengerCount < 1 || passengerCount > 10) {
            return error(
                    HttpStatus.BAD_REQUEST,
                    "Passenger count must be between 1 and 10."
            );
        }

        // Validate route stops and their order
        List<Long> stopOrder = journeyRepository.findStopOrder(routeId);

        int boardingIndex = stopOrder.indexOf(boardingStopId);
        int destinationIndex = stopOrder.indexOf(destinationStopId);

        if (boardingIndex == -1 || destinationIndex == -1) {
            return error(
                    HttpStatus.BAD_REQUEST,
                    "The selected stops do not belong to this route."
            );
        }

        if (boardingIndex >= destinationIndex) {
            return error(
                    HttpStatus.BAD_REQUEST,
                    "Destination stop must be after the boarding stop."
            );
        }

        Long tripId = requestedTripId;
        if (tripId == null) {
            // Find today's trips with active bus allocations
            List<Map<String, Object>> trips = journeyRepository.findTodaysTrips(routeId);
            if (trips.isEmpty()) {
                return error(
                        HttpStatus.CONFLICT,
                        "No buses available at the moment. Please wait..."
                );
            }
            tripId = toLong(trips.get(0).get("tripId"));
        }

        if (tripId == null) {
            return error(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to identify the scheduled trip."
            );
        }

        // Find the active planning session
        Long sessionId = journeyRepository.findActiveSessionId();
        if (sessionId == null) {
            return error(
                    HttpStatus.CONFLICT,
                    "No active planning session is available at the moment."
            );
        }

        // Duplicate submission prevention within 2-minute window (SRS F9, NFR-S7)
        if (journeyRepository.isDuplicateJourney(sessionId, tripId, boardingStopId, destinationStopId, passengerCount)) {
            return error(
                    HttpStatus.CONFLICT,
                    "Duplicate journey submission detected within the last 2 minutes. Please wait before submitting again."
            );
        }

        // Save journey in the database
        Long journeyId = journeyRepository.saveJourney(
                sessionId,
                tripId,
                boardingStopId,
                destinationStopId,
                passengerCount
        );

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("message", "Your journey has been registered successfully.");
        resp.put("journeyId", journeyId);
        resp.put("tripId", tripId);
        resp.put("journeyStatus", "REGISTERED");
        resp.put("status", "CONFIRMED");
        resp.put("passengerCount", passengerCount);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
    }

    // Get journey details by ID
    @GetMapping("/{journeyId}")
    public ResponseEntity<Map<String, Object>> getJourney(
            @PathVariable Long journeyId) {

        Map<String, Object> journey = journeyRepository.findJourneyById(journeyId);
        if (journey == null) {
            return error(
                    HttpStatus.NOT_FOUND,
                    "Journey not found."
            );
        }

        return ResponseEntity.ok(journey);
    }

    // Get common passenger demand for the active planning session and date
    @GetMapping("/demand")
    public ResponseEntity<List<Map<String, Object>>> getPassengerDemand(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Long sessionId = journeyRepository.findActiveSessionId();
        List<Map<String, Object>> demand = journeyRepository.findPassengerDemand(sessionId, date);
        return ResponseEntity.ok(demand != null ? demand : List.of());
    }

    // Record passenger search intent into common demand
    @PostMapping({"/intent", "/search"})
    public ResponseEntity<Map<String, Object>> recordSearchIntent(
            @RequestBody Map<String, Object> request) {

        if (request == null) {
            return error(HttpStatus.BAD_REQUEST, "Please provide search intent details.");
        }

        Long routeId = toLong(request.get("routeId"));
        Long boardingStopId = request.get("boardingStopId") != null
                ? toLong(request.get("boardingStopId"))
                : toLong(request.get("startStopId"));
        Long destinationStopId = request.get("destinationStopId") != null
                ? toLong(request.get("destinationStopId"))
                : toLong(request.get("endStopId"));
        Integer count = request.get("passengerCount") != null
                ? toInteger(request.get("passengerCount"))
                : toInteger(request.get("passengers"));
        Long tripId = toLong(request.get("tripId"));

        if (routeId == null || boardingStopId == null || destinationStopId == null) {
            return error(HttpStatus.BAD_REQUEST, "routeId, boardingStopId, and destinationStopId are required.");
        }

        Long sessionId = journeyRepository.findActiveSessionId();
        if (tripId == null) {
            List<Map<String, Object>> trips = journeyRepository.findTripsForRoute(routeId);
            if (!trips.isEmpty()) {
                tripId = toLong(trips.get(0).get("tripId"));
            }
        }

        if (tripId != null) {
            try {
                Long journeyId = journeyRepository.recordSearchIntent(sessionId, tripId, boardingStopId, destinationStopId, count);
                return ResponseEntity.ok(Map.of(
                        "status", "SUCCESS",
                        "journeyId", journeyId,
                        "message", "Search intent recorded in common demand."
                ));
            } catch (Exception e) {
                // If duplicates or constraints, gracefully acknowledge
                return ResponseEntity.ok(Map.of("status", "IGNORED", "message", e.getMessage()));
            }
        }

        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Search noted"));
    }

    // Update journey status
    @PatchMapping("/{journeyId}/status")
    public ResponseEntity<Map<String, Object>> updateJourneyStatus(
            @PathVariable Long journeyId,
            @RequestBody Map<String, Object> request) {

        if (request == null || request.get("status") == null) {
            return error(
                    HttpStatus.BAD_REQUEST,
                    "Please provide a journey status."
            );
        }

        String status = request.get("status")
                .toString()
                .trim()
                .toUpperCase();

        List<String> allowedStatuses = List.of(
                "REGISTERED",
                "BOARDED",
                "COMPLETED",
                "CANCELLED"
        );

        if (!allowedStatuses.contains(status)) {
            return error(
                    HttpStatus.BAD_REQUEST,
                    "Invalid journey status."
            );
        }

        Map<String, Object> existingJourney = journeyRepository.findJourneyById(journeyId);
        if (existingJourney == null) {
            return error(
                    HttpStatus.NOT_FOUND,
                    "Journey not found."
            );
        }

        int updatedRows = journeyRepository.updateJourneyStatus(journeyId, status);
        if (updatedRows == 0) {
            return error(
                    HttpStatus.NOT_FOUND,
                    "Journey status could not be updated."
            );
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("message", "Journey status updated successfully.");
        resp.put("journeyId", journeyId);
        resp.put("journeyStatus", status);
        resp.put("status", status);
        resp.put("boardingConfirmedAt", java.time.Instant.now().toString());
        resp.put("alightingConfirmedAt", java.time.Instant.now().toString());

        return ResponseEntity.ok(resp);
    }

    // Safely convert a value to Long
    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    // Safely convert a value to Integer
    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    // Standard error response
    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status,
            String message) {
        return ResponseEntity.status(status).body(
                Map.of("message", message)
        );
    }
}
