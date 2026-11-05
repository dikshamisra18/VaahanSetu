package com.vaahansetu;

import static org.junit.jupiter.api.Assertions.*;

import com.vaahansetu.allocation.AllocationEngine;
import com.vaahansetu.allocation.AllocationModels.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AllocationEngineTest {

    private final LocalDateTime base = LocalDateTime.of(2026, 4, 1, 10, 0);

    @Test
    @DisplayName("Demand surge triggers upgrade from 30-seater to 60-seater bus")
    void testDemandSurgeReallocatesToLargerBus() {
        Route r1 = new Route(1L, "R1", "Route 1", List.of(101L, 102L, 103L));
        // Trip 1 with initial 30-seat bus
        Trip t1 = new Trip(10L, 1L, base.plusHours(1), base.plusHours(2), 1L, 30);

        // Journey has 55 passengers on segment 101 -> 103
        Journey j1 = new Journey(10L, 101L, 103L, 55);

        // Fleet has Bus 1 (30 cap) and Bus 2 (60 cap)
        Bus b1 = new Bus(1L, "MH12-30", 30, "ACTIVE", 30);
        Bus b2 = new Bus(2L, "MH12-60", 60, "ACTIVE", 30);

        Input in = new Input(base, List.of(r1), List.of(b1, b2), List.of(t1), List.of(j1), List.of());
        Result res = AllocationEngine.allocate(in);

        assertNotNull(res);
        assertEquals(1, res.outcomes().size());
        TripOutcome outcome = res.outcomes().get(0);
        assertEquals(10L, outcome.tripId());
        assertEquals(2L, outcome.proposedBusId(), "Surge demand should cause reallocation to 60-seat Bus #2");
        assertTrue(outcome.reason().contains("60 seats"), "Reason should mention proposed bus capacity");
        assertTrue(outcome.reason().contains("55"), "Reason should mention peak demand of 55");
        assertEquals(0, res.afterTotal().unmet(), "All 55 passengers should be accommodated with zero unmet demand");
    }

    @Test
    @DisplayName("Rest window constraint prevents assigning resting bus")
    void testRestWindowEnforced() {
        Route r1 = new Route(1L, "R1", "Route 1", List.of(101L, 102L));
        // Trip departing at 10:15 and arriving at 10:45
        Trip t1 = new Trip(10L, 1L, base.plusMinutes(15), base.plusMinutes(45), null, 0);
        Journey j1 = new Journey(10L, 101L, 102L, 25);

        // Bus 1 has rest window from 10:00 to 11:00
        Bus restingBus = new Bus(1L, "MH12-REST", 40, "ACTIVE", null, 60,
                LocalTime.of(10, 0), LocalTime.of(11, 0), "Mandatory maintenance", null, true);
        // Bus 2 is active with no conflicting rest window
        Bus freeBus = new Bus(2L, "MH12-FREE", 40, "ACTIVE", 30);

        Input in = new Input(base, List.of(r1), List.of(restingBus, freeBus), List.of(t1), List.of(j1), List.of());
        Result res = AllocationEngine.allocate(in);

        assertEquals(1, res.outcomes().size());
        TripOutcome outcome = res.outcomes().get(0);
        assertEquals(2L, outcome.proposedBusId(), "Resting bus must not be assigned during its rest window; Free bus should be assigned");
    }

    @Test
    @DisplayName("Turnaround buffer prevents assigning bus to overlapping or back-to-back trips")
    void testTurnaroundBufferEnforced() {
        Route r1 = new Route(1L, "R1", "Route 1", List.of(101L, 102L));
        // Trip 1: 10:00 - 11:00
        Trip t1 = new Trip(1L, 1L, base, base.plusHours(1), null, 0);
        // Trip 2: 11:15 - 12:00 (starts only 15 mins after Trip 1 ends, violating 30-min turnaround)
        Trip t2 = new Trip(2L, 1L, base.plusHours(1).plusMinutes(15), base.plusHours(2), null, 0);

        Journey j1 = new Journey(1L, 101L, 102L, 20);
        Journey j2 = new Journey(2L, 101L, 102L, 20);

        // Only one bus available
        Bus b1 = new Bus(1L, "MH12-SOLO", 30, "ACTIVE", 30);

        Input in = new Input(base, List.of(r1), List.of(b1), List.of(t1, t2), List.of(j1, j2), List.of());
        Result res = AllocationEngine.allocate(in);

        // Only one trip can take Bus 1; the other trip cannot due to turnaround constraint
        long assignedCount = res.outcomes().stream().filter(o -> o.proposedBusId() != null).count();
        assertEquals(1, assignedCount, "Single bus cannot serve both trips due to 30-min turnaround buffer");
    }

    @Test
    @DisplayName("Plain-English explanation generated in outcome")
    void testOutcomeExplanationContent() {
        Route r1 = new Route(1L, "R1", "Route 1", List.of(101L, 102L));
        Trip t1 = new Trip(10L, 1L, base.plusHours(1), base.plusHours(2), 1L, 25);
        Journey j1 = new Journey(10L, 101L, 102L, 40);

        Bus b1 = new Bus(1L, "MH12-A", 25, "ACTIVE", 30);
        Bus b2 = new Bus(2L, "MH12-B", 50, "ACTIVE", 30);

        Input in = new Input(base, List.of(r1), List.of(b1, b2), List.of(t1), List.of(j1), List.of());
        Result res = AllocationEngine.allocate(in);

        TripOutcome outcome = res.outcomes().get(0);
        assertNotNull(outcome.reason());
        assertTrue(outcome.reason().contains("Upgraded") || outcome.reason().contains("Assigned"));
        assertTrue(outcome.reason().contains("passengers") || outcome.reason().contains("pax"));
        assertTrue(outcome.reason().contains("seats"));
    }
}
