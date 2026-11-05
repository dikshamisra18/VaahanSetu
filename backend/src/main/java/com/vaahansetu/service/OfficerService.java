package com.vaahansetu.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vaahansetu.repository.OfficerRepository;

/**
 * Officer service: wraps the in-memory allocation engine with
 * database-backed I/O so the officer REST endpoints can call it directly.
 *
 * <p>The allocation engine lives in the same backend module under
 * {@code com.vaahansetu.allocation}.
 */
@Service
public class OfficerService {

    private static final String AUTO_NOTE = "AUTO";

    private final OfficerRepository repo;

    public OfficerService(OfficerRepository repo) {
        this.repo = repo;
    }

    // ----------------------------------------------------------------
    // Active session
    // ----------------------------------------------------------------

    public Map<String, Object> getActiveSession() {
        Map<String, Object> session = repo.findActiveSession();
        if (session == null) {
            return Map.of("message", "No active planning session found.");
        }
        // Add selected bus/route ids
        long sessionId = toLong(session.get("sessionId"));
        session = new LinkedHashMap<>(session);
        ((LinkedHashMap<String, Object>) session).put("selectedRouteIds",
                getSelectedRouteIds(sessionId));
        ((LinkedHashMap<String, Object>) session).put("selectedBusIds",
                getSelectedBusIds(sessionId));
        return session;
    }

    private List<Long> getSelectedRouteIds(long sessionId) {
        List<Map<String, Object>> rows = repo.findEngineRoutes(sessionId);
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> r : rows) ids.add(toLong(r.get("routeId")));
        return ids;
    }

    private List<Long> getSelectedBusIds(long sessionId) {
        List<Map<String, Object>> rows = repo.findSelectedBusesBySession(sessionId);
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> r : rows) ids.add(toLong(r.get("busId")));
        return ids;
    }

    // ----------------------------------------------------------------
    // Buses
    // ----------------------------------------------------------------

    public List<Map<String, Object>> getAllBuses() {
        return repo.findAllBuses();
    }

    // ----------------------------------------------------------------
    // Routes
    // ----------------------------------------------------------------

    public List<Map<String, Object>> getAllRoutes() {
        List<Map<String, Object>> routes = repo.findAllActiveRoutes();
        for (Map<String, Object> route : routes) {
            long routeId = toLong(route.get("routeId"));
            ((Map<String, Object>) route).put("stops", repo.findStopsByRouteId(routeId));
        }
        return routes;
    }

    // ----------------------------------------------------------------
    // Scheduled trips
    // ----------------------------------------------------------------

    public List<Map<String, Object>> getTrips(LocalDate serviceDate) {
        return repo.findScheduledTrips(serviceDate == null ? repo.findActiveSessionDate() : serviceDate);
    }

    // ----------------------------------------------------------------
    // Segment demand for a trip
    // ----------------------------------------------------------------

    public List<Map<String, Object>> getSegmentDemand(long tripId) {
        Long sessionId = repo.findActiveSessionId();
        if (sessionId == null) return List.of();

        List<Map<String, Object>> rawRows = repo.findSegmentDemandForTrip(tripId, sessionId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rawRows) {
            int registered = toInt(row.get("registeredDemand"));
            int simulated  = toInt(row.get("simulatedAdjustment"));
            int effective  = Math.max(0, registered + simulated);

            Map<String, Object> enriched = new LinkedHashMap<>(row);
            enriched.put("effectiveDemand", effective);
            enriched.put("baseDemand", registered);
            enriched.put("adjustedDemand", effective);
            result.add(enriched);
        }
        return result;
    }

    // ----------------------------------------------------------------
    // Session selection
    // ----------------------------------------------------------------

    @Transactional
    public void saveSelection(long sessionId, List<Long> routeIds, List<Long> busIds) {
        repo.saveSessionSelection(sessionId, routeIds, busIds);
    }

    // ----------------------------------------------------------------
    // Allocation run + persistence
    // ----------------------------------------------------------------

    /**
     * Run the allocation engine for the active planning session and
     * persist the baseline + proposed allocation runs.
     *
     * @param allocationTime the reference time for constraint checking
     * @return a map containing both runs and the trip outcomes
     */
    @Transactional
    public Map<String, Object> runAllocation(LocalDateTime allocationTime) {
        return runAllocation(null, null, allocationTime);
    }

    @Transactional
    public Map<String, Object> runAllocation(Long targetSessionId, LocalDate targetServiceDate, LocalDateTime targetAllocationTime) {
        Long sessionId = targetSessionId != null ? targetSessionId : repo.findActiveSessionId();
        if (sessionId == null) {
            throw new IllegalStateException("No active planning session.");
        }

        LocalDate serviceDate = targetServiceDate != null
                ? targetServiceDate
                : (targetAllocationTime != null ? targetAllocationTime.toLocalDate() : repo.findActiveSessionDate());

        LocalDateTime allocationTime = targetAllocationTime != null
                ? targetAllocationTime
                : serviceDate.atStartOfDay();

        // Load allocation engine input from DB
        com.vaahansetu.allocation.AllocationModels.Input input =
                buildEngineInput(sessionId, serviceDate, allocationTime);

        // Run the engine
        com.vaahansetu.allocation.AllocationModels.Result result =
                com.vaahansetu.allocation.AllocationEngine.allocate(input);

        // Delete previous auto-generated runs for this session
        repo.deleteExistingAllocationRuns(sessionId, AUTO_NOTE);

        // Persist baseline run
        String baselineNote = AUTO_NOTE + " baseline - current assignments at " + allocationTime;
        long baselineRunId = repo.insertAllocationRun(sessionId, "APPROVED", false,
                result.beforeTotal().demand(), result.beforeTotal().accommodated(),
                result.beforeTotal().unmet(), result.beforeTotal().unused(),
                null, baselineNote);

        // Persist proposed run
        String proposedNote = AUTO_NOTE + " proposed - greedy allocation at " + allocationTime;
        long proposedRunId = repo.insertAllocationRun(sessionId, "PROPOSED", false,
                result.afterTotal().demand(), result.afterTotal().accommodated(),
                result.afterTotal().unmet(), result.afterTotal().unused(),
                null, proposedNote);

        // Persist assignments in fast batches
        repo.insertAllocationAssignmentsBatch(baselineRunId, result.outcomes(), true);
        repo.insertAllocationAssignmentsBatch(proposedRunId, result.outcomes(), false);

        // Persist segment results in fast batches
        repo.insertSegmentResultsBatch(baselineRunId, result.beforeSegments());
        repo.insertSegmentResultsBatch(proposedRunId, result.afterSegments());

        // Build response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sessionId", sessionId);
        response.put("allocationTime", allocationTime.toString());
        response.put("baselineRunId", baselineRunId);
        response.put("proposedRunId", proposedRunId);
        response.put("allocationRunId", proposedRunId);
        response.put("status", "PROPOSED");
        response.put("baselineUnmetDemand", result.beforeTotal().unmet());
        response.put("proposedUnmetDemand", result.afterTotal().unmet());
        response.put("beforeMetrics", metricsMap(result.beforeTotal()));
        response.put("afterMetrics",  metricsMap(result.afterTotal()));
        response.put("baselineMetrics", metricsMap(result.beforeTotal()));
        response.put("proposedMetrics", metricsMap(result.afterTotal()));
        response.put("busesConsidered", result.busesConsidered());
        response.put("busesAssigned",   result.busesAssigned());
        response.put("busesLeftUnassigned", result.busesLeftUnassigned());
        response.put("tripOutcomes", outcomesToMaps(result.outcomes()));
        response.put("assignments", repo.findAllocationAssignments(proposedRunId));
        response.put("baselineSegments", repo.findSegmentResults(baselineRunId));
        response.put("proposedSegments", repo.findSegmentResults(proposedRunId));
        return response;
    }

    // ----------------------------------------------------------------
    // Allocation retrieval
    // ----------------------------------------------------------------

    public Map<String, Object> getAllocationRun(long runId) {
        Map<String, Object> run = repo.findAllocationRunById(runId);
        if (run == null) return null;

        Map<String, Object> result = new LinkedHashMap<>(run);
        result.put("assignments", repo.findAllocationAssignments(runId));
        result.put("segmentResults", repo.findSegmentResults(runId));
        return result;
    }

    public List<Map<String, Object>> getAllocationRunsBySession() {
        Long sessionId = repo.findActiveSessionId();
        if (sessionId == null) return List.of();
        return repo.findAllocationRunsBySession(sessionId);
    }

    // ----------------------------------------------------------------
    // Approval
    // ----------------------------------------------------------------

    @Transactional
    public Map<String, Object> approveAllocation(long runId, Long approvedByUserId) {
        Map<String, Object> run = repo.findAllocationRunById(runId);
        if (run == null) {
            throw new IllegalArgumentException("Allocation run " + runId + " not found.");
        }
        if ("APPROVED".equals(run.get("status"))) {
            return Map.of("message", "Allocation run " + runId + " is already approved.",
                    "status", "APPROVED");
        }

        repo.approveAllocationRun(runId, approvedByUserId);
        return Map.of("message", "Allocation run " + runId + " approved successfully.",
                "status", "APPROVED",
                "allocationRunId", runId);
    }

    // ----------------------------------------------------------------
    // Simulation
    // ----------------------------------------------------------------

    @Transactional
    public int advanceClock(int minutes) {
        Long sessionId = repo.findActiveSessionId();
        if (sessionId == null) return 0;
        return repo.advanceSessionClock(sessionId, minutes);
    }

    @Transactional
    public Map<String, Object> simulateStep(long tripId, List<Map<String, Object>> segments) {
        Long sessionId = repo.findActiveSessionId();
        if (sessionId == null) {
            throw new IllegalStateException("No active planning session.");
        }
        Long routeId = repo.findRouteIdForTrip(tripId);
        if (routeId == null) {
            throw new IllegalArgumentException("Trip " + tripId + " not found.");
        }

        List<Long> startStops = new ArrayList<>();
        List<Long> endStops = new ArrayList<>();
        List<Integer> deltas = new ArrayList<>();
        List<Map<String, Object>> affected = new ArrayList<>();

        Random random = new Random();
        for (Map<String, Object> seg : segments) {
            long startId = toLong(seg.get("startStopId"));
            long endId = toLong(seg.get("endStopId"));
            int delta;
            if (seg.containsKey("delta") && seg.get("delta") != null) {
                delta = toInt(seg.get("delta"));
            } else if (seg.containsKey("demandDelta") && seg.get("demandDelta") != null) {
                delta = toInt(seg.get("demandDelta"));
            } else {
                int mag = 5 + random.nextInt(6); // 5..10
                delta = random.nextBoolean() ? mag : -mag;
            }
            startStops.add(startId);
            endStops.add(endId);
            deltas.add(delta);
            affected.add(Map.of("tripId", tripId, "routeId", routeId, "startStopId", startId, "endStopId", endId, "delta", delta));
        }

        int elapsed = repo.simulateStep(sessionId, routeId, startStops, endStops, deltas);
        return Map.of(
                "elapsedMinutes", elapsed,
                "message", "Simulation clock advanced to T+" + String.format("%02d:%02d", elapsed / 60, elapsed % 60) + " (" + segments.size() + " segment adjustments persisted)",
                "affected", affected
        );
    }

    // ----------------------------------------------------------------
    // Engine input builder
    // ----------------------------------------------------------------

    private com.vaahansetu.allocation.AllocationModels.Input buildEngineInput(
            long sessionId, LocalDate serviceDate, LocalDateTime allocationTime) {

        // Routes
        List<Map<String, Object>> routeRows = repo.findEngineRoutes(sessionId);
        Map<Long, List<Long>> allStops = repo.findAllEngineStopsByRoute();
        List<com.vaahansetu.allocation.AllocationModels.Route> routes = new ArrayList<>();
        for (Map<String, Object> rr : routeRows) {
            long routeId = toLong(rr.get("routeId"));
            List<Long> stopIds = allStops.getOrDefault(routeId, List.of());
            routes.add(new com.vaahansetu.allocation.AllocationModels.Route(
                    routeId,
                    (String) rr.get("routeCode"),
                    (String) rr.get("routeName"),
                    stopIds));
        }

        // Buses
        List<Map<String, Object>> busRows = repo.findEngineBuses(sessionId);
        List<com.vaahansetu.allocation.AllocationModels.Bus> buses = new ArrayList<>();
        for (Map<String, Object> br : busRows) {
            LocalTime restStart = br.get("restStart") == null ? null
                    : LocalTime.parse(br.get("restStart").toString());
            LocalTime restEnd = br.get("restEnd") == null ? null
                    : LocalTime.parse(br.get("restEnd").toString());
            buses.add(new com.vaahansetu.allocation.AllocationModels.Bus(
                    toLong(br.get("busId")),
                    (String) br.get("registrationNumber"),
                    toInt(br.get("capacity")),
                    (String) br.get("status"),
                    (String) br.get("currentLocation"),
                    toInt(br.get("restDurationMinutes")),
                    restStart,
                    restEnd,
                    null,
                    null,
                    Boolean.TRUE.equals(br.get("selectedForSession"))));
        }

        // Trips
        LocalDateTime fromTime = serviceDate.atStartOfDay();
        List<Map<String, Object>> tripRows = repo.findEngineTrips(sessionId, serviceDate, fromTime);
        List<com.vaahansetu.allocation.AllocationModels.Trip> trips = new ArrayList<>();
        for (Map<String, Object> tr : tripRows) {
            Long currentBusId = tr.get("currentBusId") == null ? null
                    : toLong(tr.get("currentBusId"));
            trips.add(new com.vaahansetu.allocation.AllocationModels.Trip(
                    toLong(tr.get("tripId")),
                    toLong(tr.get("routeId")),
                    toLocalDateTime(tr.get("departureTime")),
                    toLocalDateTime(tr.get("expectedArrivalTime")),
                    currentBusId,
                    toInt(tr.get("currentBusCapacity"))));
        }

        // Journeys
        List<Map<String, Object>> journeyRows =
                repo.findEngineJourneys(sessionId, serviceDate, fromTime);
        List<com.vaahansetu.allocation.AllocationModels.Journey> journeys = new ArrayList<>();
        for (Map<String, Object> jr : journeyRows) {
            journeys.add(new com.vaahansetu.allocation.AllocationModels.Journey(
                    toLong(jr.get("tripId")),
                    toLong(jr.get("boardingStopId")),
                    toLong(jr.get("destinationStopId")),
                    toInt(jr.get("passengerCount"))));
        }

        // Commitments (excluding trips being reallocated)
        List<Map<String, Object>> commitmentRows = repo.findEngineCommitments(serviceDate);
        Set<Long> reallocatedTripIds = new HashSet<>();
        for (com.vaahansetu.allocation.AllocationModels.Trip t : trips) {
            reallocatedTripIds.add(t.id());
        }
        List<com.vaahansetu.allocation.AllocationModels.Commitment> commitments = new ArrayList<>();
        for (Map<String, Object> cr : commitmentRows) {
            long tripId = toLong(cr.get("tripId"));
            if (reallocatedTripIds.contains(tripId)) continue;
            commitments.add(new com.vaahansetu.allocation.AllocationModels.Commitment(
                    toLong(cr.get("busId")),
                    tripId,
                    (String) cr.get("routeCode"),
                    toLocalDateTime(cr.get("departureTime")),
                    toLocalDateTime(cr.get("expectedArrivalTime"))));
        }

        return new com.vaahansetu.allocation.AllocationModels.Input(
                allocationTime, routes, buses, trips, journeys, commitments);
    }

    // ----------------------------------------------------------------
    // Utilities
    // ----------------------------------------------------------------

    private static long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number n) return n.longValue();
        return Long.parseLong(obj.toString());
    }

    private static int toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number n) return n.intValue();
        return Integer.parseInt(obj.toString());
    }

    private static LocalDateTime toLocalDateTime(Object obj) {
        if (obj == null) return null;
        if (obj instanceof LocalDateTime ldt) return ldt;
        if (obj instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return LocalDateTime.parse(obj.toString(),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]"));
    }

    private static Map<String, Object> metricsMap(
            com.vaahansetu.allocation.AllocationModels.Metrics m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("demand",        m.demand());
        map.put("capacity",      m.capacity());
        map.put("accommodated",  m.accommodated());
        map.put("unmet",         m.unmet());
        map.put("unused",        m.unused());
        return map;
    }

    private static List<Map<String, Object>> outcomesToMaps(
            List<com.vaahansetu.allocation.AllocationModels.TripOutcome> outcomes) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (com.vaahansetu.allocation.AllocationModels.TripOutcome o : outcomes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tripId",        o.tripId());
            m.put("routeId",       o.routeId());
            m.put("previousBusId", o.previousBusId());
            m.put("proposedBusId", o.proposedBusId());
            m.put("type",          o.type());
            m.put("reason",        o.reason());
            list.add(m);
        }
        return list;
    }
}
