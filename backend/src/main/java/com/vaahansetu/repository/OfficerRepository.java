package com.vaahansetu.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Database queries supporting the officer console REST endpoints.
 *
 * All reads are non-destructive. Write operations (save selection, persist
 * allocation results, approve allocation) are explicitly documented.
 */
@Repository
public class OfficerRepository {

    private final JdbcTemplate jdbcTemplate;

    public OfficerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ----------------------------------------------------------------
    // Active planning session
    // ----------------------------------------------------------------

    public Map<String, Object> findActiveSession() {
        String sql = """
                SELECT session_id  AS "sessionId",
                       session_name AS "sessionName",
                       started_at  AS "startedAt",
                       status,
                       simulation_elapsed_minutes AS "simulationElapsedMinutes"
                FROM planning_sessions
                WHERE status = 'ACTIVE'
                ORDER BY started_at DESC
                LIMIT 1
                """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Long findActiveSessionId() {
        Map<String, Object> session = findActiveSession();
        if (session == null) return null;
        Object id = session.get("sessionId");
        return id == null ? null : ((Number) id).longValue();
    }

    // ----------------------------------------------------------------
    // Buses
    // ----------------------------------------------------------------

    public List<Map<String, Object>> findAllBuses() {
        String sql = """
                SELECT b.bus_id                AS "busId",
                       b.registration_number   AS "registrationNumber",
                       b.capacity,
                       b.status::text          AS "status",
                       b.current_location      AS "currentLocation",
                       b.rest_duration_minutes AS "restDurationMinutes",
                       b.rest_start::text      AS "restStart",
                       b.rest_end::text        AS "restEnd",
                       b.notes,
                       b.created_at            AS "createdAt"
                FROM buses b
                ORDER BY b.bus_id
                """;
        return jdbcTemplate.queryForList(sql);
    }

    public List<Map<String, Object>> findSelectedBusesBySession(long sessionId) {
        String sql = """
                SELECT b.bus_id                AS "busId",
                       b.registration_number   AS "registrationNumber",
                       b.capacity,
                       b.status::text          AS "status",
                       b.current_location      AS "currentLocation",
                       b.rest_duration_minutes AS "restDurationMinutes",
                       b.rest_start::text      AS "restStart",
                       b.rest_end::text        AS "restEnd",
                       b.notes
                FROM buses b
                JOIN session_buses sb ON sb.bus_id = b.bus_id
                WHERE sb.session_id = ?
                ORDER BY b.bus_id
                """;
        return jdbcTemplate.queryForList(sql, sessionId);
    }

    // ----------------------------------------------------------------
    // Routes
    // ----------------------------------------------------------------

    public List<Map<String, Object>> findAllActiveRoutes() {
        String sql = """
                SELECT route_id                   AS "routeId",
                       route_code                 AS "routeCode",
                       route_name                 AS "routeName",
                       origin_name                AS "originName",
                       destination_name           AS "destinationName",
                       estimated_duration_minutes AS "estimatedDurationMinutes",
                       is_active                  AS "isActive"
                FROM routes
                WHERE is_active = TRUE
                ORDER BY route_id
                """;
        return jdbcTemplate.queryForList(sql);
    }

    public List<Map<String, Object>> findStopsByRouteId(long routeId) {
        String sql = """
                SELECT s.stop_id    AS "stopId",
                       s.stop_name  AS "stopName",
                       rs.stop_sequence AS "stopSequence",
                       rs.scheduled_offset_minutes AS "scheduledOffsetMinutes"
                FROM stops s
                JOIN route_stops rs ON rs.stop_id = s.stop_id
                WHERE rs.route_id = ?
                  AND s.is_active = TRUE
                ORDER BY rs.stop_sequence
                """;
        return jdbcTemplate.queryForList(sql, routeId);
    }

    // ----------------------------------------------------------------
    // Scheduled Trips
    // ----------------------------------------------------------------

    public List<Map<String, Object>> findScheduledTrips(LocalDate serviceDate) {
        String sql = """
                SELECT t.trip_id              AS "tripId",
                       t.route_id             AS "routeId",
                       r.route_code           AS "routeCode",
                       r.route_name           AS "routeName",
                       t.service_date         AS "serviceDate",
                       t.departure_time       AS "departureTime",
                       t.expected_arrival_time AS "expectedArrivalTime",
                       t.status::text         AS "status",
                       ta.bus_id              AS "currentBusId",
                       b.registration_number  AS "currentBusRegistration",
                       b.capacity             AS "currentBusCapacity",
                       b.rest_duration_minutes AS "currentBusRestDurationMinutes",
                       b.rest_start::text     AS "currentBusRestStart",
                       b.rest_end::text       AS "currentBusRestEnd"
                FROM scheduled_trips t
                JOIN routes r ON r.route_id = t.route_id
                LEFT JOIN trip_assignments ta
                    ON ta.trip_id = t.trip_id AND ta.status = 'ACTIVE'
                LEFT JOIN buses b ON b.bus_id = ta.bus_id
                WHERE t.service_date = ?
                  AND t.status = 'SCHEDULED'
                ORDER BY t.departure_time, t.trip_id
                """;
        return jdbcTemplate.queryForList(sql, serviceDate);
    }

    // ----------------------------------------------------------------
    // Segment demand
    // ----------------------------------------------------------------

    /**
     * Returns per-segment demand for a specific trip.
     * Counts REGISTERED and BOARDED journeys; excludes UNCONFIRMED,
     * EXPIRED, CANCELLED, COMPLETED (per allocation engine behaviour).
     */
    public List<Map<String, Object>> findSegmentDemandForTrip(long tripId, long sessionId) {
        String sql = """
                SELECT
                    t.trip_id                  AS "tripId",
                    t.route_id                 AS "routeId",
                    r.route_code               AS "routeCode",
                    TO_CHAR(t.service_date, 'YYYY-MM-DD') || ' ' || TO_CHAR(t.departure_time, 'HH24:MI:SS') AS "departureLabel",
                    rs.stop_sequence           AS "segmentIndex",
                    s_start.stop_id            AS "startStopId",
                    s_start.stop_name          AS "startStopName",
                    s_end.stop_id              AS "endStopId",
                    s_end.stop_name            AS "endStopName",
                    COALESCE(SUM(
                        CASE WHEN pj.journey_status IN ('REGISTERED','BOARDED','COMPLETED')
                             THEN pj.passenger_count ELSE 0 END
                    ), 0)                      AS "registeredDemand",
                    COALESCE(SUM(sda.demand_delta), 0) AS "simulatedAdjustment",
                    COALESCE(b.registration_number, 'UNASSIGNED') AS "currentBus",
                    COALESCE(b.capacity, 0)    AS "currentCapacity"
                FROM route_stops rs
                JOIN routes r ON r.route_id = rs.route_id
                JOIN stops s_start ON s_start.stop_id = rs.stop_id
                JOIN route_stops rs2
                    ON rs2.route_id = rs.route_id
                   AND rs2.stop_sequence = rs.stop_sequence + 1
                JOIN stops s_end ON s_end.stop_id = rs2.stop_id
                JOIN scheduled_trips t ON t.route_id = rs.route_id AND t.trip_id = ?
                LEFT JOIN trip_assignments ta ON ta.trip_id = t.trip_id AND ta.status = 'ACTIVE'
                LEFT JOIN buses b ON b.bus_id = ta.bus_id
                LEFT JOIN passenger_journeys pj
                    ON pj.trip_id = t.trip_id
                   AND pj.session_id = ?
                   AND pj.boarding_stop_id IN (
                       SELECT rs3.stop_id FROM route_stops rs3
                       WHERE rs3.route_id = rs.route_id AND rs3.stop_sequence <= rs.stop_sequence
                   )
                   AND pj.destination_stop_id IN (
                       SELECT rs4.stop_id FROM route_stops rs4
                       WHERE rs4.route_id = rs.route_id AND rs4.stop_sequence >= rs.stop_sequence + 1
                   )
                LEFT JOIN segment_demand_adjustments sda
                    ON sda.session_id = ?
                   AND sda.route_id = rs.route_id
                   AND sda.segment_start_stop_id = rs.stop_id
                   AND sda.segment_end_stop_id = rs2.stop_id
                WHERE rs.route_id = (SELECT route_id FROM scheduled_trips WHERE trip_id = ?)
                GROUP BY rs.stop_sequence, s_start.stop_id, s_start.stop_name,
                         s_end.stop_id, s_end.stop_name, t.trip_id, t.route_id, r.route_code,
                         t.service_date, t.departure_time, b.registration_number, b.capacity
                ORDER BY rs.stop_sequence
                """;
        return jdbcTemplate.queryForList(sql, tripId, sessionId, sessionId, tripId);
    }

    // ----------------------------------------------------------------
    // Session selection (save bus/route selection)
    // ----------------------------------------------------------------

    public void saveSessionSelection(long sessionId, List<Long> routeIds, List<Long> busIds) {
        // Clear existing selections
        jdbcTemplate.update("DELETE FROM session_routes WHERE session_id = ?", sessionId);
        jdbcTemplate.update("DELETE FROM session_buses  WHERE session_id = ?", sessionId);
        // Insert new selections
        for (Long routeId : routeIds) {
            jdbcTemplate.update(
                    "INSERT INTO session_routes (session_id, route_id) VALUES (?,?) ON CONFLICT DO NOTHING",
                    sessionId, routeId);
        }
        for (Long busId : busIds) {
            jdbcTemplate.update(
                    "INSERT INTO session_buses (session_id, bus_id) VALUES (?,?) ON CONFLICT DO NOTHING",
                    sessionId, busId);
        }
    }

    // ----------------------------------------------------------------
    // Allocation retrieval
    // ----------------------------------------------------------------

    public Map<String, Object> findAllocationRunById(long runId) {
        String sql = """
                SELECT allocation_run_id      AS "allocationRunId",
                       session_id             AS "sessionId",
                       created_at             AS "createdAt",
                       status::text           AS "status",
                       is_simulated           AS "isSimulated",
                       total_demand           AS "totalDemand",
                       total_accommodated     AS "totalAccommodated",
                       total_unmet_demand     AS "totalUnmetDemand",
                       total_unused_capacity  AS "totalUnusedCapacity",
                       notes
                FROM allocation_runs
                WHERE allocation_run_id = ?
                """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, runId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> findAllocationRunsBySession(long sessionId) {
        String sql = """
                SELECT allocation_run_id      AS "allocationRunId",
                       status::text           AS "status",
                       is_simulated           AS "isSimulated",
                       total_demand           AS "totalDemand",
                       total_accommodated     AS "totalAccommodated",
                       total_unmet_demand     AS "totalUnmetDemand",
                       total_unused_capacity  AS "totalUnusedCapacity",
                       created_at             AS "createdAt",
                       notes
                FROM allocation_runs
                WHERE session_id = ?
                ORDER BY created_at DESC
                """;
        return jdbcTemplate.queryForList(sql, sessionId);
    }

    public List<Map<String, Object>> findAllocationAssignments(long runId) {
        String sql = """
                SELECT aa.allocation_assignment_id AS "allocationAssignmentId",
                       aa.trip_id                  AS "tripId",
                       r.route_code                AS "routeCode",
                       t.departure_time            AS "departureTime",
                       aa.previous_bus_id          AS "previousBusId",
                       b_prev.registration_number  AS "previousBusRegistration",
                       aa.proposed_bus_id          AS "proposedBusId",
                       b_next.registration_number  AS "proposedBusRegistration",
                       aa.assignment_type::text    AS "assignmentType",
                       aa.reason
                FROM allocation_assignments aa
                JOIN scheduled_trips t ON t.trip_id = aa.trip_id
                JOIN routes r ON r.route_id = t.route_id
                LEFT JOIN buses b_prev ON b_prev.bus_id = aa.previous_bus_id
                LEFT JOIN buses b_next ON b_next.bus_id = aa.proposed_bus_id
                WHERE aa.allocation_run_id = ?
                ORDER BY t.departure_time, aa.trip_id
                """;
        return jdbcTemplate.queryForList(sql, runId);
    }

    public List<Map<String, Object>> findSegmentResults(long runId) {
        String sql = """
                SELECT asr.allocation_segment_result_id AS "id",
                       r.route_code                     AS "routeCode",
                       s_start.stop_name                AS "startStopName",
                       s_end.stop_name                  AS "endStopName",
                       asr.demand,
                       asr.allocated_capacity           AS "allocatedCapacity",
                       asr.accommodated,
                       asr.unmet_demand                 AS "unmetDemand",
                       asr.unused_capacity              AS "unusedCapacity"
                FROM allocation_segment_results asr
                JOIN routes r ON r.route_id = asr.route_id
                JOIN stops s_start ON s_start.stop_id = asr.segment_start_stop_id
                JOIN stops s_end   ON s_end.stop_id   = asr.segment_end_stop_id
                WHERE asr.allocation_run_id = ?
                ORDER BY r.route_id, asr.allocation_segment_result_id
                """;
        return jdbcTemplate.queryForList(sql, runId);
    }

    // ----------------------------------------------------------------
    // Allocation approval (transactional update of trip_assignments)
    // ----------------------------------------------------------------

    /**
     * Approves an allocation run.
     * <ol>
     *   <li>Marks the run as APPROVED.
     *   <li>For each REASSIGNED assignment: marks current trip_assignment
     *       as REPLACED and inserts a new ACTIVE assignment.
     * </ol>
     * This is performed as a series of JDBC updates; the caller is
     * responsible for wrapping them in a transaction (@Transactional).
     */
    public void approveAllocationRun(long runId, Long approvedByUserId) {
        // Mark the run as APPROVED
        jdbcTemplate.update(
                "UPDATE allocation_runs SET status = 'APPROVED'::allocation_status, created_by = COALESCE(created_by, ?) " +
                "WHERE allocation_run_id = ? AND status = 'PROPOSED'::allocation_status",
                approvedByUserId, runId);

        // Load REASSIGNED assignments from this run
        String sql = """
                SELECT aa.trip_id, aa.proposed_bus_id
                FROM allocation_assignments aa
                WHERE aa.allocation_run_id = ?
                  AND aa.assignment_type = 'REASSIGNED'::allocation_assignment_type
                  AND aa.proposed_bus_id IS NOT NULL
                """;
        List<Map<String, Object>> reassigned = jdbcTemplate.queryForList(sql, runId);

        for (Map<String, Object> row : reassigned) {
            long tripId = ((Number) row.get("trip_id")).longValue();
            long proposedBusId = ((Number) row.get("proposed_bus_id")).longValue();

            // Mark existing ACTIVE assignment(s) as REPLACED
            jdbcTemplate.update(
                    "UPDATE trip_assignments SET status = 'REPLACED'::assignment_status " +
                    "WHERE trip_id = ? AND status = 'ACTIVE'::assignment_status",
                    tripId);

            // Insert new ACTIVE assignment
            jdbcTemplate.update(
                    "INSERT INTO trip_assignments (trip_id, bus_id, assigned_at, assigned_by, status) " +
                    "VALUES (?, ?, NOW(), ?, 'ACTIVE'::assignment_status)",
                    tripId, proposedBusId, approvedByUserId);
        }
    }

    public Long findRouteIdForTrip(long tripId) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT route_id FROM scheduled_trips WHERE trip_id = ?", Long.class, tripId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    // ----------------------------------------------------------------
    // Simulation step
    // ----------------------------------------------------------------

    /**
     * Insert segment demand adjustments for simulation.
     * Returns the new total elapsed simulation minutes for this session.
     */
    public int simulateStep(long sessionId, long routeId,
                             List<Long> startStopIds, List<Long> endStopIds,
                             List<Integer> deltas) {
        for (int i = 0; i < startStopIds.size(); i++) {
            jdbcTemplate.update(
                    "INSERT INTO segment_demand_adjustments " +
                    "(session_id, route_id, segment_start_stop_id, segment_end_stop_id, demand_delta, is_simulated) " +
                    "VALUES (?, ?, ?, ?, ?, TRUE)",
                    sessionId, routeId, startStopIds.get(i), endStopIds.get(i), deltas.get(i));
        }
        // Advance simulation clock by 2 minutes
        jdbcTemplate.update(
                "UPDATE planning_sessions SET simulation_elapsed_minutes = simulation_elapsed_minutes + 2 " +
                "WHERE session_id = ?",
                sessionId);
        // Return new clock value
        Integer elapsed = jdbcTemplate.queryForObject(
                "SELECT simulation_elapsed_minutes FROM planning_sessions WHERE session_id = ?",
                Integer.class, sessionId);
        return elapsed == null ? 0 : elapsed;
    }

    public int advanceSessionClock(long sessionId, int minutes) {
        jdbcTemplate.update(
                "UPDATE planning_sessions SET simulation_elapsed_minutes = simulation_elapsed_minutes + ? " +
                "WHERE session_id = ?",
                minutes, sessionId);
        Integer elapsed = jdbcTemplate.queryForObject(
                "SELECT simulation_elapsed_minutes FROM planning_sessions WHERE session_id = ?",
                Integer.class, sessionId);
        return elapsed == null ? 0 : elapsed;
    }

    // ----------------------------------------------------------------
    // Allocation run persistence helpers
    // ----------------------------------------------------------------

    public long insertAllocationRun(long sessionId, String status, boolean isSimulated,
                                     int demand, int accommodated, int unmet, int unused,
                                     Long createdBy, String notes) {
        String sql = """
                INSERT INTO allocation_runs
                    (session_id, status, is_simulated, total_demand, total_accommodated,
                     total_unmet_demand, total_unused_capacity, created_by, notes)
                VALUES (?, ?::allocation_status, ?, ?, ?, ?, ?, ?, ?)
                RETURNING allocation_run_id
                """;
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                sessionId, status, isSimulated, demand, accommodated, unmet, unused,
                createdBy, notes);
        return id == null ? -1 : id;
    }

    public void insertAllocationAssignment(long runId, long tripId,
                                            Long previousBusId, Long proposedBusId,
                                            String type, String reason) {
        jdbcTemplate.update(
                "INSERT INTO allocation_assignments " +
                "(allocation_run_id, trip_id, previous_bus_id, proposed_bus_id, assignment_type, reason) " +
                "VALUES (?, ?, ?, ?, ?::allocation_assignment_type, ?)" +
                " ON CONFLICT (allocation_run_id, trip_id) DO NOTHING",
                runId, tripId, previousBusId, proposedBusId, type, reason);
    }

    public void insertSegmentResult(long runId, long routeId,
                                     long startStopId, long endStopId,
                                     int demand, int capacity,
                                     int accommodated, int unmet, int unused) {
        jdbcTemplate.update(
                "INSERT INTO allocation_segment_results " +
                "(allocation_run_id, route_id, segment_start_stop_id, segment_end_stop_id, " +
                " demand, allocated_capacity, accommodated, unmet_demand, unused_capacity) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                runId, routeId, startStopId, endStopId,
                demand, capacity, accommodated, unmet, unused);
    }

    public void deleteExistingAllocationRuns(long sessionId, String notePrefix) {
        // Cascades to assignments and segment results
        jdbcTemplate.update(
                "DELETE FROM allocation_runs WHERE session_id = ? AND notes LIKE ?",
                sessionId, notePrefix + "%");
    }

    // ----------------------------------------------------------------
    // Allocation engine data loading helpers
    // ----------------------------------------------------------------

    /**
     * Load all data needed by the allocation engine for the given session.
     * Returns the full fleet (not just session-selected buses) ordered by bus_id.
     */
    public List<Map<String, Object>> findEngineRoutes(long sessionId) {
        String sql = """
                SELECT r.route_id   AS "routeId",
                       r.route_code AS "routeCode",
                       r.route_name AS "routeName"
                FROM routes r
                JOIN session_routes sr ON sr.route_id = r.route_id AND sr.session_id = ?
                WHERE r.is_active = TRUE
                ORDER BY r.route_id
                """;
        return jdbcTemplate.queryForList(sql, sessionId);
    }

    public List<Map<String, Object>> findEngineStopsForRoute(long routeId) {
        String sql = """
                SELECT rs.stop_id AS "stopId", rs.stop_sequence AS "stopSequence"
                FROM route_stops rs
                JOIN stops s ON s.stop_id = rs.stop_id
                WHERE rs.route_id = ? AND s.is_active = TRUE
                ORDER BY rs.stop_sequence
                """;
        return jdbcTemplate.queryForList(sql, routeId);
    }

    public List<Map<String, Object>> findEngineBuses(long sessionId) {
        String sql = """
                SELECT b.bus_id                AS "busId",
                       b.registration_number   AS "registrationNumber",
                       b.capacity,
                       b.status::text          AS "status",
                       b.current_location      AS "currentLocation",
                       b.rest_duration_minutes AS "restDurationMinutes",
                       b.rest_start            AS "restStart",
                       b.rest_end              AS "restEnd",
                       (sb.bus_id IS NOT NULL) AS "selectedForSession"
                FROM buses b
                LEFT JOIN session_buses sb ON sb.bus_id = b.bus_id AND sb.session_id = ?
                ORDER BY b.bus_id
                """;
        return jdbcTemplate.queryForList(sql, sessionId);
    }

    public List<Map<String, Object>> findEngineTrips(long sessionId, LocalDate serviceDate,
                                                       LocalDateTime fromTime) {
        String sql = """
                SELECT t.trip_id               AS "tripId",
                       t.route_id              AS "routeId",
                       t.departure_time        AS "departureTime",
                       t.expected_arrival_time AS "expectedArrivalTime",
                       ta.bus_id               AS "currentBusId",
                       COALESCE(b.capacity, 0) AS "currentBusCapacity"
                FROM scheduled_trips t
                JOIN session_routes sr ON sr.route_id = t.route_id AND sr.session_id = ?
                LEFT JOIN trip_assignments ta
                    ON ta.trip_id = t.trip_id AND ta.status = 'ACTIVE'
                LEFT JOIN buses b ON b.bus_id = ta.bus_id
                WHERE t.status = 'SCHEDULED'
                  AND t.service_date = ?
                  AND t.departure_time >= ?
                ORDER BY t.departure_time, t.trip_id
                """;
        return jdbcTemplate.queryForList(sql, sessionId, serviceDate, fromTime);
    }

    public List<Map<String, Object>> findEngineJourneys(long sessionId, LocalDate serviceDate,
                                                          LocalDateTime fromTime) {
        String sql = """
                SELECT pj.trip_id              AS "tripId",
                       pj.boarding_stop_id     AS "boardingStopId",
                       pj.destination_stop_id  AS "destinationStopId",
                       pj.passenger_count      AS "passengerCount"
                FROM passenger_journeys pj
                JOIN scheduled_trips t ON t.trip_id = pj.trip_id
                JOIN session_routes sr ON sr.route_id = t.route_id AND sr.session_id = pj.session_id
                WHERE pj.session_id = ?
                  AND pj.journey_status IN ('REGISTERED','BOARDED','COMPLETED')
                  AND t.status = 'SCHEDULED'
                  AND t.service_date = ?
                  AND t.departure_time >= ?
                """;
        return jdbcTemplate.queryForList(sql, sessionId, serviceDate, fromTime);
    }

    public List<Map<String, Object>> findEngineCommitments(LocalDate serviceDate) {
        String sql = """
                SELECT ta.bus_id               AS "busId",
                       t.trip_id               AS "tripId",
                       r.route_code            AS "routeCode",
                       t.departure_time        AS "departureTime",
                       t.expected_arrival_time AS "expectedArrivalTime"
                FROM trip_assignments ta
                JOIN scheduled_trips t ON t.trip_id = ta.trip_id
                JOIN routes r ON r.route_id = t.route_id
                WHERE ta.status = 'ACTIVE'
                  AND t.status <> 'CANCELLED'
                  AND t.departure_time >= ?
                  AND t.departure_time < ?
                """;
        LocalDateTime start = serviceDate.minusDays(1).atStartOfDay();
        LocalDateTime end   = serviceDate.plusDays(2).atStartOfDay();
        return jdbcTemplate.queryForList(sql, start, end);
    }

    public List<Map<String, Object>> findSimulationAdjustments(long sessionId) {
        String sql = """
                SELECT session_id              AS "sessionId",
                       route_id                AS "routeId",
                       segment_start_stop_id   AS "startStopId",
                       segment_end_stop_id     AS "endStopId",
                       SUM(demand_delta)       AS "totalDelta"
                FROM segment_demand_adjustments
                WHERE session_id = ?
                GROUP BY session_id, route_id, segment_start_stop_id, segment_end_stop_id
                """;
        return jdbcTemplate.queryForList(sql, sessionId);
    }
}
