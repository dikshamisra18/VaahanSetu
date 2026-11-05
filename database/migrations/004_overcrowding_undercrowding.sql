-- =====================================================================
--  Migration 004: add overcrowding + undercrowding sample journeys
--  -------------------------------------------------------------------
--  This migration adds passenger journeys that create VISIBLE overcrowding
--  on some segments (demand > bus capacity) and undercrowding on others
--  (demand << bus capacity), so the allocation algorithm has something
--  meaningful to demonstrate.
--
--  Strategy:
--    1. Pick a few high-traffic trips and add MANY passengers to them,
--       exceeding the 60-seat bus capacity on key segments (overcrowding)
--    2. Pick a few low-traffic trips with FEW passengers (undercrowding)
--    3. Use realistic boarding/destination patterns on the routes
--
--  All journeys are tagged with passenger_reference starting with
--  'DEMO-OVER-' or 'DEMO-UNDER-' so they can be cleaned up later if needed.
--
--  Idempotent: uses ON CONFLICT (passenger_reference) DO NOTHING so
--  re-running is safe. Does NOT delete existing journeys.
-- =====================================================================

\set ON_ERROR_STOP on
BEGIN;

-- First, ensure the active planning session is fresh (in case it was stale)
UPDATE planning_sessions
SET started_at = CURRENT_TIMESTAMP - INTERVAL '2 hours',
    ended_at    = NULL,
    status      = 'ACTIVE',
    simulation_elapsed_minutes = 0
WHERE status = 'ACTIVE';

-- Refresh trip dates to today (in case the seed was applied on a past day)
UPDATE scheduled_trips
SET service_date          = CURRENT_DATE,
    departure_time        = CURRENT_DATE + (departure_time::time),
    expected_arrival_time = CURRENT_DATE + (expected_arrival_time::time)
WHERE service_date < CURRENT_DATE;

-- =====================================================================
-- OVERCROWDING: add ~80 passengers spread across 3 trips on Route 1
-- (Route 10: Secunderabad -> Sanath Nagar, 5 trips today)
-- Bus capacity = 60. We'll target segments with 70-90 passengers each.
-- =====================================================================

-- Trip 1001 (Route 1, departs 06:30) -- MORNING PEAK
-- Route 1 stops: 1=Secunderabad, 2=Patny, 3=Plaza, 4=Paradise, ... 20=Sanath Nagar
-- Add 30 passengers going from Secunderabad(1) to Ameerpet(13) -- long trip,
-- occupies segments 1-12, contributing to heavy load on the early segments.
INSERT INTO passenger_journeys
    (session_id, trip_id, boarding_stop_id, destination_stop_id, passenger_count,
     journey_status, submitted_at, passenger_reference)
SELECT 1, 1001, 1, 13, 30, 'BOARDED',
       CURRENT_TIMESTAMP - INTERVAL '45 minutes',
       'DEMO-OVER-1001-A'
WHERE NOT EXISTS (SELECT 1 FROM passenger_journeys WHERE passenger_reference = 'DEMO-OVER-1001-A');

-- 25 more passengers going Patny(2) to Sanath Nagar(20) -- occupies segments 2-19
INSERT INTO passenger_journeys
    (session_id, trip_id, boarding_stop_id, destination_stop_id, passenger_count,
     journey_status, submitted_at, passenger_reference)
SELECT 1, 1001, 2, 20, 25, 'BOARDED',
       CURRENT_TIMESTAMP - INTERVAL '40 minutes',
       'DEMO-OVER-1001-B'
WHERE NOT EXISTS (SELECT 1 FROM passenger_journeys WHERE passenger_reference = 'DEMO-OVER-1001-B');

-- 20 more from Plaza(3) to Ameerpet(13)
INSERT INTO passenger_journeys
    (session_id, trip_id, boarding_stop_id, destination_stop_id, passenger_count,
     journey_status, submitted_at, passenger_reference)
SELECT 1, 1001, 3, 13, 20, 'BOARDED',
       CURRENT_TIMESTAMP - INTERVAL '38 minutes',
       'DEMO-OVER-1001-C'
WHERE NOT EXISTS (SELECT 1 FROM passenger_journeys WHERE passenger_reference = 'DEMO-OVER-1001-C');

-- Trip 1002 (Route 1, departs 09:00) -- MID-MORNING
-- 30 passengers from Secunderabad(1) to ESI Hospital(17)
INSERT INTO passenger_journeys
    (session_id, trip_id, boarding_stop_id, destination_stop_id, passenger_count,
     journey_status, submitted_at, passenger_reference)
SELECT 1, 1002, 1, 17, 30, 'REGISTERED',
       CURRENT_TIMESTAMP - INTERVAL '20 minutes',
       'DEMO-OVER-1002-A'
WHERE NOT EXISTS (SELECT 1 FROM passenger_journeys WHERE passenger_reference = 'DEMO-OVER-1002-A');

-- 25 from Paradise(4) to Sanath Nagar(20)
INSERT INTO passenger_journeys
    (session_id, trip_id, boarding_stop_id, destination_stop_id, passenger_count,
     journey_status, submitted_at, passenger_reference)
SELECT 1, 1002, 4, 20, 25, 'REGISTERED',
       CURRENT_TIMESTAMP - INTERVAL '18 minutes',
       'DEMO-OVER-1002-B'
WHERE NOT EXISTS (SELECT 1 FROM passenger_journeys WHERE passenger_reference = 'DEMO-OVER-1002-B');

-- Trip 1003 (Route 1, departs 12:00) -- LUNCH HOUR
-- 35 from Secunderabad(1) to Sanath Nagar(20) -- full route
INSERT INTO passenger_journeys
    (session_id, trip_id, boarding_stop_id, destination_stop_id, passenger_count,
     journey_status, submitted_at, passenger_reference)
SELECT 1, 1003, 1, 20, 35, 'REGISTERED',
       CURRENT_TIMESTAMP - INTERVAL '10 minutes',
       'DEMO-OVER-1003-A'
WHERE NOT EXISTS (SELECT 1 FROM passenger_journeys WHERE passenger_reference = 'DEMO-OVER-1003-A');

-- =====================================================================
-- OVERCROWDING on Route 5 (10H: Kondapur -> Secunderabad, 34 stops)
-- Trip 1021 (departs 09:00)
-- =====================================================================
-- Route 5 stops: 1=Kondapur, ... , 34=Secunderabad
-- 40 passengers from Kondapur(1) to Ameerpet(21)
INSERT INTO passenger_journeys
    (session_id, trip_id, boarding_stop_id, destination_stop_id, passenger_count,
     journey_status, submitted_at, passenger_reference)
SELECT 1, 1021, (SELECT stop_id FROM stops WHERE stop_name = 'Kondapur'),
       (SELECT stop_id FROM stops WHERE stop_name = 'Ameerpet'),
       40, 'REGISTERED',
       CURRENT_TIMESTAMP - INTERVAL '25 minutes',
       'DEMO-OVER-1021-A'
WHERE NOT EXISTS (SELECT 1 FROM passenger_journeys WHERE passenger_reference = 'DEMO-OVER-1021-A');

-- 30 from HITEC City Cyber Towers to Secunderabad
INSERT INTO passenger_journeys
    (session_id, trip_id, boarding_stop_id, destination_stop_id, passenger_count,
     journey_status, submitted_at, passenger_reference)
SELECT 1, 1021,
       (SELECT stop_id FROM stops WHERE stop_name = 'HITEC City Cyber Towers'),
       (SELECT stop_id FROM stops WHERE stop_name = 'Secunderabad'),
       30, 'REGISTERED',
       CURRENT_TIMESTAMP - INTERVAL '22 minutes',
       'DEMO-OVER-1021-B'
WHERE NOT EXISTS (SELECT 1 FROM passenger_journeys WHERE passenger_reference = 'DEMO-OVER-1021-B');

-- =====================================================================
-- UNDERCROWDING: trips with very few passengers (1-3 total)
-- These will show as "unused capacity" in the allocation report.
-- =====================================================================

-- Trip 1004 (Route 1, departs 15:00) -- only 2 passengers
INSERT INTO passenger_journeys
    (session_id, trip_id, boarding_stop_id, destination_stop_id, passenger_count,
     journey_status, submitted_at, passenger_reference)
SELECT 1, 1004, 1, 13, 2, 'REGISTERED',
       CURRENT_TIMESTAMP - INTERVAL '5 minutes',
       'DEMO-UNDER-1004-A'
WHERE NOT EXISTS (SELECT 1 FROM passenger_journeys WHERE passenger_reference = 'DEMO-UNDER-1004-A');

-- Trip 1005 (Route 1, departs 18:00) -- only 1 passenger
INSERT INTO passenger_journeys
    (session_id, trip_id, boarding_stop_id, destination_stop_id, passenger_count,
     journey_status, submitted_at, passenger_reference)
SELECT 1, 1005, 4, 20, 1, 'REGISTERED',
       CURRENT_TIMESTAMP - INTERVAL '2 minutes',
       'DEMO-UNDER-1005-A'
WHERE NOT EXISTS (SELECT 1 FROM passenger_journeys WHERE passenger_reference = 'DEMO-UNDER-1005-A');

-- =====================================================================
-- Verify
-- =====================================================================
SELECT '--- Overcrowding/Undercrowding verification ---' AS section;

-- Per-trip demand on Route 1
SELECT 'Route 1 demand by trip' AS check_name,
       t.trip_id,
       t.dep_time,
       SUM(pj.passenger_count) AS total_passengers,
       CASE WHEN SUM(pj.passenger_count) > 60 THEN 'OVERCROWDED'
            WHEN SUM(pj.passenger_count) < 10 THEN 'UNDERCROWDED'
            ELSE 'NORMAL' END AS load_category
FROM (
    SELECT 1001 AS trip_id, '06:30' AS dep_time UNION ALL
    SELECT 1002, '09:00' UNION ALL
    SELECT 1003, '12:00' UNION ALL
    SELECT 1004, '15:00' UNION ALL
    SELECT 1005, '18:00'
) t
LEFT JOIN passenger_journeys pj ON pj.trip_id = t.trip_id
GROUP BY t.trip_id, t.dep_time
ORDER BY t.trip_id;

-- Total demo journeys added
SELECT 'Demo journeys added' AS check_name,
       COUNT(*) FILTER (WHERE passenger_reference LIKE 'DEMO-OVER-%') AS overcrowding_count,
       COUNT(*) FILTER (WHERE passenger_reference LIKE 'DEMO-UNDER-%') AS undercrowding_count,
       COUNT(*) AS total_demo
FROM passenger_journeys
WHERE passenger_reference LIKE 'DEMO-%';

COMMIT;
