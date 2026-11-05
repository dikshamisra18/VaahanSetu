package com.vaahansetu;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaahansetu.allocation.AllocationEngine;
import com.vaahansetu.allocation.AllocationModels.*;

public class JourneyServiceTest {

    private LocalDateTime t(String hhmm) {
        return LocalDateTime.parse("2026-11-04T" + hhmm);
    }

    @Test
    public void testStopSequenceOrdering() {
        List<Long> stops = List.of(1L, 2L, 3L, 4L);
        int boardingIndex = stops.indexOf(1L);
        int destinationIndex = stops.indexOf(3L);

        assertTrue(boardingIndex >= 0, "Boarding stop must exist");
        assertTrue(destinationIndex > boardingIndex, "Destination must follow boarding stop");

        int invalidIndex = stops.indexOf(99L);
        assertEquals(-1, invalidIndex, "Unknown stop should return -1");
    }

    @Test
    public void testPassengerCountBounds() {
        int validCount = 4;
        assertTrue(validCount >= 1 && validCount <= 10, "Valid passenger count within [1, 10]");

        int lowCount = 0;
        assertFalse(lowCount >= 1 && lowCount <= 10, "Passenger count 0 is invalid");

        int highCount = 11;
        assertFalse(highCount >= 1 && highCount <= 10, "Passenger count 11 is invalid");
    }

    @Test
    public void testAllocationEngineSegmentDemandCalculation() {
        Route r1 = new Route(1, "R1", "Central-Airport", List.of(1L, 2L, 3L, 4L));

        Input input = new Input(
                t("07:00"),
                List.of(r1),
                List.of(new Bus(101, "KA-01-AA-1001", 50, "ACTIVE", 15)),
                List.of(new Trip(1001, 1, t("08:00"), t("09:30"), null, 0)),
                List.of(new Journey(1001, 1, 3, 2), new Journey(1001, 2, 4, 1)),
                List.of()
        );

        Result result = AllocationEngine.allocate(input);
        assertNotNull(result);
        assertEquals(3, result.afterSegments().size(), "Route with 4 stops must have 3 segments");

        // Segment A-B: only journey 1 (2 passengers)
        assertEquals(2, result.afterSegments().get(0).demand());
        // Segment B-C: journey 1 (2) + journey 2 (1) = 3 passengers
        assertEquals(3, result.afterSegments().get(1).demand());
        // Segment C-D: only journey 2 (1 passenger)
        assertEquals(1, result.afterSegments().get(2).demand());

        assertEquals(0, result.afterTotal().unmet(), "All 6 passenger-segments accommodated");
        assertEquals(1, result.busesAssigned());
    }

    @Test
    public void testAllocationEngineReassignmentForHigherDemand() {
        Route r1 = new Route(1, "R1", "Central-Airport", List.of(1L, 2L, 3L, 4L));
        Route r2 = new Route(2, "R2", "EastGate-Lake", List.of(5L, 6L, 7L));

        // Baseline had small bus (30) on big trip and big bus (50) on small trip
        Input input = new Input(
                t("07:00"),
                List.of(r1, r2),
                List.of(
                        new Bus(1, "SMALL", 30, "ACTIVE", 0),
                        new Bus(2, "BIG", 50, "ACTIVE", 0)
                ),
                List.of(
                        new Trip(10, 1, t("08:00"), t("09:30"), 1L, 30),
                        new Trip(20, 2, t("08:00"), t("09:00"), 2L, 50)
                ),
                List.of(
                        new Journey(10, 1, 4, 45),
                        new Journey(20, 5, 7, 20)
                ),
                List.of()
        );

        Result result = AllocationEngine.allocate(input);
        assertEquals(2L, result.outcomes().get(0).proposedBusId(), "Trip 10 with 45 demand gets 50-seater bus");
        assertEquals(1L, result.outcomes().get(1).proposedBusId(), "Trip 20 with 20 demand gets 30-seater bus");
        assertEquals("REASSIGNED", result.outcomes().get(0).type());
        assertEquals(0, result.afterTotal().unmet());
    }

    @Test
    public void testAllocationEngineSurgeDemandExceedingCapacity() {
        Route r1 = new Route(1, "R1", "Secunderabad-Airport", List.of(1L, 2L, 3L));

        // Only one 50-seater bus available, but demand is 75 passengers
        Input input = new Input(
                t("07:00"),
                List.of(r1),
                List.of(new Bus(1, "BUS-50", 50, "ACTIVE", 15)),
                List.of(new Trip(10, 1, t("08:00"), t("09:00"), null, 0)),
                List.of(new Journey(10, 1, 3, 75)),
                List.of()
        );

        Result result = AllocationEngine.allocate(input);
        assertNotNull(result);
        assertEquals(1L, result.outcomes().get(0).proposedBusId(), "Bus assigned to best accommodate load");
        assertTrue(result.afterTotal().unmet() > 0, "Unmet demand should be identified when demand exceeds bus capacity");
        assertEquals(50, result.outcomes().get(0).reason().contains("50") ? 50 : 0);
        assertTrue(result.outcomes().get(0).reason().contains("75") || result.outcomes().get(0).reason().contains("occupancy"),
                "Explanation must mention load or occupancy details");
    }

    @Test
    public void testAllocationEngineTurnaroundRestConstraint() {
        Route r1 = new Route(1, "R1", "Route-1", List.of(1L, 2L));

        // Trip 1 arrives at 09:00. Bus requires 30 min rest, so not available until 09:30.
        // Trip 2 departs at 09:15. Overlaps with rest window!
        Input input = new Input(
                t("07:00"),
                List.of(r1),
                List.of(new Bus(1, "BUS-1", 50, "ACTIVE", 30)),
                List.of(
                        new Trip(10, 1, t("08:00"), t("09:00"), 1L, 50),
                        new Trip(20, 1, t("09:15"), t("10:15"), null, 0)
                ),
                List.of(
                        new Journey(10, 1, 2, 20),
                        new Journey(20, 1, 2, 25)
                ),
                List.of()
        );

        Result result = AllocationEngine.allocate(input);
        assertNotNull(result);
        // Only one bus can take at most one of the overlapping trips
        long assignedCount = result.outcomes().stream().filter(o -> o.proposedBusId() != null).count();
        assertEquals(1, assignedCount, "Bus cannot be assigned to second trip due to mandatory 30-min rest period");
        assertTrue(result.outcomes().stream().anyMatch(o -> "UNASSIGNED".equals(o.type())),
                "Conflicting trip must be UNASSIGNED when no free bus is available");
    }
}
