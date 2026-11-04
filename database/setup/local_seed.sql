-- VaahanSetu local seed data
-- Uses current date for scheduled trips so they appear as "today"
-- Bcrypt hashes below are for:
--   admin    / admin123   -> $2a$10$0mg0VRy3jW.w9djT/rSRh.7GHBnbDEdSgaNHGr0oJfOXdcx5UqaZa
--   officer1 / officer123 -> $2a$10$QK/sA3IlBhwCdwoyKNx0gOqkf314Jw0EvMPXEtGzxjbVCwSdWDWni

-- ---------- users ----------
INSERT INTO users (user_id, full_name, username, password_hash, role, is_active) VALUES
    (1, 'System Admin',      'admin',    '$2a$10$0mg0VRy3jW.w9djT/rSRh.7GHBnbDEdSgaNHGr0oJfOXdcx5UqaZa', 'ADMIN',   TRUE),
    (2, 'Crowd Officer One', 'officer1', '$2a$10$QK/sA3IlBhwCdwoyKNx0gOqkf314Jw0EvMPXEtGzxjbVCwSdWDWni', 'OFFICER', TRUE)
ON CONFLICT (username) DO UPDATE
    SET password_hash = EXCLUDED.password_hash;
SELECT setval(pg_get_serial_sequence('users','user_id'), GREATEST((SELECT COALESCE(MAX(user_id),0) FROM users), 2));

-- ---------- buses ----------
INSERT INTO buses (bus_id, registration_number, capacity, status, current_location, rest_duration_minutes, rest_start, rest_end, notes) VALUES
    (101, 'KA-01-AA-1001', 45, 'ACTIVE',      'Central Depot',  15, '13:00:00', '13:30:00', 'Standard 45-seater'),
    (102, 'KA-01-AA-1002', 45, 'ACTIVE',      'Central Depot',  15, '13:00:00', '13:30:00', NULL),
    (103, 'KA-01-AA-1003', 35, 'MAINTENANCE', 'Workshop Bay 2',  0, NULL,       NULL,        'Brake service in progress'),
    (104, 'KA-02-BB-2001', 50, 'ACTIVE',      'East Depot',     20, '14:00:00', '14:30:00', NULL)
ON CONFLICT (registration_number) DO NOTHING;
SELECT setval(pg_get_serial_sequence('buses','bus_id'), GREATEST((SELECT COALESCE(MAX(bus_id),0) FROM buses), 104));

-- ---------- stops ----------
INSERT INTO stops (stop_id, stop_name, latitude, longitude, is_active) VALUES
    (1, 'Stop A (Central)',   12.971600, 77.594600, TRUE),
    (2, 'Stop B (Market)',    12.975000, 77.605000, TRUE),
    (3, 'Stop C (Tech Park)', 12.980000, 77.615000, TRUE),
    (4, 'Stop D (Airport)',   12.990000, 77.625000, TRUE),
    (5, 'Stop X (East Gate)', 12.965000, 77.640000, TRUE),
    (6, 'Stop Y (Junction)',  12.970000, 77.650000, TRUE),
    (7, 'Stop Z (Lake)',      12.975000, 77.660000, TRUE)
ON CONFLICT (stop_id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('stops','stop_id'), GREATEST((SELECT COALESCE(MAX(stop_id),0) FROM stops), 7));

-- ---------- routes ----------
INSERT INTO routes (route_id, route_code, route_name, origin_name, destination_name, estimated_duration_minutes, is_active) VALUES
    (1, 'R1', 'Central-Airport', 'Stop A (Central)',   'Stop D (Airport)', 90, TRUE),
    (2, 'R2', 'EastGate-Lake',   'Stop X (East Gate)', 'Stop Z (Lake)',    60, TRUE)
ON CONFLICT (route_code) DO NOTHING;
SELECT setval(pg_get_serial_sequence('routes','route_id'), GREATEST((SELECT COALESCE(MAX(route_id),0) FROM routes), 2));

-- ---------- route_stops ----------
INSERT INTO route_stops (route_id, stop_id, stop_sequence, scheduled_offset_minutes) VALUES
    (1, 1, 1,  0),
    (1, 2, 2, 25),
    (1, 3, 3, 55),
    (1, 4, 4, 90),
    (2, 5, 1,  0),
    (2, 6, 2, 30),
    (2, 7, 3, 60)
ON CONFLICT DO NOTHING;

-- ---------- scheduled_trips (TODAY's date) ----------
-- Use CURRENT_DATE so that trips always appear as today.
INSERT INTO scheduled_trips (trip_id, route_id, service_date, departure_time, expected_arrival_time, status) VALUES
    (1001, 1, CURRENT_DATE, (CURRENT_DATE || ' 08:00:00')::TIMESTAMP, (CURRENT_DATE || ' 09:30:00')::TIMESTAMP, 'SCHEDULED'),
    (1002, 1, CURRENT_DATE, (CURRENT_DATE || ' 14:00:00')::TIMESTAMP, (CURRENT_DATE || ' 15:30:00')::TIMESTAMP, 'SCHEDULED'),
    (2001, 2, CURRENT_DATE, (CURRENT_DATE || ' 09:00:00')::TIMESTAMP, (CURRENT_DATE || ' 10:00:00')::TIMESTAMP, 'SCHEDULED')
ON CONFLICT (route_id, departure_time) DO NOTHING;
SELECT setval(pg_get_serial_sequence('scheduled_trips','trip_id'), GREATEST((SELECT COALESCE(MAX(trip_id),0) FROM scheduled_trips), 2001));

-- ---------- planning_sessions (exactly one ACTIVE, with simulation clock) ----------
INSERT INTO planning_sessions (session_id, session_name, started_at, ended_at, created_by, status, simulation_elapsed_minutes) VALUES
    (1, 'Session ' || TO_CHAR(CURRENT_DATE, 'YYYY-MM-DD') || ' morning planning', NOW(), NULL, 1, 'ACTIVE', 0)
ON CONFLICT (session_id) DO NOTHING;
SELECT setval(pg_get_serial_sequence('planning_sessions','session_id'), GREATEST((SELECT COALESCE(MAX(session_id),0) FROM planning_sessions), 1));

-- ---------- session_buses / session_routes ----------
INSERT INTO session_buses (session_id, bus_id) VALUES (1, 101), (1, 102), (1, 104)
ON CONFLICT DO NOTHING;
INSERT INTO session_routes (session_id, route_id) VALUES (1, 1), (1, 2)
ON CONFLICT DO NOTHING;

-- ---------- trip_assignments ----------
INSERT INTO trip_assignments (assignment_id, trip_id, bus_id, assigned_at, assigned_by, status) VALUES
    (1, 1001, 101, NOW(), 1, 'ACTIVE'),
    (2, 1002, 102, NOW(), 1, 'ACTIVE'),
    (3, 2001, 104, NOW(), 1, 'ACTIVE')
ON CONFLICT DO NOTHING;
SELECT setval(pg_get_serial_sequence('trip_assignments','assignment_id'), GREATEST((SELECT COALESCE(MAX(assignment_id),0) FROM trip_assignments), 3));

-- ---------- passenger_journeys (sample data) ----------
-- journey 1: Stop A (1) -> Stop C (3), 1 passenger, BOARDED
-- journey 2: Stop B (2) -> Stop D (4), 2 passengers, REGISTERED  
-- journey 3: Stop A (1) -> Stop D (4), 1 passenger, COMPLETED
INSERT INTO passenger_journeys (journey_id, session_id, trip_id, boarding_stop_id, destination_stop_id,
                                passenger_count, journey_status, boarding_confirmed_at, submitted_at, passenger_reference) VALUES
    (1, 1, 1001, 1, 3, 1, 'BOARDED',    NOW() - INTERVAL '10 minutes', NOW() - INTERVAL '15 minutes', 'PJ-SAMPLE-1'),
    (2, 1, 1001, 2, 4, 2, 'REGISTERED', NULL,                           NOW() - INTERVAL '5 minutes',  'PJ-SAMPLE-2'),
    (3, 1, 1001, 1, 4, 1, 'COMPLETED',  NOW() - INTERVAL '8 minutes',  NOW() - INTERVAL '20 minutes', 'PJ-SAMPLE-3')
ON CONFLICT DO NOTHING;
SELECT setval(pg_get_serial_sequence('passenger_journeys','journey_id'), GREATEST((SELECT COALESCE(MAX(journey_id),0) FROM passenger_journeys), 3));
