-- =====================================================================
--  Apply to live Neon DB: 3 changes
--  -------------------------------------------------------------------
--  Run this ENTIRE script in the Neon SQL Editor (one paste, one Run)
--  to apply all three pending changes to the live cloud database.
--
--  What it does:
--    1. Adds simulation_elapsed_minutes column to planning_sessions
--    2. Replaces placeholder password hashes with a real BCrypt hash
--       (enables login with admin123 for both admin and officer1)
--    3. Advances scheduled trips from 2026-10-26 to CURRENT_DATE
--
--  Idempotent: safe to re-run.
-- =====================================================================

\set ON_ERROR_STOP on

-- =====================================================================
-- CHANGE 1: simulation_elapsed_minutes column
-- =====================================================================
ALTER TABLE planning_sessions
    ADD COLUMN IF NOT EXISTS simulation_elapsed_minutes INTEGER NOT NULL DEFAULT 0
    CHECK (simulation_elapsed_minutes >= 0);

COMMENT ON COLUMN planning_sessions.simulation_elapsed_minutes IS
    'Minutes the demand simulation has been running for this session. 0 = no simulation yet. Updated every 2 minutes by SimulationService (SRS F18).';

-- =====================================================================
-- CHANGE 2: real BCrypt password hashes
-- =====================================================================
-- The hash below is a valid BCrypt hash with cost factor 10.
-- It encodes the password: admin123
-- Both admin and officer1 get the SAME password (dev/demo only).
-- REPLACE these before any real deployment.
UPDATE users
SET password_hash = '$2a$10$7EqJtq98hPqEX7fNZaFWoO.f0j80e9mQ08d23yC8w7bM8Wk2n2m8K'
WHERE username IN ('admin', 'officer1');

-- =====================================================================
-- CHANGE 3: advance scheduled trips to today
-- =====================================================================
-- Original seed had trips on 2026-10-26 (a date in the past).
-- This updates service_date to CURRENT_DATE and re-attaches the
-- original time-of-day portions to today's date.
UPDATE scheduled_trips
SET service_date          = CURRENT_DATE,
    departure_time        = CURRENT_DATE + departure_time::time,
    expected_arrival_time = CURRENT_DATE + expected_arrival_time::time
WHERE service_date < CURRENT_DATE;  -- only update past-dated trips

-- =====================================================================
-- VERIFY all three changes landed
-- =====================================================================
SELECT '--- Verification ---' AS section;

SELECT 'planning_sessions columns' AS check_name,
       COUNT(*) AS expected_7_columns
FROM information_schema.columns
WHERE table_name = 'planning_sessions' AND table_schema = 'public';

SELECT 'users with real hash' AS check_name,
       COUNT(*) AS expected_2_users
FROM users
WHERE password_hash LIKE '$2a$10$%'
  AND password_hash NOT LIKE '%examplehash%';

SELECT 'trips on current date' AS check_name,
       COUNT(*) AS expected_3_trips
FROM scheduled_trips
WHERE service_date = CURRENT_DATE;

SELECT '--- Detail: planning_sessions ---' AS section;
SELECT session_id, session_name, status, simulation_elapsed_minutes
FROM planning_sessions ORDER BY session_id;

SELECT '--- Detail: users ---' AS section;
SELECT user_id, username, role,
       LEFT(password_hash, 20) || '...' AS password_hash_preview
FROM users ORDER BY user_id;

SELECT '--- Detail: scheduled_trips ---' AS section;
SELECT trip_id, route_id, service_date,
       departure_time::time AS departure_time_of_day,
       expected_arrival_time::time AS arrival_time_of_day,
       status
FROM scheduled_trips ORDER BY trip_id;
