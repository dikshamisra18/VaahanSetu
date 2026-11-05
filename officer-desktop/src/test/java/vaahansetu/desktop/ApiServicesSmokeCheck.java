package vaahansetu.desktop;

import java.util.List;
import java.util.Set;

import vaahansetu.desktop.api.ApiServices;
import vaahansetu.desktop.model.*;
import vaahansetu.desktop.service.DesktopServices;

public final class ApiServicesSmokeCheck {
    private static int failures = 0;

    private static void check(boolean ok, String name) {
        System.out.println((ok ? "PASS: " : "FAIL: ") + name);
        if (!ok) failures++;
    }

    public static void main(String[] args) throws Exception {
        String url = System.getProperty("backend.url", "http://localhost:8080/api");
        if (!ApiServices.isAvailable(url)) {
            System.out.println("SKIP: Backend is not currently running at " + url);
            return;
        }

        ApiServices api = new ApiServices(url);
        DesktopServices services = api.create();

        // 1. Admin login
        boolean adminLogin = services.auth().login("admin", "admin123");
        check(adminLogin, "admin logs in successfully via backend API");
        check(services.auth().currentRole() == UserRole.ADMIN, "admin role verified");

        // 2. Fetch buses
        List<Bus> buses = services.buses().listBuses();
        check(!buses.isEmpty(), "buses loaded from live backend");
        Bus b101 = buses.stream().filter(b -> b.busId() == 101).findFirst().orElse(null);
        check(b101 != null && (b101.restDurationMinutes() == 15 || b101.restDurationMinutes() == 30), "bus 101 rest duration matches database");

        // 3. Fetch routes and stops
        List<Route> routes = services.routes().listRoutes();
        check(!routes.isEmpty(), "routes loaded from live backend");
        List<RouteStop> stops = services.routes().stopsOfRoute(1);
        check(stops.size() >= 2, "stops for route 1 loaded in order");

        // 4. Fetch trips
        List<ScheduledTrip> trips = services.trips().listTrips();
        check(!trips.isEmpty(), "scheduled trips loaded from live backend");

        // 5. Active planning session
        long sessionId = services.planning().activeSessionId();
        check(sessionId > 0, "active planning session retrieved");
        Set<Long> selRoutes = services.planning().selectedRouteIds();
        check(!selRoutes.isEmpty(), "session routes retrieved");

        // 6. Segment demand
        List<SegmentDemand> demand = services.demand().demandForTrip(1001);
        check(demand.size() >= 3, "trip 1001 has consecutive segments");

        // 7. Allocation proposal
        AllocationResult proposal = services.allocation().runAllocation();
        check(proposal != null && "PROPOSED".equals(proposal.status()), "allocation returns live proposal");
        check(!proposal.assignments().isEmpty(), "proposal has assignments");

        // 8. Officer login
        ApiServices officerApi = new ApiServices(url);
        DesktopServices officerServices = officerApi.create();
        boolean officerLogin = officerServices.auth().login("officer1", "officer123")
                || officerServices.auth().login("officer", "officer123");
        check(officerLogin, "officer logs in successfully via backend API");
        check(officerServices.auth().currentRole() == UserRole.OFFICER, "officer role verified");

        System.out.println(failures == 0 ? "\nAll ApiServices live checks passed." : "\n" + failures + " check(s) FAILED.");
        if (failures > 0) System.exit(1);
    }
}
