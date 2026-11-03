package vaahansetu.desktop.model;

/** A mock/transport representation of an anonymous passenger journey submission. */
public record PassengerJourney(long tripId, long boardingStopId,
                               long destinationStopId, int passengerCount) {
}
