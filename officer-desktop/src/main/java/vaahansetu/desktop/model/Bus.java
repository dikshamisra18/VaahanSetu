package vaahansetu.desktop.model;

import java.time.LocalTime;

/** Read-only bus data displayed by the officer console. */
public record Bus(
        long busId,
        String registrationNumber,
        int capacity,
        BusStatus status,
        String currentLocation,
        int restDurationMinutes,
        LocalTime restStart,
        LocalTime restEnd) {

    public boolean hasFixedRestWindow() {
        return restStart != null && restEnd != null && !restStart.equals(restEnd);
    }

    @Override
    public String toString() {
        String restSlot = hasFixedRestWindow() ? "; fixed rest " + restStart + "-" + restEnd : "; no fixed rest slot";
        return registrationNumber + " (" + capacity + " seats; minimum rest " + restDurationMinutes
                + " min" + restSlot + ")";
    }
}
