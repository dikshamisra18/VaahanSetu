package com.vaahansetu.allocation;

import static com.vaahansetu.allocation.AllocationModels.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Pure allocation logic: no database access, so it can be tested and explained on its own.
 *
 * Objective: minimise total unmet demand. Method: constraint-aware GREEDY (a heuristic,
 * NOT guaranteed globally optimal) - trips with the most demand are served first, and each
 * trip takes the conflict-free bus that leaves the least unmet demand.
 */
public final class AllocationEngine {
    private AllocationEngine() {}

    public static Result allocate(Input in) {
        Map<Long, Route> routes = new HashMap<>();
        for (Route r : in.routes()) routes.put(r.id(), r);

        Map<Long, int[]> demand = demandPerTrip(in, routes);

        // Busy intervals per bus start with the fixed commitments (trips we must not touch).
        Map<Long, List<LocalDateTime[]>> busy = new HashMap<>();
        for (Bus b : in.buses()) busy.put(b.id(), new ArrayList<>());
        for (Commitment c : in.commitments()) {
            List<LocalDateTime[]> list = busy.get(c.busId());
            if (list != null) list.add(new LocalDateTime[]{c.start(), c.end()});
        }

        List<Bus> eligible = new ArrayList<>();                 // only ACTIVE buses can be assigned
        for (Bus b : in.buses()) if ("ACTIVE".equals(b.status())) eligible.add(b);
        eligible.sort(Comparator.comparingLong(Bus::id));

        // Hardest trips first (largest total demand), then earliest departure.
        List<Trip> order = new ArrayList<>(in.trips());
        order.sort(Comparator.comparingInt((Trip t) -> -sum(demand.get(t.id())))
                .thenComparing(Trip::departure).thenComparingLong(Trip::id));

        Map<Long, Bus> proposed = new HashMap<>();
        Map<Long, TripOutcome> outcomeByTrip = new HashMap<>();
        for (Trip t : order) {
            int[] d = demand.get(t.id());
            Bus best = null;
            int bestUnmet = 0;
            for (Bus b : eligible) {
                if (!isFree(b, busy.get(b.id()), t) || overlapsFixedRestWindow(b, t)) continue;
                int u = unmet(d, b.capacity());
                if (best == null || isBetter(b, u, best, bestUnmet, t.currentBusId())) {
                    best = b;
                    bestUnmet = u;
                }
            }
            if (best != null) {
                proposed.put(t.id(), best);
                busy.get(best.id()).add(new LocalDateTime[]{t.departure(), t.arrival()});
            }
            outcomeByTrip.put(t.id(), outcome(t, best, d));
        }

        // Keep outcomes in departure order for readable output.
        List<TripOutcome> outcomes = new ArrayList<>();
        for (Trip t : in.trips()) outcomes.add(outcomeByTrip.get(t.id()));

        List<SegmentRow> before = segments(in, routes, demand, t -> t.currentBusCapacity());
        List<SegmentRow> after = segments(in, routes, demand,
                t -> proposed.containsKey(t.id()) ? proposed.get(t.id()).capacity() : 0);

        return buildResult(in, routes, outcomes, before, after);
    }

    // ---------------- demand ----------------

    /** Segment i = stop i -> stop i+1. A journey boarding at stop b and leaving at stop d
     *  occupies segments b .. d-1 (it is not counted on the segment starting at its destination). */
    private static Map<Long, int[]> demandPerTrip(Input in, Map<Long, Route> routes) {
        Map<Long, Trip> trips = new HashMap<>();
        Map<Long, int[]> demand = new HashMap<>();
        for (Trip t : in.trips()) {
            trips.put(t.id(), t);
            demand.put(t.id(), new int[Math.max(0, routes.get(t.routeId()).stopIds().size() - 1)]);
        }
        for (Journey j : in.journeys()) {
            Trip t = trips.get(j.tripId());
            if (t == null) continue;
            List<Long> stops = routes.get(t.routeId()).stopIds();
            int from = stops.indexOf(j.boardingStopId());
            int to = stops.indexOf(j.destinationStopId());
            if (from < 0 || to <= from) continue;               // invalid journey: ignore
            int[] d = demand.get(t.id());
            for (int s = from; s < to; s++) d[s] += j.passengers();
        }
        return demand;
    }

    private static int sum(int[] a) { int s = 0; for (int x : a) s += x; return s; }

    private static int unmet(int[] demand, int capacity) {
        int u = 0;
        for (int d : demand) u += Math.max(0, d - capacity);
        return u;
    }

    // ---------------- availability ----------------

    /** A bus is free for a trip if it does not overlap any other trip, counting the bus's rest
     *  time after the earlier of the two trips. */
    private static boolean isFree(Bus bus, List<LocalDateTime[]> intervals, Trip t) {
        for (LocalDateTime[] iv : intervals) {
            boolean startsAfterOther = !t.departure().isBefore(iv[1].plusMinutes(bus.restMinutes()));
            boolean endsBeforeOther = !iv[0].isBefore(t.arrival().plusMinutes(bus.restMinutes()));
            if (!startsAfterOther && !endsBeforeOther) return false;
        }
        return true;
    }

    /** Fixed daily rest window is independent of rest_duration_minutes. Handles windows crossing midnight. */
    private static boolean overlapsFixedRestWindow(Bus bus, Trip trip) {
        if (!bus.hasFixedRestWindow()) return false;
        LocalDateTime day = trip.departure().toLocalDate().atStartOfDay();
        // Include the preceding date because a rest window may cross midnight into departure day.
        for (int offset = -1; offset <= 2; offset++) {
            java.time.LocalDate d = day.toLocalDate().plusDays(offset);
            LocalDateTime start = d.atTime(bus.restStart());
            LocalDateTime end = d.atTime(bus.restEnd());
            if (bus.restEnd().isBefore(bus.restStart())) end = end.plusDays(1);
            if (trip.departure().isBefore(end) && start.isBefore(trip.arrival())) return true;
        }
        return false;
    }

    private static boolean isInsideFixedRestWindow(Bus bus, LocalDateTime time) {
        if (!bus.hasFixedRestWindow()) return false;
        java.time.LocalTime now = time.toLocalTime();
        if (bus.restStart().isBefore(bus.restEnd()))
            return !now.isBefore(bus.restStart()) && now.isBefore(bus.restEnd());
        return !now.isBefore(bus.restStart()) || now.isBefore(bus.restEnd());
    }

    /** Fewer unmet passengers wins; then keep the trip's current bus; then the smaller bus. */
    private static boolean isBetter(Bus b, int u, Bus best, int bestU, Long currentBusId) {
        if (u != bestU) return u < bestU;
        boolean bIsCurrent = currentBusId != null && b.id() == currentBusId;
        boolean bestIsCurrent = currentBusId != null && best.id() == currentBusId;
        if (bIsCurrent != bestIsCurrent) return bIsCurrent;
        return b.capacity() < best.capacity();
    }

    // ---------------- results ----------------

    private static TripOutcome outcome(Trip t, Bus best, int[] d) {
        Long prev = t.currentBusId();
        if (best == null) {
            return new TripOutcome(t.id(), t.routeId(), prev, null, "UNASSIGNED",
                    "No ACTIVE bus is free for this full trip (overlap, minimum rest, fixed daily rest window, or existing commitment).");
        }
        if (prev != null && prev == best.id()) {
            return new TripOutcome(t.id(), t.routeId(), prev, best.id(), "UNCHANGED",
                    "Current bus is already the best choice.");
        }
        String why = String.format("Bus %s (capacity %d) leaves %d unmet vs %d with the current bus.",
                best.regNo(), best.capacity(), unmet(d, best.capacity()), unmet(d, t.currentBusCapacity()));
        return new TripOutcome(t.id(), t.routeId(), prev, best.id(), "REASSIGNED", why);
    }

    /** One row per (route, segment): demand and capacity summed over all trips of that route. */
    private static List<SegmentRow> segments(Input in, Map<Long, Route> routes, Map<Long, int[]> demand,
                                             java.util.function.ToIntFunction<Trip> capacityOf) {
        Map<Long, int[]> dem = new LinkedHashMap<>();
        Map<Long, int[]> cap = new LinkedHashMap<>();
        for (Route r : in.routes()) {
            int n = Math.max(0, r.stopIds().size() - 1);
            dem.put(r.id(), new int[n]);
            cap.put(r.id(), new int[n]);
        }
        for (Trip t : in.trips()) {
            int[] d = demand.get(t.id());
            for (int i = 0; i < d.length; i++) {
                dem.get(t.routeId())[i] += d[i];
                cap.get(t.routeId())[i] += capacityOf.applyAsInt(t);
            }
        }
        List<SegmentRow> rows = new ArrayList<>();
        for (Route r : in.routes()) {
            for (int i = 0; i < dem.get(r.id()).length; i++) {
                rows.add(new SegmentRow(r.id(), r.stopIds().get(i), r.stopIds().get(i + 1),
                        dem.get(r.id())[i], cap.get(r.id())[i]));
            }
        }
        return rows;
    }

    private static Metrics metrics(List<SegmentRow> rows) {
        int d = 0, c = 0, a = 0, u = 0, f = 0;
        for (SegmentRow r : rows) {
            d += r.demand(); c += r.capacity(); a += r.accommodated(); u += r.unmet(); f += r.unused();
        }
        return new Metrics(d, c, a, u, f);
    }

    private static List<SegmentRow> forRoute(List<SegmentRow> rows, long routeId) {
        List<SegmentRow> out = new ArrayList<>();
        for (SegmentRow r : rows) if (r.routeId() == routeId) out.add(r);
        return out;
    }

    private static Result buildResult(Input in, Map<Long, Route> routes,
                                      List<TripOutcome> outcomes, List<SegmentRow> before, List<SegmentRow> after) {
        // Which buses ended up on which trips / routes
        Map<Long, Set<String>> originalRoutes = new HashMap<>();
        Map<Long, Set<String>> newRoutes = new HashMap<>();
        Map<Long, List<Long>> busesPerRoute = new HashMap<>();
        for (Bus b : in.buses()) { originalRoutes.put(b.id(), new TreeSet<>()); newRoutes.put(b.id(), new TreeSet<>()); }
        for (Commitment c : in.commitments()) {
            if (originalRoutes.containsKey(c.busId())) {
                originalRoutes.get(c.busId()).add(c.routeCode());
                newRoutes.get(c.busId()).add(c.routeCode());
            }
        }
        for (TripOutcome o : outcomes) {
            String code = routes.get(o.routeId()).code();
            if (o.previousBusId() != null && originalRoutes.containsKey(o.previousBusId()))
                originalRoutes.get(o.previousBusId()).add(code);
            if (o.proposedBusId() != null) {
                newRoutes.get(o.proposedBusId()).add(code);
                List<Long> l = busesPerRoute.computeIfAbsent(o.routeId(), k -> new ArrayList<>());
                if (!l.contains(o.proposedBusId())) l.add(o.proposedBusId());
            }
        }

        List<RouteReport> routeReports = new ArrayList<>();
        for (Route r : in.routes()) {
            routeReports.add(new RouteReport(r.id(), r.code(), r.name(),
                    metrics(forRoute(before, r.id())), metrics(forRoute(after, r.id())),
                    busesPerRoute.getOrDefault(r.id(), List.of())));
        }

        Set<Long> assigned = new HashSet<>();
        for (TripOutcome o : outcomes) if (o.proposedBusId() != null) assigned.add(o.proposedBusId());
        int considered = 0;
        List<BusReport> busReports = new ArrayList<>();
        for (Bus b : in.buses()) {
            if ("ACTIVE".equals(b.status())) considered++;
            String reason = unavailableReason(b, in);
            busReports.add(new BusReport(b.id(), b.regNo(), b.capacity(),
                    join(originalRoutes.get(b.id())), join(newRoutes.get(b.id())), reason == null, reason));
        }
        int left = 0;
        for (Bus b : in.buses()) if ("ACTIVE".equals(b.status()) && !assigned.contains(b.id())) left++;

        return new Result(in.time(), metrics(before), metrics(after), considered, assigned.size(), left,
                routeReports, busReports, outcomes, before, after);
    }

    private static String join(Set<String> s) { return s.isEmpty() ? "-" : String.join(", ", s); }

    /** Why a bus cannot be used at the requested time (null = available). Uses the fixed
     *  commitments only, because those are the trips that really bind the bus at that moment. */
    private static String unavailableReason(Bus b, Input in) {
        if (!"ACTIVE".equals(b.status())) return "Bus status is " + b.status();
        LocalDateTime now = in.time();
        if (isInsideFixedRestWindow(b, now))
            return "Fixed daily rest window (" + b.restStart() + "-" + b.restEnd() + ")";
        for (Commitment c : in.commitments()) {
            if (c.busId() != b.id()) continue;
            if (!now.isBefore(c.start()) && now.isBefore(c.end()))
                return "On trip " + c.tripId() + " (" + c.routeCode() + ") until " + c.end();
            LocalDateTime restEnd = c.end().plusMinutes(b.restMinutes());
            if (!now.isBefore(c.end()) && now.isBefore(restEnd))
                return "Resting after trip " + c.tripId() + " until " + restEnd;
        }
        return null;
    }
}

