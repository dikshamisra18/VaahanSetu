package vaahansetu.desktop.model;

import java.util.List;

public record AllocationResult(
        long allocationRunId,
        String status,
        AllocationMetrics baselineMetrics,
        AllocationMetrics proposedMetrics,
        List<AllocationAssignment> assignments,
        List<SegmentAllocationResult> baselineSegments,
        List<SegmentAllocationResult> proposedSegments) {

    public AllocationResult {
        assignments = List.copyOf(assignments);
        baselineSegments = List.copyOf(baselineSegments);
        proposedSegments = List.copyOf(proposedSegments);
    }
}
