package vaahansetu.desktop.model;

public record Route(long routeId, String routeCode, String routeName,
                    String originName, String destinationName,
                    int estimatedDurationMinutes, boolean active) {
    @Override
    public String toString() {
        return routeCode + " - " + routeName;
    }
}
