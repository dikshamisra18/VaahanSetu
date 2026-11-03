package vaahansetu.desktop.demo;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import vaahansetu.desktop.model.*;

/** In-memory data used only for the Member 2 demo until shared REST APIs are ready. */
final class DemoStore {
    final List<Bus> buses = new ArrayList<>();
    final List<Route> routes = new ArrayList<>();
    final Map<Long, List<RouteStop>> routeStops = new LinkedHashMap<>();
    final List<ScheduledTrip> trips = new ArrayList<>();
    final List<PassengerJourney> journeys = new ArrayList<>();
    final Map<SegmentKey, Integer> simulationAdjustments = new LinkedHashMap<>();
    final Set<Long> selectedRoutes = new LinkedHashSet<>();
    final Set<Long> selectedBuses = new LinkedHashSet<>();
    final long activeSessionId = 1L;
    final String activeSessionName = "Demo planning session";
    int simulationElapsedMinutes = 0;
    int simulationCounter = 0;
    long nextAllocationRunId = 1L;

    DemoStore() {
        seedBuses();
        seedRoutes();
        seedTrips();
        seedJourneys();
        routes.stream().filter(Route::active).forEach(r -> selectedRoutes.add(r.routeId()));
        buses.stream().filter(b -> b.status() == BusStatus.ACTIVE).forEach(b -> selectedBuses.add(b.busId()));
    }

    private void seedBuses() {
        buses.add(new Bus(101, "BUS-101", 50, BusStatus.ACTIVE, "Central Depot", 15,
                LocalTime.of(13, 0), LocalTime.of(13, 30)));
        buses.add(new Bus(102, "BUS-102", 35, BusStatus.ACTIVE, "Central Depot", 15,
                LocalTime.of(13, 0), LocalTime.of(13, 30)));
        buses.add(new Bus(103, "BUS-103", 42, BusStatus.ACTIVE, "East Depot", 20,
                LocalTime.of(12, 30), LocalTime.of(13, 0)));
        buses.add(new Bus(104, "BUS-104", 25, BusStatus.ACTIVE, "East Depot", 15,
                LocalTime.of(14, 0), LocalTime.of(14, 30)));
        buses.add(new Bus(105, "BUS-105", 48, BusStatus.ACTIVE, "Campus Depot", 15,
                null, null));
        buses.add(new Bus(106, "BUS-106", 32, BusStatus.ACTIVE, "Campus Depot", 10,
                null, null));
        buses.add(new Bus(107, "BUS-107", 55, BusStatus.ACTIVE, "Central Depot", 15,
                LocalTime.of(15, 0), LocalTime.of(15, 30)));
        buses.add(new Bus(108, "BUS-108", 50, BusStatus.MAINTENANCE, "Workshop", 0,
                null, null));
    }

    private void seedRoutes() {
        addRoute(1, "R1", "Central-Airport", "Central", "Airport", 90,
                new String[] {"Central", "Market", "Tech Park", "Airport"}, 1);
        addRoute(2, "R2", "EastGate-Lake", "East Gate", "Lake", 60,
                new String[] {"East Gate", "Junction", "Lake"}, 10);
        addRoute(3, "R3", "Campus-City", "Campus", "City Centre", 75,
                new String[] {"Campus", "Library", "Market", "City Centre"}, 20);
    }

    private void addRoute(long id, String code, String name, String origin, String destination,
                          int duration, String[] names, long firstStopId) {
        routes.add(new Route(id, code, name, origin, destination, duration, true));
        List<RouteStop> stops = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            stops.add(new RouteStop(id, firstStopId + i, i + 1, names[i]));
        }
        routeStops.put(id, stops);
    }

    private void seedTrips() {
        LocalDate date = LocalDate.now().plusDays(1);
        addTrip(1001, 1, date, "08:00", 101L);
        addTrip(1002, 1, date, "10:00", 102L);
        addTrip(2001, 2, date, "08:30", 103L);
        addTrip(2002, 2, date, "12:00", 104L);
        addTrip(3001, 3, date, "09:00", null);
        addTrip(3002, 3, date, "14:00", null);
    }

    private void addTrip(long id, long routeId, LocalDate date, String departure, Long busId) {
        Route route = findRoute(routeId);
        LocalTime dep = LocalTime.parse(departure);
        trips.add(new ScheduledTrip(id, routeId, date, dep,
                dep.plusMinutes(route.estimatedDurationMinutes()), TripStatus.SCHEDULED, busId));
    }

    private void seedJourneys() {
        // Each record represents an anonymous passenger submission; passengerCount may be > 1.
        // R1 / trip 1001: A->C(2), B->D(3), A->D(1) => 3, 6, 4 per segment.
        addJourney(1001, 1, 3, 2);
        addJourney(1001, 2, 4, 3);
        addJourney(1001, 1, 4, 1);

        // R1 / trip 1002: demand concentrates on the final two segments.
        addJourney(1002, 1, 4, 28);
        addJourney(1002, 2, 4, 16);

        // R2 / trip 2001: 50 passengers from origin to destination.
        addJourney(2001, 10, 12, 50);
        // R2 / trip 2002: 20 passengers from origin to destination.
        addJourney(2002, 10, 12, 20);

        // R3 trips.
        addJourney(3001, 20, 23, 43);
        addJourney(3002, 20, 23, 30);
    }

    private void addJourney(long tripId, long board, long destination, int count) {
        journeys.add(new PassengerJourney(tripId, board, destination, count));
    }

    Route findRoute(long id) {
        return routes.stream().filter(r -> r.routeId() == id).findFirst().orElse(null);
    }

    Bus findBus(Long id) {
        if (id == null) return null;
        return buses.stream().filter(b -> b.busId() == id).findFirst().orElse(null);
    }

    ScheduledTrip findTrip(long id) {
        return trips.stream().filter(t -> t.getTripId() == id).findFirst().orElse(null);
    }

    String busLabel(Long id) {
        Bus bus = findBus(id);
        return bus == null ? "UNASSIGNED" : bus.registrationNumber();
    }

    List<SegmentDemand> demandForTrip(long tripId) {
        ScheduledTrip trip = findTrip(tripId);
        if (trip == null) return List.of();
        Route route = findRoute(trip.getRouteId());
        List<RouteStop> stops = routeStops.getOrDefault(trip.getRouteId(), List.of());
        Bus current = findBus(trip.getCurrentBusId());
        List<SegmentDemand> result = new ArrayList<>();

        for (int i = 0; i < stops.size() - 1; i++) {
            RouteStop start = stops.get(i);
            RouteStop end = stops.get(i + 1);
            int registered = 0;
            for (PassengerJourney journey : journeys) {
                if (journey.tripId() != tripId) continue;
                int from = indexOfStop(stops, journey.boardingStopId());
                int to = indexOfStop(stops, journey.destinationStopId());
                if (from >= 0 && to > from && from <= i && i < to) {
                    registered += journey.passengerCount();
                }
            }
            SegmentKey key = new SegmentKey(tripId, start.stopId(), end.stopId());
            int adjustment = simulationAdjustments.getOrDefault(key, 0);
            int effective = Math.max(0, registered + adjustment);
            result.add(new SegmentDemand(key, route.routeCode(), trip.departureLabel(),
                    start.stopName(), end.stopName(), registered, adjustment, effective,
                    current == null ? "UNASSIGNED" : current.registrationNumber(),
                    current == null ? 0 : current.capacity()));
        }
        return result;
    }

    int indexOfStop(List<RouteStop> stops, long stopId) {
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).stopId() == stopId) return i;
        }
        return -1;
    }
}
