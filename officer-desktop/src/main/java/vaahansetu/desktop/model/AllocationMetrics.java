package vaahansetu.desktop.model;

/** Totals are passenger-segments summed across all evaluated trips and segments. */
public record AllocationMetrics(int totalSegmentDemand, int allocatedSegmentCapacity,
                                int accommodatedPassengerSegments,
                                int unmetPassengerSegments,
                                int unusedSeatSegments) {
}
