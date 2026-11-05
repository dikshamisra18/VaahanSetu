package com.vaahansetu.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JourneyRepository {

    private final JdbcTemplate jdbcTemplate;

    public JourneyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Find today's trips that have an active allocated bus
    public List<Map<String, Object>> findTodaysTrips(Long routeId) {
        return findTodaysTrips(routeId, null);
    }

    public List<Map<String, Object>> findTodaysTrips(Long routeId, LocalDate serviceDate) {
        // Date resolution: explicit param > CURRENT_DATE > latest future trip date > latest trip date.
        // We no longer fall back to planning_sessions.started_at::date because that goes stale
        // when "today" moves past the session start.
        String sql = """
                SELECT DISTINCT
                       st.trip_id AS "tripId",
                       st.route_id AS "routeId",
                       st.service_date AS "serviceDate",
                       TO_CHAR(st.departure_time, 'HH24:MI') AS "departureTimeFormatted",
                       TO_CHAR(st.expected_arrival_time, 'HH24:MI') AS "arrivalTimeFormatted",
                       st.departure_time AS "departureTime",
                       st.expected_arrival_time AS "expectedArrivalTime",
                       st.status,
                       ta.bus_id AS "busId",
                       b.registration_number AS "registrationNumber",
                       b.capacity
                FROM scheduled_trips st
                LEFT JOIN trip_assignments ta
                    ON ta.trip_id = st.trip_id
                LEFT JOIN buses b
                    ON b.bus_id = ta.bus_id
                WHERE st.route_id = ?
                  AND st.service_date = COALESCE(?,
                        CURRENT_DATE,
                        (SELECT MAX(service_date) FROM scheduled_trips WHERE route_id = ? AND service_date >= CURRENT_DATE),
                        (SELECT MAX(service_date) FROM scheduled_trips WHERE route_id = ?),
                        (SELECT MAX(service_date) FROM scheduled_trips))
                  AND ta.status = 'ACTIVE'
                  AND st.status = 'SCHEDULED'
                  AND b.status = 'ACTIVE'
                ORDER BY st.departure_time
                """;

        return jdbcTemplate.queryForList(
                sql,
                routeId,
                serviceDate,
                routeId,
                routeId
        );
    }

    // Find all trips for a route on a specific date (or today if no date given).
    // Returns trips that are SCHEDULED (future departures) so passengers can book.
    // Use findPastTripsForRoute() for historical/completed trips.
    public List<Map<String, Object>> findTripsForRoute(Long routeId) {
        return findTripsForRoute(routeId, null);
    }

    public List<Map<String, Object>> findTripsForRoute(Long routeId, LocalDate serviceDate) {
        // Date resolution priority (in order):
        //   1. Explicit serviceDate parameter (passenger picked a date in the UI)
        //   2. CURRENT_DATE (today -- always preferred over stale session dates)
        //   3. Latest service_date in scheduled_trips for this route (fallback if no trips today)
        //   4. Latest service_date across all trips (last resort)
        //
        // NOTE: we no longer use planning_sessions.started_at::date as a tiebreaker because
        // it goes stale when "today" moves past the session's started_at. CURRENT_DATE is
        // always correct.
        String sql = """
                SELECT st.trip_id AS "tripId",
                       st.route_id AS "routeId",
                       st.service_date AS "serviceDate",
                       TO_CHAR(st.departure_time, 'HH24:MI') AS "departureTimeFormatted",
                       TO_CHAR(st.expected_arrival_time, 'HH24:MI') AS "arrivalTimeFormatted",
                       st.departure_time AS "departureTime",
                       st.expected_arrival_time AS "expectedArrivalTime",
                       st.status,
                       ta.bus_id AS "busId",
                       COALESCE(b.registration_number, 'UNASSIGNED') AS "registrationNumber",
                       COALESCE(b.capacity, 0) AS "capacity",
                       CASE
                           WHEN st.service_date = CURRENT_DATE THEN 'TODAY'
                           WHEN st.service_date > CURRENT_DATE THEN 'FUTURE'
                           ELSE 'PAST'
                       END AS "dateCategory"
                FROM scheduled_trips st
                LEFT JOIN trip_assignments ta
                    ON ta.trip_id = st.trip_id AND ta.status = 'ACTIVE'
                LEFT JOIN buses b
                    ON b.bus_id = ta.bus_id
                WHERE st.route_id = ?
                  AND st.service_date = COALESCE(?,
                        CURRENT_DATE,
                        (SELECT MAX(service_date) FROM scheduled_trips WHERE route_id = ? AND service_date >= CURRENT_DATE),
                        (SELECT MAX(service_date) FROM scheduled_trips WHERE route_id = ?),
                        (SELECT MAX(service_date) FROM scheduled_trips))
                  AND st.status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED')
                ORDER BY st.departure_time
                """;

        return jdbcTemplate.queryForList(
                sql,
                routeId,
                serviceDate,
                routeId,
                routeId
        );
    }

    /**
     * Find past trips for a route (history view).
     * Returns up to {@code limit} most recent past trips, including COMPLETED and CANCELLED ones.
     * Useful for the passenger UI to show "recent departures on this route" history.
     */
    public List<Map<String, Object>> findPastTripsForRoute(Long routeId, int limit) {
        String sql = """
                SELECT st.trip_id AS "tripId",
                       st.route_id AS "routeId",
                       st.service_date AS "serviceDate",
                       TO_CHAR(st.departure_time, 'HH24:MI') AS "departureTimeFormatted",
                       TO_CHAR(st.expected_arrival_time, 'HH24:MI') AS "arrivalTimeFormatted",
                       st.departure_time AS "departureTime",
                       st.expected_arrival_time AS "expectedArrivalTime",
                       st.status,
                       ta.bus_id AS "busId",
                       COALESCE(b.registration_number, 'UNASSIGNED') AS "registrationNumber",
                       COALESCE(b.capacity, 0) AS "capacity",
                       'PAST' AS "dateCategory"
                FROM scheduled_trips st
                LEFT JOIN trip_assignments ta
                    ON ta.trip_id = st.trip_id AND ta.status = 'ACTIVE'
                LEFT JOIN buses b
                    ON b.bus_id = ta.bus_id
                WHERE st.route_id = ?
                  AND st.service_date < CURRENT_DATE
                ORDER BY st.service_date DESC, st.departure_time DESC
                LIMIT ?
                """;

        return jdbcTemplate.queryForList(sql, routeId, limit);
    }

    // Find the currently active planning session
    public Long findActiveSessionId() {

        String sql = """
                SELECT session_id
                FROM planning_sessions
                WHERE status = 'ACTIVE'
                ORDER BY started_at DESC
                LIMIT 1
                """;

        List<Long> sessions =
                jdbcTemplate.queryForList(sql, Long.class);

        return sessions.isEmpty() ? null : sessions.get(0);
    }

    // Get active stops belonging to the selected route in sequence
    public List<Long> findStopOrder(Long routeId) {

        String sql = """
                SELECT rs.stop_id
                FROM route_stops rs
                JOIN stops s
                    ON s.stop_id = rs.stop_id
                WHERE rs.route_id = ?
                  AND s.is_active = TRUE
                ORDER BY rs.stop_sequence
                """;

        return jdbcTemplate.queryForList(
                sql,
                Long.class,
                routeId
        );
    }

    // Save a passenger journey and return its generated ID
    public Long saveJourney(
            Long sessionId,
            Long tripId,
            Long boardingStopId,
            Long destinationStopId,
            Integer passengerCount) {

        String sql = """
                INSERT INTO passenger_journeys (
                    session_id,
                    trip_id,
                    boarding_stop_id,
                    destination_stop_id,
                    passenger_count,
                    journey_status
                )
                VALUES (?, ?, ?, ?, ?, 'REGISTERED')
                RETURNING journey_id
                """;

        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                sessionId,
                tripId,
                boardingStopId,
                destinationStopId,
                passengerCount
        );
    }

    // Check if a duplicate journey was submitted recently within 2 minutes (SRS F9, NFR-S7)
    public boolean isDuplicateJourney(
            Long sessionId,
            Long tripId,
            Long boardingStopId,
            Long destinationStopId,
            Integer passengerCount) {

        String sql = """
                SELECT COUNT(*)
                FROM passenger_journeys
                WHERE session_id = ?
                  AND trip_id = ?
                  AND boarding_stop_id = ?
                  AND destination_stop_id = ?
                  AND passenger_count = ?
                  AND journey_status IN ('REGISTERED', 'BOARDED')
                  AND submitted_at >= NOW() - INTERVAL '2 minutes'
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                sessionId,
                tripId,
                boardingStopId,
                destinationStopId,
                passengerCount
        );

        return count != null && count > 0;
    }

    // Find a saved journey
    public Map<String, Object> findJourneyById(Long journeyId) {

        String sql = """
                SELECT pj.journey_id AS "journeyId",
                       st.route_id AS "routeId",
                       pj.trip_id AS "tripId",
                       pj.boarding_stop_id AS "boardingStopId",
                       pj.destination_stop_id AS "destinationStopId",
                       pj.passenger_count AS "passengerCount",
                       pj.journey_status AS "journeyStatus",
                       pj.boarding_confirmed_at AS "boardingConfirmedAt",
                       pj.alighting_confirmed_at AS "alightingConfirmedAt",
                       pj.submitted_at AS "submittedAt",
                       pj.passenger_reference AS "passengerReference"
                FROM passenger_journeys pj
                JOIN scheduled_trips st
                    ON st.trip_id = pj.trip_id
                WHERE pj.journey_id = ?
                """;

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(sql, journeyId);

        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Full journey details including route, trip, boarding/destination stop names,
     * and the assigned bus (registration, capacity, depot, status).
     * Used in the passenger UI's "journey registered successfully" confirmation card.
     */
    public Map<String, Object> findJourneyWithDetailsById(Long journeyId) {
        String sql = """
                SELECT pj.journey_id AS "journeyId",
                       pj.session_id AS "sessionId",
                       pj.trip_id AS "tripId",
                       st.route_id AS "routeId",
                       r.route_code AS "routeCode",
                       r.route_name AS "routeName",
                       r.origin_name AS "originName",
                       r.destination_name AS "destinationName",
                       r.estimated_duration_minutes AS "estimatedDurationMinutes",
                       pj.boarding_stop_id AS "boardingStopId",
                       bs.stop_name AS "boardingStopName",
                       pj.destination_stop_id AS "destinationStopId",
                       ds.stop_name AS "destinationStopName",
                       pj.passenger_count AS "passengerCount",
                       pj.journey_status AS "journeyStatus",
                       pj.boarding_confirmed_at AS "boardingConfirmedAt",
                       pj.alighting_confirmed_at AS "alightingConfirmedAt",
                       pj.submitted_at AS "submittedAt",
                       pj.passenger_reference AS "passengerReference",
                       st.service_date AS "serviceDate",
                       TO_CHAR(st.departure_time, 'HH24:MI') AS "departureTimeFormatted",
                       TO_CHAR(st.expected_arrival_time, 'HH24:MI') AS "arrivalTimeFormatted",
                       st.status AS "tripStatus",
                       ta.bus_id AS "busId",
                       COALESCE(b.registration_number, 'UNASSIGNED') AS "busRegistrationNumber",
                       COALESCE(b.capacity, 0) AS "busCapacity",
                       COALESCE(b.current_location, 'Unknown') AS "busCurrentLocation",
                       COALESCE(b.status, 'UNKNOWN') AS "busStatus",
                       COALESCE(b.rest_duration_minutes, 0) AS "busRestDurationMinutes"
                FROM passenger_journeys pj
                JOIN scheduled_trips st ON st.trip_id = pj.trip_id
                JOIN routes r ON r.route_id = st.route_id
                JOIN stops bs ON bs.stop_id = pj.boarding_stop_id
                JOIN stops ds ON ds.stop_id = pj.destination_stop_id
                LEFT JOIN trip_assignments ta ON ta.trip_id = pj.trip_id AND ta.status = 'ACTIVE'
                LEFT JOIN buses b ON b.bus_id = ta.bus_id
                WHERE pj.journey_id = ?
                """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, journeyId);
        return results.isEmpty() ? null : results.get(0);
    }

    // Get passenger demand grouped by route for the active session and service date
    public List<Map<String, Object>> findPassengerDemand(Long sessionId) {
        return findPassengerDemand(sessionId, null);
    }

    public List<Map<String, Object>> findPassengerDemand(Long sessionId, LocalDate serviceDate) {
        String sql = """
                SELECT r.route_id AS "routeId",
                       r.route_code AS "routeCode",
                       r.route_name AS "routeName",
                       r.origin_name AS "originName",
                       r.destination_name AS "destinationName",
                       COALESCE(j.confirmed_count, 0) AS "confirmedCount",
                       COALESCE(j.search_count, 0) AS "searchCount",
                       COALESCE(a.adj_count, 0) AS "adjustmentCount",
                       GREATEST(0, COALESCE(j.confirmed_count, 0) + COALESCE(j.search_count, 0) + COALESCE(a.adj_count, 0)) AS "passengerCount",
                       GREATEST(0, COALESCE(j.confirmed_count, 0) + COALESCE(j.search_count, 0) + COALESCE(a.adj_count, 0)) AS "totalDemand"
                FROM routes r
                LEFT JOIN LATERAL (
                    SELECT
                        COALESCE(SUM(CASE WHEN pj.journey_status IN ('REGISTERED', 'BOARDED', 'COMPLETED') THEN pj.passenger_count ELSE 0 END), 0) AS confirmed_count,
                        COALESCE(SUM(CASE WHEN pj.journey_status = 'UNCONFIRMED' THEN pj.passenger_count ELSE 0 END), 0) AS search_count
                    FROM passenger_journeys pj
                    JOIN scheduled_trips st ON st.trip_id = pj.trip_id
                    WHERE st.route_id = r.route_id
                      AND (pj.session_id = ? OR ? IS NULL)
                      AND st.service_date = COALESCE(?,
                            (SELECT started_at::date FROM planning_sessions WHERE status = 'ACTIVE' ORDER BY started_at DESC LIMIT 1),
                            (SELECT MAX(service_date) FROM scheduled_trips),
                            CURRENT_DATE)
                ) j ON TRUE
                LEFT JOIN LATERAL (
                    SELECT COALESCE(MAX(sub.max_seg), 0) AS adj_count
                    FROM (
                        SELECT COALESCE(SUM(sda.demand_delta), 0) AS max_seg
                        FROM segment_demand_adjustments sda
                        WHERE sda.route_id = r.route_id
                          AND (sda.session_id = ? OR ? IS NULL)
                        GROUP BY sda.segment_start_stop_id
                    ) sub
                ) a ON TRUE
                WHERE r.is_active = TRUE
                ORDER BY r.route_id
                """;
        return jdbcTemplate.queryForList(sql, sessionId, sessionId, serviceDate, sessionId, sessionId);
    }

    // Record passenger travel search intent into common demand
    public Long recordSearchIntent(Long sessionId, Long tripId, Long boardingStopId, Long destinationStopId, Integer count) {
        String sql = """
                INSERT INTO passenger_journeys (
                    session_id,
                    trip_id,
                    boarding_stop_id,
                    destination_stop_id,
                    passenger_count,
                    journey_status
                )
                VALUES (?, ?, ?, ?, ?, 'UNCONFIRMED')
                RETURNING journey_id
                """;
        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                sessionId,
                tripId,
                boardingStopId,
                destinationStopId,
                count != null && count > 0 ? count : 1
        );
    }

    // Update the status of a passenger journey
    public int updateJourneyStatus(Long journeyId, String status) {

        String sql;
        if ("BOARDED".equalsIgnoreCase(status)) {
            sql = """
                    UPDATE passenger_journeys
                    SET journey_status = ?::journey_status,
                        boarding_confirmed_at = COALESCE(boarding_confirmed_at, NOW())
                    WHERE journey_id = ?
                    """;
        } else if ("COMPLETED".equalsIgnoreCase(status)) {
            sql = """
                    UPDATE passenger_journeys
                    SET journey_status = ?::journey_status,
                        alighting_confirmed_at = COALESCE(alighting_confirmed_at, NOW())
                    WHERE journey_id = ?
                    """;
        } else {
            sql = """
                    UPDATE passenger_journeys
                    SET journey_status = ?::journey_status
                    WHERE journey_id = ?
                    """;
        }

        return jdbcTemplate.update(
                sql,
                status,
                journeyId
        );
    }
}
