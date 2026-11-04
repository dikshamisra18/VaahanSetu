package com.vaahansetu.dto;

import java.time.LocalDateTime;

public record AllocationStatusResponse(
        Long allocationRunId,
        Long sessionId,
        String status,
        boolean isSimulated,
        int totalDemand,
        int totalAccommodated,
        int totalUnmetDemand,
        int totalUnusedCapacity,
        LocalDateTime createdAt) {
}
