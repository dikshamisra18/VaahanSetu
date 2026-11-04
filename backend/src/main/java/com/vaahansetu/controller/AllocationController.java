package com.vaahansetu.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.vaahansetu.service.AllocationStatusService;

@RestController
@RequestMapping("/api/allocations")
@CrossOrigin(origins = "*")
public class AllocationController {
    private final AllocationStatusService allocationStatusService;

    public AllocationController(AllocationStatusService allocationStatusService) {
        this.allocationStatusService = allocationStatusService;
    }

    @GetMapping("/recent")
    public ResponseEntity<List<Map<String, Object>>> getRecentAllocations(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(allocationStatusService.getRecentRuns(limit));
    }

    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> getLatestAllocation() {
        Map<String, Object> latest = allocationStatusService.getLatestRun();
        if (latest == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(latest);
    }
}
