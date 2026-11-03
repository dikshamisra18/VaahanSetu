package vaahansetu.desktop.model;

import java.time.LocalDate;
import java.time.LocalTime;

public class ScheduledTrip {
    private final long tripId;
    private final long routeId;
    private final LocalDate serviceDate;
    private final LocalTime departureTime;
    private final LocalTime arrivalTime;
    private final TripStatus status;
    private Long currentBusId;

    public ScheduledTrip(long tripId, long routeId, LocalDate serviceDate,
                         LocalTime departureTime, LocalTime arrivalTime,
                         TripStatus status, Long currentBusId) {
        this.tripId = tripId;
        this.routeId = routeId;
        this.serviceDate = serviceDate;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.status = status;
        this.currentBusId = currentBusId;
    }

    public long getTripId() { return tripId; }
    public long getRouteId() { return routeId; }
    public LocalDate getServiceDate() { return serviceDate; }
    public LocalTime getDepartureTime() { return departureTime; }
    public LocalTime getArrivalTime() { return arrivalTime; }
    public TripStatus getStatus() { return status; }
    public Long getCurrentBusId() { return currentBusId; }
    public void setCurrentBusId(Long currentBusId) { this.currentBusId = currentBusId; }

    public String departureLabel() {
        return serviceDate + " " + departureTime;
    }

    @Override
    public String toString() {
        return "Trip " + tripId + " - " + departureLabel();
    }
}
