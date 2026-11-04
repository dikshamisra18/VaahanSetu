package com.vaahansetu.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.vaahansetu.service.JourneyService;

@RestController
@RequestMapping("/api/journeys")
@CrossOrigin(origins = "*")
public class JourneyController {
    private final JourneyService journeyService;

    public JourneyController(JourneyService journeyService) {
        this.journeyService = journeyService;
    }

    @GetMapping("/{journeyId}")
    public ResponseEntity<Map<String, Object>> getJourney(@PathVariable Long journeyId) {
        Map<String, Object> journey = journeyService.getJourneyById(journeyId);
        if (journey == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(journey);
    }
}
