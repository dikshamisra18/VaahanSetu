package com.vaahansetu.repository;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Find a user by username (case-insensitive). Returns null if not found. */
    public Map<String, Object> findByUsername(String username) {
        String sql = """
                SELECT user_id   AS "userId",
                       full_name AS "fullName",
                       username,
                       password_hash AS "passwordHash",
                       role::text AS "role",
                       is_active AS "isActive"
                FROM users
                WHERE LOWER(username) = LOWER(?)
                  AND is_active = TRUE
                """;
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, username);
        return results.isEmpty() ? null : results.get(0);
    }
}
