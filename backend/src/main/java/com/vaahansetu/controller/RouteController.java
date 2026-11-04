package com.vaahansetu.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vaahansetu.repository.JourneyRepository;
import com.vaahansetu.repository.RouteRepository;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RouteController {

    private final RouteRepository routeRepository;
    private final JourneyRepository journeyRepository;

    public RouteController(RouteRepository routeRepository, JourneyRepository journeyRepository) {
        this.routeRepository = routeRepository;
        this.journeyRepository = journeyRepository;
    }

    // Get all active routes
    @GetMapping("/routes")
    public List<Map<String, Object>> getRoutes() {
        return routeRepository.findAllActiveRoutes();
    }

    // Get stops for a selected route
    @GetMapping("/routes/{routeId}/stops")
    public List<Map<String, Object>> getStops(
            @PathVariable Long routeId) {
        return routeRepository.findStopsByRouteId(routeId);
    }

    // Get buses allocated to a selected route
    @GetMapping("/routes/{routeId}/buses")
    public List<Map<String, Object>> getAllocatedBuses(
            @PathVariable Long routeId) {
        return routeRepository.findAllocatedBuses(routeId);
    }

    // Get today's scheduled trips for a route
    @GetMapping("/routes/{routeId}/trips")
    public List<Map<String, Object>> getRouteTrips(
            @PathVariable Long routeId) {
        return journeyRepository.findTripsForRoute(routeId);
    }
}
