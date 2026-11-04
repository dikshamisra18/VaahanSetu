-- =====================================================================
--  Migration 002: add simulation_elapsed_minutes to planning_sessions
--  -------------------------------------------------------------------
--  Adds a column to track how many minutes the demand simulation has
--  been running for a planning session. Defaults to 0 (no simulation
--  yet). The Java SimulationService is expected to UPDATE this column
--  every 2 minutes while simulation is active (per SRS F18).
--
--  Idempotent: re-running is safe (uses IF NOT EXISTS).
--  Non-destructive: does NOT drop or alter existing data.
-- =====================================================================

ALTER TABLE planning_sessions
    ADD COLUMN IF NOT EXISTS simulation_elapsed_minutes INTEGER NOT NULL DEFAULT 0
    CHECK (simulation_elapsed_minutes >= 0);

COMMENT ON COLUMN planning_sessions.simulation_elapsed_minutes IS
    'Minutes the demand simulation has been running for this session. 0 = no simulation yet. Updated every 2 minutes by SimulationService (SRS F18).';
