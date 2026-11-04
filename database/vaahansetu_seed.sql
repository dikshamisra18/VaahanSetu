-- =====================================================================
--  VaahanSetu  --  small sample seed data (PDF section 8:
--  "Request SQL in dependency order, constraints, indexes, comments,
--   and small sample data.")
--  This data is intentionally small and only verifies FK integrity,
--  enum values, partial-unique rules and trigger behaviour.
--  It is NOT representative of production volumes.
-- =====================================================================

\set ON_ERROR_STOP on

-- ---------- users (admin + officer) ----------
-- ⚠️  SECURITY WARNING ⚠️
-- The password hashes below are REAL bcrypt hashes for the dev/demo
-- passwords: admin123 (both admin and officer1).
-- These are committed for student lab convenience only.
-- REPLACE THEM with hashes of strong, unique passwords before ANY
-- non-dev deployment. To generate a new hash:
--   python -c "import bcrypt; print(bcrypt.hashpw(b'YourRealPwd', bcrypt.gensalt(10)).decode())"
INSERT INTO users (user_id, full_name, username, password_hash, role, is_active) VALUES
    (1, 'System Administrator', 'admin',   '$2a$10$7EqJtq98hPqEX7fNZaFWoO.f0j80e9mQ08d23yC8w7bM8Wk2n2m8K', 'ADMIN',   TRUE),
    (2, 'Crowd Officer One',    'officer1','$2a$10$7EqJtq98hPqEX7fNZaFWoO.f0j80e9mQ08d23yC8w7bM8Wk2n2m8K', 'OFFICER', TRUE);
SELECT setval(pg_get_serial_sequence('users','user_id'), (SELECT MAX(user_id) FROM users));

-- ---------- buses ----------
INSERT INTO buses (bus_id, registration_number, capacity, status, current_location, rest_duration_minutes, rest_start, rest_end, notes) VALUES
    (101, 'KA-01-AA-1001', 45, 'ACTIVE',     'Central Depot', 15, '13:00:00', '13:30:00', 'Standard 45-seater'),
    (102, 'KA-01-AA-1002', 45, 'ACTIVE',     'Central Depot', 15, '13:00:00', '13:30:00', NULL),
    (103, 'KA-01-AA-1003', 35, 'MAINTENANCE','Workshop Bay 2', 0, NULL,       NULL,        'Brake service in progress'),
    (104, 'KA-02-BB-2001', 50, 'ACTIVE',     'East Depot',    20, '14:00:00', '14:30:00', NULL);
SELECT setval(pg_get_serial_sequence('buses','bus_id'), (SELECT MAX(bus_id) FROM buses));

-- ---------- stops (route R1: A -> B -> C -> D, route R2: X -> Y -> Z) ----------
INSERT INTO stops (stop_id, stop_name, latitude, longitude, is_active) VALUES
    (1, 'Stop A (Central)',  12.971600, 77.594600, TRUE),
    (2, 'Stop B (Market)',   12.975000, 77.605000, TRUE),
    (3, 'Stop C (Tech Park)',12.980000, 77.615000, TRUE),
    (4, 'Stop D (Airport)',  12.990000, 77.625000, TRUE),
    (5, 'Stop X (East Gate)',12.965000, 77.640000, TRUE),
    (6, 'Stop Y (Junction)', 12.970000, 77.650000, TRUE),
    (7, 'Stop Z (Lake)',     12.975000, 77.660000, TRUE);
SELECT setval(pg_get_serial_sequence('stops','stop_id'), (SELECT MAX(stop_id) FROM stops));

-- ---------- routes ----------
INSERT INTO routes (route_id, route_code, route_name, origin_name, destination_name, estimated_duration_minutes, is_active) VALUES
    (1, 'R1', 'Central-Airport',     'Stop A (Central)',  'Stop D (Airport)',  90, TRUE),
    (2, 'R2', 'EastGate-Lake',       'Stop X (East Gate)','Stop Z (Lake)',     60, TRUE);
SELECT setval(pg_get_serial_sequence('routes','route_id'), (SELECT MAX(route_id) FROM routes));

-- ---------- route_stops (ordered) ----------
-- Route 1: A(1) -> B(2) -> C(3) -> D(4)
INSERT INTO route_stops (route_id, stop_id, stop_sequence, scheduled_offset_minutes) VALUES
    (1, 1, 1,  0),
    (1, 2, 2, 25),
    (1, 3, 3, 55),
    (1, 4, 4, 90);
-- Route 2: X(5) -> Y(6) -> Z(7)
INSERT INTO route_stops (route_id, stop_id, stop_sequence, scheduled_offset_minutes) VALUES
    (2, 5, 1,  0),
    (2, 6, 2, 30),
    (2, 7, 3, 60);

-- ---------- scheduled_trips ----------
-- Two trips on route 1 (morning + afternoon), one trip on route 2.
INSERT INTO scheduled_trips (trip_id, route_id, service_date, departure_time, expected_arrival_time, status) VALUES
    (1001, 1, DATE '2026-10-26', TIMESTAMP '2026-10-26 08:00:00', TIMESTAMP '2026-10-26 09:30:00', 'SCHEDULED'),
    (1002, 1, DATE '2026-10-26', TIMESTAMP '2026-10-26 14:00:00', TIMESTAMP '2026-10-26 15:30:00', 'SCHEDULED'),
    (2001, 2, DATE '2026-10-26', TIMESTAMP '2026-10-26 09:00:00', TIMESTAMP '2026-10-26 10:00:00', 'SCHEDULED');
SELECT setval(pg_get_serial_sequence('scheduled_trips','trip_id'), (SELECT MAX(trip_id) FROM scheduled_trips));

-- ---------- planning_sessions (exactly one ACTIVE) ----------
INSERT INTO planning_sessions (session_id, session_name, started_at, ended_at, created_by, status) VALUES
    (1, 'Session 2026-10-26 morning planning', TIMESTAMPTZ '2026-10-26 07:00:00+05:30', NULL, 1, 'ACTIVE');
SELECT setval(pg_get_serial_sequence('planning_sessions','session_id'), (SELECT MAX(session_id) FROM planning_sessions));

-- ---------- session_buses / session_routes ----------
INSERT INTO session_buses (session_id, bus_id) VALUES (1, 101), (1, 102), (1, 104);
INSERT INTO session_routes (session_id, route_id) VALUES (1, 1), (1, 2);

-- ---------- trip_assignments (baseline; one ACTIVE per trip) ----------
INSERT INTO trip_assignments (assignment_id, trip_id, bus_id, assigned_at, assigned_by, status) VALUES
    (1, 1001, 101, TIMESTAMPTZ '2026-10-26 07:05:00+05:30', 1, 'ACTIVE'),
    (2, 1002, 102, TIMESTAMPTZ '2026-10-26 07:05:00+05:30', 1, 'ACTIVE'),
    (3, 2001, 104, TIMESTAMPTZ '2026-10-26 07:05:00+05:30', 1, 'ACTIVE');
SELECT setval(pg_get_serial_sequence('trip_assignments','assignment_id'), (SELECT MAX(assignment_id) FROM trip_assignments));

-- ---------- passenger_journeys ----------
-- All use trip 1001 on route 1 (A->B->C->D).
-- Journey A->C contributes to segments A-B and B-C.
-- Journey B->D contributes to segments B-C and C-D.
-- Journey A->D contributes to A-B, B-C, C-D.
INSERT INTO passenger_journeys (journey_id, session_id, trip_id, boarding_stop_id, destination_stop_id,
                                passenger_count, journey_status, boarding_confirmed_at, alighting_confirmed_at, passenger_reference) VALUES
    (1, 1, 1001, 1, 3, 1, 'BOARDED',   TIMESTAMPTZ '2026-10-26 08:01:00+05:30', NULL,                            'PJ-A1B2C3'),
    (2, 1, 1001, 2, 4, 2, 'REGISTERED',NULL,                                       NULL,                            'PJ-D4E5F6'),
    (3, 1, 1001, 1, 4, 1, 'COMPLETED', TIMESTAMPTZ '2026-10-26 08:00:30+05:30', TIMESTAMPTZ '2026-10-26 09:25:00+05:30', 'PJ-G7H8I9'),
    (4, 1, 1001, 1, 2, 1, 'UNCONFIRMED',NULL,                                     NULL,                            'PJ-J1K2L3');
SELECT setval(pg_get_serial_sequence('passenger_journeys','journey_id'), (SELECT MAX(journey_id) FROM passenger_journeys));

-- ---------- allocation_runs (baseline + proposed for the same session) ----------
INSERT INTO allocation_runs (allocation_run_id, session_id, created_by, status, is_simulated,
                             total_demand, total_accommodated, total_unmet_demand, total_unused_capacity, notes) VALUES
    (1, 1, 2, 'APPROVED', FALSE, 5, 5, 0, 130, 'Baseline allocation: keep current bus assignments.'),
    (2, 1, 2, 'PROPOSED', FALSE, 5, 5, 0,  10, 'Proposed allocation: swap bus 102 -> 104 on trip 1002 to free capacity.');
SELECT setval(pg_get_serial_sequence('allocation_runs','allocation_run_id'), (SELECT MAX(allocation_run_id) FROM allocation_runs));

-- ---------- allocation_assignments (one row per trip per run) ----------
-- Baseline run 1: all UNCHANGED
INSERT INTO allocation_assignments (allocation_run_id, trip_id, previous_bus_id, proposed_bus_id, assignment_type, reason) VALUES
    (1, 1001, 101, 101, 'UNCHANGED', 'Baseline keeps bus 101.'),
    (1, 1002, 102, 102, 'UNCHANGED', 'Baseline keeps bus 102.'),
    (1, 2001, 104, 104, 'UNCHANGED', 'Baseline keeps bus 104.');
-- Proposed run 2: trip 1002 REASSIGNED 102 -> 104
INSERT INTO allocation_assignments (allocation_run_id, trip_id, previous_bus_id, proposed_bus_id, assignment_type, reason) VALUES
    (2, 1001, 101, 101, 'UNCHANGED',  'No change.'),
    (2, 1002, 102, 104, 'REASSIGNED', 'Larger bus 104 (50 seats) frees capacity on segment B-C for trip 1002.'),
    (2, 2001, 104, 104, 'UNCHANGED',  'No change.');

-- ---------- allocation_segment_results (proposed run 2, route 1 segments) ----------
-- Per-segment demand (all journeys counted regardless of status; team may exclude UNCONFIRMED):
--   A-B : journeys 1 (A->C, pc=1) + 3 (A->D, pc=1) + 4 (A->B, pc=1) = 3
--   B-C : journeys 1 (A->C, pc=1) + 2 (B->D, pc=2) + 3 (A->D, pc=1) = 4
--   C-D : journeys 2 (B->D, pc=2) + 3 (A->D, pc=1)                  = 3
INSERT INTO allocation_segment_results (allocation_run_id, route_id, segment_start_stop_id, segment_end_stop_id,
                                        demand, allocated_capacity, accommodated, unmet_demand, unused_capacity) VALUES
    (2, 1, 1, 2, 3, 45, 3, 0, 42),  -- A-B
    (2, 1, 2, 3, 4, 45, 4, 0, 41),  -- B-C
    (2, 1, 3, 4, 3, 45, 3, 0, 42);  -- C-D

-- ---------- segment_demand_adjustments (a single simulated +/- adjustment) ----------
INSERT INTO segment_demand_adjustments (session_id, route_id, segment_start_stop_id, segment_end_stop_id, demand_delta, is_simulated) VALUES
    (1, 1, 2, 3, +7, TRUE),   -- simulated +7 on segment B-C of route 1
    (1, 1, 3, 4, -5, TRUE);   -- simulated -5 on segment C-D of route 1
