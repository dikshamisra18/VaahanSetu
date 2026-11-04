package com.vaahansetu.model;

import java.time.LocalDateTime;

public record AllocationResult(
        Long allocationRunId,
        Long sessionId,
        LocalDateTime createdAt,
        String status,
        int totalDemand,
        int totalAccommodated,
        int totalUnmetDemand,
        int totalUnusedCapacity,
        String notes) {
}
