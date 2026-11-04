package com.vaahansetu.repository;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AllocationRepository {
    private final JdbcTemplate jdbcTemplate;

    public AllocationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findRecentRuns(int limit) {
        String sql = """
                SELECT allocation_run_id AS "allocationRunId",
                       session_id AS "sessionId",
                       status::text AS "status",
                       is_simulated AS "isSimulated",
                       total_demand AS "totalDemand",
                       total_accommodated AS "totalAccommodated",
                       total_unmet_demand AS "totalUnmetDemand",
                       total_unused_capacity AS "totalUnusedCapacity",
                       created_at AS "createdAt",
                       notes
                FROM allocation_runs
                ORDER BY created_at DESC
                LIMIT ?
                """;
        return jdbcTemplate.queryForList(sql, limit);
    }

    public Map<String, Object> findLatestRun() {
        List<Map<String, Object>> runs = findRecentRuns(1);
        return runs.isEmpty() ? null : runs.get(0);
    }
}
