-- =====================================================================
--  Bus health check -- verifies the buses table is working correctly
--  Run this in the Neon SQL Editor on the vaahansetu database.
-- =====================================================================

-- =====================================================================
-- 1. ALL buses with all columns
-- =====================================================================
SELECT
    bus_id,
    registration_number,
    capacity,
    status,
    current_location,
    rest_duration_minutes,
    rest_start,
    rest_end,
    LEFT(notes, 30) AS notes_preview,
    created_at
FROM buses
ORDER BY bus_id;

-- =====================================================================
-- 2. CHECK constraint validation: rest slots must both be NULL or both set
--    (this is the constraint your teammate added in commit b81d4b1)
-- =====================================================================
SELECT
    bus_id,
    registration_number,
    rest_start,
    rest_end,
    CASE
        WHEN rest_start IS NULL AND rest_end IS NULL THEN 'OK: both NULL'
        WHEN rest_start IS NOT NULL AND rest_end IS NOT NULL THEN 'OK: both set'
        ELSE 'VIOLATION: only one is set'
    END AS constraint_check
FROM buses
ORDER BY bus_id;

-- =====================================================================
-- 3. ACTIVE buses only (the ones eligible for allocation)
-- =====================================================================
SELECT
    bus_id,
    registration_number,
    capacity,
    current_location,
    rest_duration_minutes,
    rest_start,
    rest_end
FROM buses
WHERE status = 'ACTIVE'
ORDER BY bus_id;

-- =====================================================================
-- 4. Buses currently in maintenance (cannot be allocated)
-- =====================================================================
SELECT
    bus_id,
    registration_number,
    capacity,
    current_location,
    notes
FROM buses
WHERE status = 'MAINTENANCE'
ORDER BY bus_id;

-- =====================================================================
-- 5. Buses selected for the active planning session (session 1)
--    These are the buses the allocation engine considers
-- =====================================================================
SELECT
    sb.session_id,
    b.bus_id,
    b.registration_number,
    b.capacity,
    b.status,
    b.rest_start,
    b.rest_end
FROM session_buses sb
JOIN buses b ON b.bus_id = sb.bus_id
WHERE sb.session_id = 1
ORDER BY b.bus_id;

-- =====================================================================
-- 6. Buses currently assigned to trips (the "baseline" allocation)
-- =====================================================================
SELECT
    ta.assignment_id,
    ta.trip_id,
    ta.bus_id,
    b.registration_number,
    b.capacity,
    ta.status AS assignment_status,
    b.status AS bus_status,
    ta.assigned_at
FROM trip_assignments ta
JOIN buses b ON b.bus_id = ta.bus_id
WHERE ta.status = 'ACTIVE'
ORDER BY ta.trip_id;

-- =====================================================================
-- 7. Try an invalid UPDATE -- should be rejected by the CHECK constraint
--    This sets rest_start to a value but leaves rest_end NULL.
--    Expect: ERROR: new row for relation "buses" violates check
--                  constraint "buses_check"
-- =====================================================================
-- (Uncomment the next 4 lines to actually test the constraint -- it will
--  raise an error and roll back. Safe to run, just demonstrates the
--  constraint works.)
--
-- UPDATE buses
-- SET rest_start = '12:00:00', rest_end = NULL
-- WHERE bus_id = 101;

-- =====================================================================
-- 8. Try an invalid INSERT -- capacity <= 0, should be rejected
-- =====================================================================
-- (Uncomment to test. Expect: ERROR: new row for relation "buses"
--  violates check constraint "buses_capacity_check")
--
-- INSERT INTO buses (registration_number, capacity, status)
-- VALUES ('TEST-0000', 0, 'ACTIVE');

-- =====================================================================
-- 9. Summary statistics
-- =====================================================================
SELECT
    COUNT(*) AS total_buses,
    COUNT(*) FILTER (WHERE status = 'ACTIVE') AS active_buses,
    COUNT(*) FILTER (WHERE status = 'MAINTENANCE') AS maintenance_buses,
    COUNT(*) FILTER (WHERE status = 'INACTIVE') AS inactive_buses,
    COUNT(*) FILTER (WHERE rest_start IS NOT NULL) AS with_rest_slot,
    COUNT(*) FILTER (WHERE rest_start IS NULL) AS without_rest_slot,
    SUM(capacity) AS total_capacity,
    AVG(capacity)::INTEGER AS avg_capacity
FROM buses;
