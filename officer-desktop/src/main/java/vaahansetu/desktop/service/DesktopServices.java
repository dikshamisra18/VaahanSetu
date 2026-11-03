package vaahansetu.desktop.service;

/** Dependency bundle passed to the Swing screens. Replace demo services with API-backed services here. */
public record DesktopServices(
        AuthService auth,
        BusService buses,
        RouteService routes,
        TripService trips,
        PlanningService planning,
        DemandService demand,
        SimulationService simulation,
        AllocationService allocation) {
}
