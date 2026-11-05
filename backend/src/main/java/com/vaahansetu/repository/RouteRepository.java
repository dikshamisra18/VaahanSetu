package com.vaahansetu.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RouteRepository {

    private final JdbcTemplate jdbcTemplate;

    public RouteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Get all active routes
    public List<Map<String, Object>> findAllActiveRoutes() {

        String sql = """
                SELECT route_id AS "routeId",
                       route_code AS "routeCode",
                       route_name AS "routeName",
                       origin_name AS "originName",
                       destination_name AS "destinationName",
                       estimated_duration_minutes AS "estimatedDurationMinutes"
                FROM routes
                WHERE is_active = TRUE
                ORDER BY route_id
                """;

        return jdbcTemplate.queryForList(sql);
    }

    // Get active stops for a selected route
    public List<Map<String, Object>> findStopsByRouteId(Long routeId) {

        String sql = """
                SELECT s.stop_id AS "stopId",
                       s.stop_name AS "stopName",
                       s.is_active AS "isActive"
                FROM stops s
                JOIN route_stops rs ON rs.stop_id = s.stop_id
                WHERE rs.route_id = ?
                  AND s.is_active = TRUE
                ORDER BY rs.stop_sequence
                """;

        return jdbcTemplate.queryForList(sql, routeId);
    }

    // Get buses actively allocated to a route for today
    public List<Map<String, Object>> findAllocatedBuses(Long routeId) {
        return findAllocatedBuses(routeId, null);
    }

    public List<Map<String, Object>> findAllocatedBuses(Long routeId, LocalDate serviceDate) {
        String sql = """
                SELECT b.bus_id AS "busId",
                       b.registration_number AS "registrationNumber",
                       b.capacity AS "capacity",
                       b.current_location AS "currentLocation",
                       st.trip_id AS "tripId",
                       st.departure_time AS "departureTime",
                       st.expected_arrival_time AS "expectedArrivalTime"
                FROM scheduled_trips st
                JOIN trip_assignments ta ON ta.trip_id = st.trip_id
                JOIN buses b ON b.bus_id = ta.bus_id
                WHERE st.route_id = ?
                  AND st.service_date = COALESCE(?,
                        (SELECT started_at::date FROM planning_sessions WHERE status = 'ACTIVE' ORDER BY started_at DESC LIMIT 1),
                        (SELECT MAX(service_date) FROM scheduled_trips WHERE route_id = ?),
                        (SELECT MAX(service_date) FROM scheduled_trips),
                        CURRENT_DATE)
                  AND ta.status = 'ACTIVE'
                  AND b.status = 'ACTIVE'
                ORDER BY st.departure_time
                """;

        return jdbcTemplate.queryForList(
                sql,
                routeId,
                serviceDate,
                routeId
        );
    }
}
