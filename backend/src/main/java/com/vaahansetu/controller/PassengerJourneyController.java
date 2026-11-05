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
import com.vaahansetu.service.OfficerService;

@RestController
@RequestMapping({"/api/passenger-journeys", "/api/passenger/journeys"})
@CrossOrigin(origins = "*")
public class PassengerJourneyController {

    private final JourneyRepository journeyRepository;
    private final OfficerService officerService;

    public PassengerJourneyController(JourneyRepository journeyRepository,
                                      OfficerService officerService) {
        this.journeyRepository = journeyRepository;
        this.officerService = officerService;
    }

    // Register a passenger journey
    @PostMapping
    public ResponseEntity<Map<String, Object>> registerJourney(
            @RequestBody Map<String, Object> request,
            @RequestParam(defaultValue = "false") boolean autoAllocate) {

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

        // Build a rich response that includes the journey ID + the assigned bus
        // details + the trip schedule, so the passenger UI can show a complete
        // "Journey Registered" confirmation card without an extra round-trip.
        Map<String, Object> journeyDetails = journeyRepository.findJourneyWithDetailsById(journeyId);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("message", "Your journey has been registered successfully.");
        resp.put("journeyId", journeyId);
        resp.put("tripId", tripId);
        resp.put("journeyStatus", "REGISTERED");
        resp.put("status", "CONFIRMED");
        resp.put("passengerCount", passengerCount);
        if (journeyDetails != null) {
            // Bus details (so the passenger can see which bus to look for)
            resp.put("busId", journeyDetails.get("busId"));
            resp.put("busRegistrationNumber", journeyDetails.get("busRegistrationNumber"));
            resp.put("busCapacity", journeyDetails.get("busCapacity"));
            resp.put("busCurrentLocation", journeyDetails.get("busCurrentLocation"));
            resp.put("busStatus", journeyDetails.get("busStatus"));
            // Trip schedule (so the passenger knows when to be at the stop)
            resp.put("routeCode", journeyDetails.get("routeCode"));
            resp.put("routeName", journeyDetails.get("routeName"));
            resp.put("originName", journeyDetails.get("originName"));
            resp.put("destinationName", journeyDetails.get("destinationName"));
            resp.put("boardingStopName", journeyDetails.get("boardingStopName"));
            resp.put("destinationStopName", journeyDetails.get("destinationStopName"));
            resp.put("serviceDate", journeyDetails.get("serviceDate"));
            resp.put("departureTime", journeyDetails.get("departureTimeFormatted"));
            resp.put("arrivalTime", journeyDetails.get("arrivalTimeFormatted"));
            resp.put("estimatedDurationMinutes", journeyDetails.get("estimatedDurationMinutes"));
            resp.put("passengerReference", journeyDetails.get("passengerReference"));
        }

        // Optional auto-allocation: when the frontend passes ?autoAllocate=true,
        // re-run the allocation engine after the journey is registered so the
        // proposed bus assignments reflect the new demand. The latest allocation
        // run is included in the response so the passenger UI can show whether
        // the bus assignment changed.
        //
        // This is "a little automated" per the user's request -- not a full
        // background job, but on-demand refresh triggered by passenger activity.
        // Officers can still manually trigger allocation via POST /api/officer/allocations.
        if (autoAllocate) {
            try {
                Map<String, Object> allocResult = officerService.runAllocation(
                        java.time.LocalDateTime.now());
                Map<String, Object> allocSummary = new LinkedHashMap<>();
                allocSummary.put("allocationRunId", allocResult.get("proposedRunId"));
                allocSummary.put("baselineUnmetDemand", allocResult.get("baselineUnmetDemand"));
                allocSummary.put("proposedUnmetDemand", allocResult.get("proposedUnmetDemand"));
                allocSummary.put("busesAssigned", allocResult.get("busesAssigned"));
                allocSummary.put("busesLeftUnassigned", allocResult.get("busesLeftUnassigned"));
                resp.put("autoAllocation", allocSummary);
                // Refresh bus details after allocation (the bus may have changed)
                Map<String, Object> refreshed = journeyRepository.findJourneyWithDetailsById(journeyId);
                if (refreshed != null) {
                    resp.put("busId", refreshed.get("busId"));
                    resp.put("busRegistrationNumber", refreshed.get("busRegistrationNumber"));
                    resp.put("busCapacity", refreshed.get("busCapacity"));
                    resp.put("busCurrentLocation", refreshed.get("busCurrentLocation"));
                    resp.put("busStatus", refreshed.get("busStatus"));
                }
            } catch (Exception ex) {
                // Auto-allocation is best-effort; don't fail the journey registration
                Map<String, Object> allocSummary = new LinkedHashMap<>();
                allocSummary.put("error", ex.getMessage());
                allocSummary.put("note", "Journey registered, but auto-allocation refresh failed. An officer can run allocation manually.");
                resp.put("autoAllocation", allocSummary);
            }
        }

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
