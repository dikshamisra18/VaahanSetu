package vaahansetu.desktop.model;

public record SegmentAllocationResult(
        long tripId,
        String routeCode,
        String departureLabel,
        String startStopName,
        String endStopName,
        int demand,
        int allocatedCapacity,
        int accommodated,
        int unmet,
        int unused) {
}
