package vaahansetu.desktop.model;

public record SegmentDemand(
        SegmentKey key,
        String routeCode,
        String departureLabel,
        String startStopName,
        String endStopName,
        int registeredDemand,
        int simulatedAdjustment,
        int effectiveDemand,
        String currentBus,
        int currentCapacity) {

    public String statusLabel() {
        if (currentBus == null || "UNASSIGNED".equals(currentBus)) return "UNASSIGNED";
        if (effectiveDemand > currentCapacity) return "OVER CAPACITY by " + (effectiveDemand - currentCapacity);
        return "Within capacity";
    }

    @Override
    public String toString() {
        return startStopName + " → " + endStopName + " (effective demand: " + effectiveDemand + ")";
    }
}
