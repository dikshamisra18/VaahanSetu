package vaahansetu.desktop.model;

public record AllocationAssignment(
        long tripId,
        String routeCode,
        String departureLabel,
        String previousBus,
        String proposedBus,
        Long proposedBusId,
        AssignmentType type,
        String reason) {
}
