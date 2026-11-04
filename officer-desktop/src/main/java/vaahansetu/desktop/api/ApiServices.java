package vaahansetu.desktop.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import vaahansetu.desktop.model.*;
import vaahansetu.desktop.service.*;

/**
 * Backend API-backed implementation of all desktop services.
 * Connects the Swing Officer Console to live Spring Boot endpoints.
 */
public final class ApiServices {

    private final String baseUrl;
    private final HttpClient http;
    private String token;
    private String displayName;
    private UserRole role;

    private final HttpAuthService authService = new HttpAuthService();
    private final HttpBusService busService = new HttpBusService();
    private final HttpRouteService routeService = new HttpRouteService();
    private final HttpTripService tripService = new HttpTripService();
    private final HttpPlanningService planningService = new HttpPlanningService();
    private final HttpDemandService demandService = new HttpDemandService();
    private final HttpSimulationService simulationService = new HttpSimulationService();
    private final HttpAllocationService allocationService = new HttpAllocationService();

    public ApiServices(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            this.baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        } else {
            this.baseUrl = baseUrl;
        }
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public DesktopServices create() {
        return new DesktopServices(
                authService,
                busService,
                routeService,
                tripService,
                planningService,
                demandService,
                simulationService,
                allocationService
        );
    }

    public static boolean isAvailable(String baseUrl) {
        try {
            String url = (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + "/routes";
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() >= 200 && resp.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }

    // ----------------------------------------------------------------
    // HTTP helper
    // ----------------------------------------------------------------

    private HttpResponse<String> send(String method, String path, String jsonBody) throws ServiceException {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/json");

            if (token != null) {
                b.header("Authorization", "Bearer " + token);
            }

            if ("POST".equalsIgnoreCase(method)) {
                b.header("Content-Type", "application/json");
                b.POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "{}" : jsonBody));
            } else if ("PUT".equalsIgnoreCase(method)) {
                b.header("Content-Type", "application/json");
                b.PUT(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "{}" : jsonBody));
            } else if ("PATCH".equalsIgnoreCase(method)) {
                b.header("Content-Type", "application/json");
                b.method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody == null ? "{}" : jsonBody));
            } else {
                b.GET();
            }

            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                String errMsg = "HTTP error " + resp.statusCode();
                try {
                    Map<String, Object> errObj = SimpleJson.parseObject(resp.body());
                    if (errObj.containsKey("message")) {
                        errMsg = String.valueOf(errObj.get("message"));
                    }
                } catch (Exception ignored) {}
                throw new ServiceException(errMsg);
            }
            return resp;
        } catch (ServiceException se) {
            throw se;
        } catch (Exception e) {
            throw new ServiceException("Failed to communicate with backend: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------
    // Auth Service
    // ----------------------------------------------------------------

    private final class HttpAuthService implements AuthService {
        @Override
        public boolean login(String username, String password) throws ServiceException {
            try {
                Map<String, Object> req = Map.of("username", username, "password", password);
                HttpResponse<String> resp = send("POST", "/auth/login", SimpleJson.stringify(req));
                Map<String, Object> body = SimpleJson.parseObject(resp.body());
                token = (String) body.get("token");
                displayName = (String) body.get("fullName");
                String roleStr = (String) body.get("role");
                role = "ADMIN".equalsIgnoreCase(roleStr) ? UserRole.ADMIN : UserRole.OFFICER;
                return true;
            } catch (ServiceException e) {
                if (e.getMessage() != null && e.getMessage().contains("401") || e.getMessage().contains("Invalid")) {
                    token = null;
                    displayName = null;
                    role = null;
                    return false;
                }
                throw e;
            }
        }

        @Override
        public String currentDisplayName() {
            return displayName;
        }

        @Override
        public UserRole currentRole() {
            return role;
        }
    }

    // ----------------------------------------------------------------
    // Bus Service
    // ----------------------------------------------------------------

    private final class HttpBusService implements BusService {
        @Override
        public List<Bus> listBuses() throws ServiceException {
            HttpResponse<String> resp = send("GET", "/officer/buses", null);
            List<Object> rows = SimpleJson.parseArray(resp.body());
            List<Bus> buses = new ArrayList<>();
            for (Object obj : rows) {
                if (obj instanceof Map<?, ?> m) {
                    long id = toLong(m.get("busId"));
                    String reg = String.valueOf(m.get("registrationNumber"));
                    int capacity = toInt(m.get("capacity"));
                    BusStatus status = BusStatus.valueOf(String.valueOf(m.get("status")));
                    String loc = m.get("currentLocation") == null ? "-" : String.valueOf(m.get("currentLocation"));
                    int restDur = toInt(m.get("restDurationMinutes"));
                    LocalTime restStart = parseTime(m.get("restStart"));
                    LocalTime restEnd = parseTime(m.get("restEnd"));
                    buses.add(new Bus(id, reg, capacity, status, loc, restDur, restStart, restEnd));
                }
            }
            return buses;
        }
    }

    // ----------------------------------------------------------------
    // Route Service
    // ----------------------------------------------------------------

    private final class HttpRouteService implements RouteService {
        @Override
        public List<Route> listRoutes() throws ServiceException {
            HttpResponse<String> resp = send("GET", "/officer/routes", null);
            List<Object> rows = SimpleJson.parseArray(resp.body());
            List<Route> routes = new ArrayList<>();
            for (Object obj : rows) {
                if (obj instanceof Map<?, ?> m) {
                    long id = toLong(m.get("routeId"));
                    String code = String.valueOf(m.get("routeCode"));
                    String name = String.valueOf(m.get("routeName"));
                    String orig = m.get("originName") == null ? "" : String.valueOf(m.get("originName"));
                    String dest = m.get("destinationName") == null ? "" : String.valueOf(m.get("destinationName"));
                    int dur = toInt(m.get("estimatedDurationMinutes"));
                    routes.add(new Route(id, code, name, orig, dest, dur, true));
                }
            }
            return routes;
        }

        @Override
        public List<RouteStop> stopsOfRoute(long routeId) throws ServiceException {
            HttpResponse<String> resp = send("GET", "/routes/" + routeId + "/stops", null);
            List<Object> rows = SimpleJson.parseArray(resp.body());
            List<RouteStop> stops = new ArrayList<>();
            int seq = 1;
            for (Object obj : rows) {
                if (obj instanceof Map<?, ?> m) {
                    long stopId = toLong(m.get("stopId"));
                    String name = String.valueOf(m.get("stopName"));
                    int s = m.get("stopSequence") != null ? toInt(m.get("stopSequence")) : seq++;
                    stops.add(new RouteStop(routeId, stopId, s, name));
                }
            }
            return stops;
        }
    }

    // ----------------------------------------------------------------
    // Trip Service
    // ----------------------------------------------------------------

    private final class HttpTripService implements TripService {
        private final Map<Long, ScheduledTrip> cache = new HashMap<>();

        @Override
        public List<ScheduledTrip> listTrips() throws ServiceException {
            HttpResponse<String> resp = send("GET", "/officer/trips", null);
            List<Object> rows = SimpleJson.parseArray(resp.body());
            List<ScheduledTrip> trips = new ArrayList<>();
            cache.clear();
            for (Object obj : rows) {
                if (obj instanceof Map<?, ?> m) {
                    long tripId = toLong(m.get("tripId"));
                    long routeId = toLong(m.get("routeId"));
                    LocalDate date = parseDate(m.get("serviceDate"));
                    LocalTime dep = parseDateTimeToTime(m.get("departureTime"));
                    LocalTime arr = parseDateTimeToTime(m.get("expectedArrivalTime"));
                    TripStatus status = TripStatus.valueOf(String.valueOf(m.get("status")));
                    Long busId = m.get("currentBusId") == null ? null : toLong(m.get("currentBusId"));
                    ScheduledTrip trip = new ScheduledTrip(tripId, routeId, date, dep, arr, status, busId);
                    trips.add(trip);
                    cache.put(tripId, trip);
                }
            }
            return trips;
        }

        @Override
        public String currentBusLabel(long tripId) throws ServiceException {
            ScheduledTrip t = cache.get(tripId);
            if (t == null) listTrips();
            t = cache.get(tripId);
            if (t == null || t.getCurrentBusId() == null) return "UNASSIGNED";
            for (Bus b : busService.listBuses()) {
                if (b.busId() == t.getCurrentBusId()) return b.registrationNumber();
            }
            return "Bus #" + t.getCurrentBusId();
        }

        @Override
        public void refresh() throws ServiceException {
            listTrips();
        }
    }

    // ----------------------------------------------------------------
    // Planning Service
    // ----------------------------------------------------------------

    private final class HttpPlanningService implements PlanningService {
        private long sessionId = 1;
        private String sessionName = "Active Planning Session";

        @Override
        public long activeSessionId() throws ServiceException {
            refreshSession();
            return sessionId;
        }

        @Override
        public String activeSessionName() throws ServiceException {
            refreshSession();
            return sessionName;
        }

        @Override
        public Set<Long> selectedRouteIds() throws ServiceException {
            Map<String, Object> session = refreshSession();
            Set<Long> ids = new LinkedHashSet<>();
            if (session.get("selectedRouteIds") instanceof List<?> list) {
                for (Object item : list) ids.add(toLong(item));
            }
            return ids;
        }

        @Override
        public Set<Long> selectedBusIds() throws ServiceException {
            Map<String, Object> session = refreshSession();
            Set<Long> ids = new LinkedHashSet<>();
            if (session.get("selectedBusIds") instanceof List<?> list) {
                for (Object item : list) ids.add(toLong(item));
            }
            return ids;
        }

        @Override
        public void saveSelection(Set<Long> routeIds, Set<Long> busIds) throws ServiceException {
            if (routeIds == null || routeIds.isEmpty()) {
                throw new ServiceException("Select at least one route for this planning session.");
            }
            if (busIds == null || busIds.isEmpty()) {
                throw new ServiceException("Select at least one bus for this planning session.");
            }
            Map<String, Object> req = Map.of(
                    "routeIds", new ArrayList<>(routeIds),
                    "busIds", new ArrayList<>(busIds)
            );
            send("PUT", "/officer/sessions/" + activeSessionId() + "/selection", SimpleJson.stringify(req));
        }

        private Map<String, Object> refreshSession() throws ServiceException {
            HttpResponse<String> resp = send("GET", "/officer/sessions/active", null);
            Map<String, Object> map = SimpleJson.parseObject(resp.body());
            if (map.containsKey("sessionId")) {
                sessionId = toLong(map.get("sessionId"));
                sessionName = String.valueOf(map.get("sessionName"));
            }
            return map;
        }
    }

    // ----------------------------------------------------------------
    // Demand Service
    // ----------------------------------------------------------------

    private final class HttpDemandService implements DemandService {
        @Override
        public List<SegmentDemand> demandForTrip(long tripId) throws ServiceException {
            HttpResponse<String> resp = send("GET", "/officer/trips/" + tripId + "/demand", null);
            List<Object> rows = SimpleJson.parseArray(resp.body());
            List<SegmentDemand> list = new ArrayList<>();
            for (Object obj : rows) {
                if (obj instanceof Map<?, ?> m) {
                    long routeId = toLong(m.get("routeId"));
                    long startId = toLong(m.get("startStopId"));
                    long endId = toLong(m.get("endStopId"));
                    SegmentKey key = new SegmentKey(tripId, startId, endId);
                    String routeCode = m.get("routeCode") == null ? "R" : String.valueOf(m.get("routeCode"));
                    String depLabel = m.get("departureLabel") == null ? "" : String.valueOf(m.get("departureLabel"));
                    String startName = String.valueOf(m.get("startStopName"));
                    String endName = String.valueOf(m.get("endStopName"));
                    int reg = toInt(m.get("registeredDemand"));
                    int sim = toInt(m.get("simulatedAdjustment"));
                    int eff = toInt(m.get("effectiveDemand"));
                    String curBus = m.get("currentBus") == null ? "UNASSIGNED" : String.valueOf(m.get("currentBus"));
                    int curCap = toInt(m.get("currentCapacity"));
                    list.add(new SegmentDemand(key, routeCode, depLabel, startName, endName, reg, sim, eff, curBus, curCap));
                }
            }
            return list;
        }
    }

    // ----------------------------------------------------------------
    // Simulation Service
    // ----------------------------------------------------------------

    private final class HttpSimulationService implements SimulationService {
        @Override
        public SimulationStepResult simulateTwoMinutes(long tripId, List<SegmentKey> segments) throws ServiceException {
            if (role != UserRole.ADMIN) {
                throw new ServiceException("Only an administrator can run the simulation.");
            }
            List<Map<String, Object>> segMaps = new ArrayList<>();
            for (SegmentKey k : segments) {
                segMaps.add(Map.of("startStopId", k.startStopId(), "endStopId", k.endStopId()));
            }
            Map<String, Object> req = Map.of(
                    "tripId", tripId,
                    "segments", segMaps
            );
            HttpResponse<String> resp = send("POST", "/admin/simulation/step", SimpleJson.stringify(req));
            Map<String, Object> body = SimpleJson.parseObject(resp.body());
            int elapsed = toInt(body.get("elapsedMinutes"));
            String msg = (String) body.get("message");
            return new SimulationStepResult(elapsed, msg, segments);
        }

        @Override
        public int elapsedMinutes() throws ServiceException {
            Map<String, Object> s = planningService.refreshSession();
            return toInt(s.get("simulationElapsedMinutes"));
        }
    }

    // ----------------------------------------------------------------
    // Allocation Service
    // ----------------------------------------------------------------

    private final class HttpAllocationService implements AllocationService {
        private AllocationResult pending;

        @Override
        public AllocationResult runAllocation() throws ServiceException {
            HttpResponse<String> resp = send("POST", "/officer/allocations", "{}");
            Map<String, Object> body = SimpleJson.parseObject(resp.body());

            long runId = toLong(body.get("proposedRunId") != null ? body.get("proposedRunId") : body.get("allocationRunId"));
            String status = body.get("status") == null ? "PROPOSED" : String.valueOf(body.get("status"));

            AllocationMetrics baselineMetrics = parseMetrics(body.get("beforeMetrics") != null ? body.get("beforeMetrics") : body.get("baselineMetrics"));
            AllocationMetrics proposedMetrics = parseMetrics(body.get("afterMetrics") != null ? body.get("afterMetrics") : body.get("proposedMetrics"));

            // Parse assignments
            List<AllocationAssignment> assignments = new ArrayList<>();
            List<Object> rawAssignments = (List<Object>) body.get("assignments");
            if (rawAssignments != null) {
                for (Object o : rawAssignments) {
                    if (o instanceof Map<?, ?> m) {
                        long tripId = toLong(m.get("tripId"));
                        String rCode = String.valueOf(m.get("routeCode"));
                        String dep = m.get("departureLabel") == null ? "" : String.valueOf(m.get("departureLabel"));
                        String prev = m.get("previousBus") == null ? "-" : String.valueOf(m.get("previousBus"));
                        String prop = m.get("proposedBus") == null ? "UNASSIGNED" : String.valueOf(m.get("proposedBus"));
                        Long propId = m.get("proposedBusId") == null ? null : toLong(m.get("proposedBusId"));
                        String rawType = m.get("type") != null ? String.valueOf(m.get("type")) : String.valueOf(m.get("assignmentType"));
                        AssignmentType type;
                        try {
                            type = AssignmentType.valueOf(rawType);
                        } catch (Exception e) {
                            type = AssignmentType.UNCHANGED;
                        }
                        String reason = m.get("reason") == null ? "" : String.valueOf(m.get("reason"));
                        assignments.add(new AllocationAssignment(tripId, rCode, dep, prev, prop, propId, type, reason));
                    }
                }
            } else if (body.get("tripOutcomes") instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        long tripId = toLong(m.get("tripId"));
                        String rCode = "R" + m.get("routeId");
                        String prev = m.get("previousBusId") == null ? "UNASSIGNED" : "Bus #" + m.get("previousBusId");
                        String prop = m.get("proposedBusId") == null ? "UNASSIGNED" : "Bus #" + m.get("proposedBusId");
                        Long propId = m.get("proposedBusId") == null ? null : toLong(m.get("proposedBusId"));
                        AssignmentType type = AssignmentType.valueOf(String.valueOf(m.get("type")));
                        String reason = String.valueOf(m.get("reason"));
                        assignments.add(new AllocationAssignment(tripId, rCode, "", prev, prop, propId, type, reason));
                    }
                }
            }

            // Parse segment results
            List<SegmentAllocationResult> baseSegs = parseSegments(body.get("baselineSegments"));
            List<SegmentAllocationResult> propSegs = parseSegments(body.get("proposedSegments"));

            pending = new AllocationResult(runId, status, baselineMetrics, proposedMetrics, assignments, baseSegs, propSegs);
            return pending;
        }

        @Override
        public void approveAndApply(long allocationRunId) throws ServiceException {
            send("POST", "/officer/allocations/" + allocationRunId + "/approve", "{}");
            if (pending != null) {
                pending = new AllocationResult(
                        pending.allocationRunId(),
                        "APPROVED / APPLIED",
                        pending.baselineMetrics(),
                        pending.proposedMetrics(),
                        pending.assignments(),
                        pending.baselineSegments(),
                        pending.proposedSegments()
                );
            }
        }

        private AllocationMetrics parseMetrics(Object obj) {
            if (obj instanceof Map<?, ?> m) {
                int demand = toInt(m.get("demand") != null ? m.get("demand") : m.get("totalSegmentDemand"));
                int cap = toInt(m.get("capacity"));
                int acc = toInt(m.get("accommodated") != null ? m.get("accommodated") : m.get("accommodatedPassengerSegments"));
                int unmet = toInt(m.get("unmet") != null ? m.get("unmet") : m.get("unmetPassengerSegments"));
                int unused = toInt(m.get("unused") != null ? m.get("unused") : m.get("unusedSeatSegments"));
                return new AllocationMetrics(demand, cap, acc, unmet, unused);
            }
            return new AllocationMetrics(0, 0, 0, 0, 0);
        }

        private List<SegmentAllocationResult> parseSegments(Object obj) {
            List<SegmentAllocationResult> list = new ArrayList<>();
            if (obj instanceof List<?> l) {
                for (Object item : l) {
                    if (item instanceof Map<?, ?> m) {
                        long tripId = toLong(m.get("tripId"));
                        String rCode = String.valueOf(m.get("routeCode"));
                        String dep = m.get("departureLabel") == null ? "" : String.valueOf(m.get("departureLabel"));
                        String sStart = String.valueOf(m.get("startStopName"));
                        String sEnd = String.valueOf(m.get("endStopName"));
                        int dem = toInt(m.get("demand"));
                        int cap = toInt(m.get("allocatedCapacity"));
                        int acc = toInt(m.get("accommodated"));
                        int unmet = toInt(m.get("unmetDemand") != null ? m.get("unmetDemand") : m.get("unmet"));
                        int unused = toInt(m.get("unusedCapacity") != null ? m.get("unusedCapacity") : m.get("unused"));
                        list.add(new SegmentAllocationResult(tripId, rCode, dep, sStart, sEnd, dem, cap, acc, unmet, unused));
                    }
                }
            }
            return list;
        }
    }

    // ----------------------------------------------------------------
    // Type conversion helpers
    // ----------------------------------------------------------------

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(o.toString());
    }

    private static LocalTime parseTime(Object o) {
        if (o == null) return null;
        try {
            return LocalTime.parse(o.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate parseDate(Object o) {
        if (o == null) return LocalDate.now();
        try {
            return LocalDate.parse(o.toString());
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private static LocalTime parseDateTimeToTime(Object o) {
        if (o == null) return null;
        String s = o.toString();
        try {
            if (s.contains("T")) {
                int tIdx = s.indexOf('T');
                String sub = s.substring(tIdx + 1);
                if (sub.contains("+")) sub = sub.substring(0, sub.indexOf('+'));
                if (sub.contains("Z")) sub = sub.substring(0, sub.indexOf('Z'));
                return LocalTime.parse(sub);
            }
            if (s.contains(" ")) {
                return LocalTime.parse(s.substring(s.indexOf(' ') + 1));
            }
            return LocalTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
