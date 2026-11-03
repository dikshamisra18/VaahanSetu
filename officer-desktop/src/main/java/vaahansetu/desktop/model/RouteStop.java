package vaahansetu.desktop.model;

public record RouteStop(long routeId, long stopId, int sequence, String stopName) {
    @Override
    public String toString() {
        return sequence + ". " + stopName;
    }
}
