package vaahansetu.desktop.demo;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import vaahansetu.desktop.model.*;
import vaahansetu.desktop.service.*;

/**
 * Demo-only implementations. They make the complete Member 2 UI runnable before the
 * shared officer APIs are implemented. All records are held in memory and reset on restart.
 */
public final class MockServices {
    private final DemoStore store = new DemoStore();
    private final MockAuth auth = new MockAuth();
    private final MockAllocation allocation = new MockAllocation();

    public DesktopServices create() {
        return new DesktopServices(auth,
                () -> List.copyOf(store.buses),
                new MockRoutes(),
                new MockTrips(),
                new MockPlanning(),
                store::demandForTrip,
                new MockSimulation(),
                allocation);
    }

    private final class MockAuth implements AuthService {
        private String displayName;
        private UserRole role;

        @Override
        public boolean login(String username, String password) {
            if ("admin".equals(username) && "admin123".equals(password)) {
                displayName = "System Administrator";
                role = UserRole.ADMIN;
                return true;
            }
            if ("officer".equals(username) && "officer123".equals(password)) {
                displayName = "Control Room Officer";
                role = UserRole.OFFICER;
                return true;
            }
            displayName = null;
            role = null;
            return false;
        }

        @Override public String currentDisplayName() { return displayName; }
        @Override public UserRole currentRole() { return role; }
    }

    private final class MockRoutes implements RouteService {
        @Override public List<Route> listRoutes() { return store.routes.stream().filter(Route::active).toList(); }
        @Override public List<RouteStop> stopsOfRoute(long routeId) {
            return List.copyOf(store.routeStops.getOrDefault(routeId, List.of()));
        }
    }

    private final class MockTrips implements TripService {
        @Override public List<ScheduledTrip> listTrips() {
            return store.trips.stream()
                    .filter(t -> t.getStatus() == TripStatus.SCHEDULED || t.getStatus() == TripStatus.IN_PROGRESS)
                    .sorted(Comparator.comparing(ScheduledTrip::getServiceDate)
                            .thenComparing(ScheduledTrip::getDepartureTime))
                    .toList();
        }
        @Override public String currentBusLabel(long tripId) {
            ScheduledTrip trip = store.findTrip(tripId);
            return trip == null ? "-" : store.busLabel(trip.getCurrentBusId());
        }
        @Override public void refresh() { /* in-memory demo data is already current */ }
    }

    private final class MockPlanning implements PlanningService {
        @Override public long activeSessionId() { return store.activeSessionId; }
        @Override public String activeSessionName() { return store.activeSessionName; }
        @Override public Set<Long> selectedRouteIds() { return Set.copyOf(store.selectedRoutes); }
        @Override public Set<Long> selectedBusIds() { return Set.copyOf(store.selectedBuses); }
        @Override public void saveSelection(Set<Long> routeIds, Set<Long> busIds) throws ServiceException {
            if (routeIds == null || routeIds.isEmpty()) throw new ServiceException("Select at least one route for this planning session.");
            if (busIds == null || busIds.isEmpty()) throw new ServiceException("Select at least one bus for this planning session.");
            Set<Long> validRoutes = new HashSet<>();
            store.routes.stream().filter(Route::active).forEach(r -> validRoutes.add(r.routeId()));
            Set<Long> validBuses = new HashSet<>();
            store.buses.stream().filter(b -> b.status() == BusStatus.ACTIVE).forEach(b -> validBuses.add(b.busId()));
            if (!validRoutes.containsAll(routeIds)) throw new ServiceException("Selection contains an inactive or unknown route.");
            if (!validBuses.containsAll(busIds)) throw new ServiceException("Selection contains a non-active or unknown bus.");
            store.selectedRoutes.clear();
            store.selectedRoutes.addAll(routeIds);
            store.selectedBuses.clear();
            store.selectedBuses.addAll(busIds);
            allocation.invalidate();
        }
    }

    private final class MockSimulation implements SimulationService {
        @Override
        public SimulationStepResult simulateTwoMinutes(long tripId, List<SegmentKey> segments) throws ServiceException {
            ScheduledTrip trip = store.findTrip(tripId);
            if (trip == null) throw new ServiceException("Select a valid scheduled trip.");
            if (segments == null || segments.isEmpty()) throw new ServiceException("Select at least one route segment to simulate.");
            List<SegmentDemand> current = store.demandForTrip(tripId);
            Set<SegmentKey> allowed = new HashSet<>();
            current.forEach(d -> allowed.add(d.key()));
            if (!allowed.containsAll(segments)) throw new ServiceException("One or more selected segments do not belong to this trip.");

            List<SegmentKey> affected = new ArrayList<>();
            List<String> changes = new ArrayList<>();
            for (SegmentKey key : segments) {
                int magnitude = 5 + (store.simulationCounter % 6); // 5..10
                boolean increase = (store.simulationCounter++ % 2 == 0);
                int delta = increase ? magnitude : -magnitude;
                store.simulationAdjustments.merge(key, delta, Integer::sum);
                affected.add(key);
                SegmentDemand row = current.stream().filter(d -> d.key().equals(key)).findFirst().orElseThrow();
                int after = Math.max(0, row.registeredDemand() + store.simulationAdjustments.get(key));
                changes.add(row.startStopName() + " → " + row.endStopName() + " "
                        + (delta > 0 ? "+" : "") + delta + " (effective demand " + after + ")");
            }
            store.simulationElapsedMinutes += 2;
            allocation.invalidate();
            return new SimulationStepResult(store.simulationElapsedMinutes,
                    "Simulation T+" + formatTime(store.simulationElapsedMinutes) + ": " + String.join("; ", changes),
                    affected);
        }

        @Override public int elapsedMinutes() { return store.simulationElapsedMinutes; }
    }

    private final class MockAllocation implements AllocationService {
        private AllocationResult pending;

        @Override
        public AllocationResult runAllocation() throws ServiceException {
            List<ScheduledTrip> trips = store.trips.stream()
                    .filter(t -> store.selectedRoutes.contains(t.getRouteId()))
                    .filter(t -> t.getStatus() == TripStatus.SCHEDULED || t.getStatus() == TripStatus.IN_PROGRESS)
                    .sorted(Comparator.comparing(ScheduledTrip::getDepartureTime).thenComparingLong(ScheduledTrip::getTripId))
                    .toList();
            if (trips.isEmpty()) throw new ServiceException("No scheduled trips belong to the selected planning routes.");

            List<SegmentAllocationResult> baselineSegments = new ArrayList<>();
            List<SegmentAllocationResult> proposedSegments = new ArrayList<>();
            List<AllocationAssignment> assignments = new ArrayList<>();
            Map<Long, Long> proposedBusByTrip = new LinkedHashMap<>();
            Map<Long, List<TripInterval>> plannedIntervals = new HashMap<>();
            Set<Long> selectedTripIds = new HashSet<>();
            trips.forEach(t -> selectedTripIds.add(t.getTripId()));

            // Trips outside the selected route set remain fixed commitments.
            for (ScheduledTrip other : store.trips) {
                if (selectedTripIds.contains(other.getTripId()) || other.getCurrentBusId() == null) continue;
                plannedIntervals.computeIfAbsent(other.getCurrentBusId(), k -> new ArrayList<>())
                        .add(interval(other));
            }

            for (ScheduledTrip trip : trips) {
                List<SegmentDemand> demandRows = store.demandForTrip(trip.getTripId());
                int maxSegmentDemand = demandRows.stream().mapToInt(SegmentDemand::effectiveDemand).max().orElse(0);
                Bus oldBus = store.findBus(trip.getCurrentBusId());
                int baselineCapacity = oldBus != null && oldBus.status() == BusStatus.ACTIVE ? oldBus.capacity() : 0;
                for (SegmentDemand d : demandRows) baselineSegments.add(segmentResult(d, baselineCapacity));

                Bus chosen = chooseBus(trip, demandRows, plannedIntervals);
                Long proposedBusId = chosen == null ? null : chosen.busId();
                proposedBusByTrip.put(trip.getTripId(), proposedBusId);
                if (chosen != null) {
                    plannedIntervals.computeIfAbsent(chosen.busId(), k -> new ArrayList<>()).add(interval(trip));
                }

                String previous = store.busLabel(trip.getCurrentBusId());
                String next = chosen == null ? "UNASSIGNED" : chosen.registrationNumber();
                AssignmentType type;
                String reason;
                if (chosen == null) {
                    type = AssignmentType.UNASSIGNED;
                    reason = "No selected ACTIVE bus satisfies capacity, fixed rest-slot, minimum rest and schedule constraints.";
                } else if (trip.getCurrentBusId() != null && trip.getCurrentBusId() == chosen.busId()) {
                    type = AssignmentType.UNCHANGED;
                    reason = "Current bus is eligible and has sufficient capacity for every segment.";
                } else {
                    type = AssignmentType.REASSIGNED;
                    reason = "Selected " + chosen.registrationNumber() + " (" + chosen.capacity()
                            + " seats); maximum segment demand is " + maxSegmentDemand + ".";
                }
                assignments.add(new AllocationAssignment(trip.getTripId(),
                        store.findRoute(trip.getRouteId()).routeCode(), trip.departureLabel(),
                        previous, next, proposedBusId, type, reason));

                int proposedCapacity = chosen == null ? 0 : chosen.capacity();
                for (SegmentDemand d : demandRows) proposedSegments.add(segmentResult(d, proposedCapacity));
            }

            pending = new AllocationResult(store.nextAllocationRunId++, "PROPOSED",
                    metrics(baselineSegments), metrics(proposedSegments), assignments,
                    baselineSegments, proposedSegments);
            return pending;
        }

        private Bus chooseBus(ScheduledTrip trip, List<SegmentDemand> demandRows,
                              Map<Long, List<TripInterval>> plannedIntervals) {
            int maxDemand = demandRows.stream().mapToInt(SegmentDemand::effectiveDemand).max().orElse(0);
            Bus current = store.findBus(trip.getCurrentBusId());
            if (current != null && store.selectedBuses.contains(current.busId())
                    && eligible(current, trip, maxDemand, plannedIntervals)) return current;

            return store.buses.stream()
                    .filter(b -> store.selectedBuses.contains(b.busId()))
                    .filter(b -> eligible(b, trip, maxDemand, plannedIntervals))
                    .min(Comparator.comparingInt(Bus::capacity).thenComparingLong(Bus::busId))
                    .orElse(null);
        }

        private boolean eligible(Bus bus, ScheduledTrip trip, int maxDemand,
                                 Map<Long, List<TripInterval>> plannedIntervals) {
            if (bus.status() != BusStatus.ACTIVE || bus.capacity() < maxDemand) return false;
            LocalDateTime start = LocalDateTime.of(trip.getServiceDate(), trip.getDepartureTime());
            LocalDateTime end = LocalDateTime.of(trip.getServiceDate(), trip.getArrivalTime());
            if (overlapsFixedRest(bus, start, end)) return false;
            for (TripInterval interval : plannedIntervals.getOrDefault(bus.busId(), List.of())) {
                LocalDateTime restEnd = interval.end().plusMinutes(bus.restDurationMinutes());
                LocalDateTime candidateRestEnd = end.plusMinutes(bus.restDurationMinutes());
                boolean conflict = start.isBefore(restEnd) && interval.start().isBefore(candidateRestEnd);
                if (conflict) return false;
            }
            return true;
        }

        private boolean overlapsFixedRest(Bus bus, LocalDateTime tripStart, LocalDateTime tripEnd) {
            if (!bus.hasFixedRestWindow()) return false;
            for (int offset = -1; offset <= 2; offset++) {
                var day = tripStart.toLocalDate().plusDays(offset);
                LocalDateTime restStart = day.atTime(bus.restStart());
                LocalDateTime restEnd = day.atTime(bus.restEnd());
                if (bus.restEnd().isBefore(bus.restStart())) restEnd = restEnd.plusDays(1);
                if (tripStart.isBefore(restEnd) && restStart.isBefore(tripEnd)) return true;
            }
            return false;
        }

        private TripInterval interval(ScheduledTrip trip) {
            return new TripInterval(LocalDateTime.of(trip.getServiceDate(), trip.getDepartureTime()),
                    LocalDateTime.of(trip.getServiceDate(), trip.getArrivalTime()));
        }

        private SegmentAllocationResult segmentResult(SegmentDemand d, int capacity) {
            int accommodated = Math.min(d.effectiveDemand(), capacity);
            int unmet = Math.max(0, d.effectiveDemand() - capacity);
            int unused = Math.max(0, capacity - d.effectiveDemand());
            return new SegmentAllocationResult(d.key().tripId(), d.routeCode(), d.departureLabel(),
                    d.startStopName(), d.endStopName(), d.effectiveDemand(), capacity,
                    accommodated, unmet, unused);
        }

        private AllocationMetrics metrics(List<SegmentAllocationResult> rows) {
            int demand = 0, capacity = 0, accommodated = 0, unmet = 0, unused = 0;
            for (SegmentAllocationResult row : rows) {
                demand += row.demand();
                capacity += row.allocatedCapacity();
                accommodated += row.accommodated();
                unmet += row.unmet();
                unused += row.unused();
            }
            return new AllocationMetrics(demand, capacity, accommodated, unmet, unused);
        }

        @Override
        public void approveAndApply(long allocationRunId) throws ServiceException {
            if (pending == null || pending.allocationRunId() != allocationRunId || !"PROPOSED".equals(pending.status())) {
                throw new ServiceException("There is no current proposal to approve. Run allocation again.");
            }
            // Mock-only apply. Real backend must perform this operation in one DB transaction.
            for (AllocationAssignment row : pending.assignments()) {
                ScheduledTrip trip = store.findTrip(row.tripId());
                if (trip != null) trip.setCurrentBusId(row.proposedBusId());
            }
            pending = new AllocationResult(pending.allocationRunId(), "APPROVED / APPLIED",
                    pending.baselineMetrics(), pending.proposedMetrics(), pending.assignments(),
                    pending.baselineSegments(), pending.proposedSegments());
        }

        void invalidate() { pending = null; }
    }

    private record TripInterval(LocalDateTime start, LocalDateTime end) { }

    private static String formatTime(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }
}
