-- =====================================================================
--  Refresh scheduled trips to today's date
--  -------------------------------------------------------------------
--  Run this in the Neon SQL Editor whenever the trip dropdowns go empty
--  because "today" has moved past the date the trips were inserted on.
--
--  What it does:
--    1. Updates all scheduled_trips rows to set service_date = today
--    2. Re-attaches the original time-of-day portions to today's date
--    3. Resets the active planning session's started_at to today
--    4. Resets trip_assignments timestamps to "now minus a few minutes"
--
--  Idempotent: safe to re-run on any day.
--  Run whenever the trip dropdown in the passenger UI is empty.
-- =====================================================================

BEGIN;

-- 1. Advance scheduled trips to today's date (preserving time-of-day)
UPDATE scheduled_trips
SET service_date          = CURRENT_DATE,
    departure_time        = CURRENT_DATE + (departure_time::time),
    expected_arrival_time = CURRENT_DATE + (expected_arrival_time::time)
WHERE service_date < CURRENT_DATE;

-- 2. Reset the active planning session to today
UPDATE planning_sessions
SET started_at = CURRENT_TIMESTAMP - INTERVAL '2 hours',
    ended_at    = NULL,
    status      = 'ACTIVE',
    simulation_elapsed_minutes = 0
WHERE status = 'ACTIVE';

-- 3. Refresh trip_assignments timestamps to recent (so they look "live")
UPDATE trip_assignments
SET assigned_at = CURRENT_TIMESTAMP - INTERVAL '30 minutes'
WHERE status = 'ACTIVE';

-- 4. Verify
SELECT '--- Verification ---' AS section;

SELECT 'trips on today' AS check_name,
       COUNT(*) AS expected_90
FROM scheduled_trips
WHERE service_date = CURRENT_DATE;

SELECT 'trips on past dates' AS check_name,
       COUNT(*) AS expected_0
FROM scheduled_trips
WHERE service_date < CURRENT_DATE;

SELECT '--- Trip detail (first 10) ---' AS section;
SELECT trip_id, route_id, service_date,
       departure_time::time AS dep_time,
       expected_arrival_time::time AS arr_time,
       status
FROM scheduled_trips
ORDER BY departure_time
LIMIT 10;

COMMIT;
