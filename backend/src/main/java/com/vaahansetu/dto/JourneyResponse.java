package com.vaahansetu.dto;

public record JourneyResponse(
        Long journeyId,
        String journeyStatus,
        Long tripId,
        Long routeId,
        Long boardingStopId,
        Long destinationStopId,
        Integer passengerCount,
        String message) {
}
