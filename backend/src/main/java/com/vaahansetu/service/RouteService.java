package com.vaahansetu.service;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.vaahansetu.repository.RouteRepository;

@Service
public class RouteService {
    private final RouteRepository routeRepository;

    public RouteService(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    public List<Map<String, Object>> getAllActiveRoutes() {
        return routeRepository.findAllActiveRoutes();
    }

    public List<Map<String, Object>> getStopsForRoute(Long routeId) {
        return routeRepository.findStopsByRouteId(routeId);
    }

    public List<Map<String, Object>> getAllocatedBusesForRoute(Long routeId) {
        return routeRepository.findAllocatedBuses(routeId);
    }
}
