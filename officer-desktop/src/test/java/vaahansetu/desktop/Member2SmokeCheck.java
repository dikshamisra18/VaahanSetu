package vaahansetu.desktop;

import java.awt.Component;
import java.awt.Container;
import java.util.List;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

import vaahansetu.desktop.demo.MockServices;
import vaahansetu.desktop.model.AllocationAssignment;
import vaahansetu.desktop.model.Bus;
import vaahansetu.desktop.model.SegmentDemand;
import vaahansetu.desktop.model.SegmentKey;
import vaahansetu.desktop.model.UserRole;
import vaahansetu.desktop.service.DesktopServices;
import vaahansetu.desktop.service.ServiceException;
import vaahansetu.desktop.ui.AllocationPanel;

/** Database-free checks for Member 2 UI data, roles, simulation and proposal lifecycle. */
public final class Member2SmokeCheck {
    private Member2SmokeCheck() { }

    public static void main(String[] args) throws Exception {
        DesktopServices services = new MockServices().create();

        // Bus screen data must contain all three rest-related values.
        List<Bus> buses = services.buses().listBuses();
        Bus firstBus = buses.stream().filter(bus -> bus.busId() == 101).findFirst().orElseThrow();
        check(firstBus.restDurationMinutes() == 15, "bus exposes minimum post-trip rest duration");
        check(firstBus.restStart() != null && firstBus.restEnd() != null,
                "bus exposes fixed daily rest start and end");
        check(firstBus.hasFixedRestWindow(), "bus identifies a configured fixed rest window");

        // Role-specific UI visibility must match the team decision.
        SwingUtilities.invokeAndWait(() -> {
            AllocationPanel adminPanel = new AllocationPanel(services, UserRole.ADMIN, () -> { });
            AllocationPanel officerPanel = new AllocationPanel(services, UserRole.OFFICER, () -> { });
            JButton adminSimulation = findButton(adminPanel, "Admin: Simulate +2 Minutes");
            JButton officerSimulation = findButton(officerPanel, "Admin: Simulate +2 Minutes");
            check(adminSimulation != null && adminSimulation.isVisible(),
                    "admin can see the two-minute simulation button");
            check(officerSimulation != null && !officerSimulation.isVisible(),
                    "officer cannot see the admin-only simulation button");
        });

        // Per-trip, per-segment registered demand.
        List<SegmentDemand> before = services.demand().demandForTrip(1001);
        check(before.size() == 3, "trip 1001 has three consecutive route segments");
        check(before.get(0).registeredDemand() == 3, "A-B registered demand is 3");
        check(before.get(1).registeredDemand() == 6, "B-C registered demand is 6");
        check(before.get(2).registeredDemand() == 4, "C-D registered demand is 4");
        check(services.demand().demandForTrip(1002).get(0).key().tripId() == 1002,
                "segment demand is associated with its own scheduled trip");

        // A run is a proposal only; simulation makes an old proposal stale.
        String trip1002BusBefore = services.trips().currentBusLabel(1002);
        var firstProposal = services.allocation().runAllocation();
        check(firstProposal != null && "PROPOSED".equals(firstProposal.status()),
                "allocation returns a proposal");
        check(services.trips().currentBusLabel(1002).equals(trip1002BusBefore),
                "running a proposal does not change current bus assignments");
        check(!firstProposal.baselineSegments().isEmpty() && !firstProposal.proposedSegments().isEmpty(),
                "allocation returns baseline and proposed segment rows");

        SegmentKey firstSegment = before.get(0).key();
        services.simulation().simulateTwoMinutes(1001, List.of(firstSegment));
        List<SegmentDemand> after = services.demand().demandForTrip(1001);
        check(services.simulation().elapsedMinutes() == 2,
                "simulation click advances clock by two minutes");
        check(after.get(0).registeredDemand() == 3,
                "simulation leaves registered journey demand unchanged");
        check(after.get(0).simulatedAdjustment() != 0,
                "simulation adjustment is stored separately");
        check(after.get(0).effectiveDemand() == Math.max(0, 3 + after.get(0).simulatedAdjustment()),
                "effective demand applies adjustment and clamps at zero");

        boolean staleProposalRejected = false;
        try {
            services.allocation().approveAndApply(firstProposal.allocationRunId());
        } catch (ServiceException expected) {
            staleProposalRejected = true;
        }
        check(staleProposalRejected, "simulation invalidates an older proposal before approval");

        // A fresh proposal can be approved; only then do current assignments refresh.
        var freshProposal = services.allocation().runAllocation();
        AllocationAssignment trip1002 = freshProposal.assignments().stream()
                .filter(row -> row.tripId() == 1002).findFirst().orElseThrow();
        services.allocation().approveAndApply(freshProposal.allocationRunId());
        check(services.trips().listTrips().stream().anyMatch(t -> t.getTripId() == 1001),
                "trip list remains available after applying proposal");
        check(services.trips().currentBusLabel(1002).equals(trip1002.proposedBus()),
                "approved proposal updates the current bus assignment");

        System.out.println("\nMember 2 smoke checks passed.");
    }

    private static JButton findButton(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) {
                return button;
            }
            if (component instanceof Container child) {
                JButton found = findButton(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError("FAILED: " + description);
        System.out.println("PASS: " + description);
    }
}
