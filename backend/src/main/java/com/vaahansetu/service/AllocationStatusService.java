package com.vaahansetu.service;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.vaahansetu.repository.AllocationRepository;

@Service
public class AllocationStatusService {
    private final AllocationRepository allocationRepository;

    public AllocationStatusService(AllocationRepository allocationRepository) {
        this.allocationRepository = allocationRepository;
    }

    public List<Map<String, Object>> getRecentRuns(int limit) {
        return allocationRepository.findRecentRuns(limit);
    }

    public Map<String, Object> getLatestRun() {
        return allocationRepository.findLatestRun();
    }
}
