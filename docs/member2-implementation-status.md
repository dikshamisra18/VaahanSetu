# Member 2 implementation status

## Included in `officer-desktop/`

- Java Swing login with local demo ADMIN and OFFICER accounts.
- Read-only predefined bus table, including `rest_duration_minutes`, `rest_start`, and `rest_end`.
- Read-only predefined routes and ordered stops.
- Read-only scheduled trips and current assignments, including assigned bus rest information.
- Planning-session route and bus selection UI.
- Segment-level registered / simulated / effective demand display.
- Admin-only manual two-minute simulation control.
- Baseline-versus-proposed segment comparison.
- Per-trip proposed bus assignment table with `UNCHANGED`, `REASSIGNED`, and `UNASSIGNED` plus reason.
- Separate proposal and approval actions.
- Mock service implementations so the desktop can be run without a database or backend.
- Database-free smoke tests and Windows/Linux run scripts.

## Verified in this workspace (2026-10-31)

- Java 21 compilation of all Member 2 main source files: passed.
- Expanded Member 2 smoke checks: 20 assertions passed. These cover bus rest fields, ADMIN/OFFICER simulation-button visibility, per-trip segment demand, baseline/proposal separation, two-minute simulation, separation of registered and simulated demand, stale-proposal invalidation, and assignment update after approval.
- Existing allocation-engine checks in `src/allocation/core`: 12 checks passed, including minimum post-trip rest, fixed daily rest windows and overnight-window support.
- Swing application launch smoke check under Xvfb: window remained running for the five-second check without a startup exception.
- `javac -Xlint:all` completed with Swing serialization warnings only; no compile errors.

Commands used:

```bash
cd officer-desktop
xvfb-run -a ./test.sh
find src/main/java -name '*.java' -print0 | xargs -0 javac -encoding UTF-8 -d out
cd ..
javac -encoding UTF-8 -d /tmp/vaahansetu-allocation-test \
  src/allocation/core/AllocationModels.java \
  src/allocation/core/AllocationEngine.java \
  tests/allocation/core/AllocationEngineCheck.java
java -cp /tmp/vaahansetu-allocation-test allocation.core.AllocationEngineCheck
```

The Windows equivalents `test.bat` and `compile.bat` are included but were not executed in this Linux test environment.

## Not yet complete / requires team backend work

The latest uploaded backend has some passenger/route endpoints, but it does not yet provide the complete officer APIs needed to make the Swing UI live-data-backed. The current Swing application therefore uses in-memory mock services. The API gaps are listed in `officer-desktop/docs/BACKEND_INTEGRATION_REQUIREMENTS.md`.

The backend `Bus` model already includes `restDurationMinutes`, `restStart`, and `restEnd`; the remaining gap is exposing these values through a fleet/bus REST endpoint and loading them into the desktop service.

Specifically, the team still needs to implement and agree:

- Admin/officer authentication endpoint and authorization.
- Officer bus endpoint including rest fields.
- Active planning session and route/bus selection endpoints.
- Scheduled-trip endpoint.
- Per-trip/per-segment demand endpoint.
- Simulation-step endpoint with persisted adjustments and session clock.
- Allocation run, comparison result and transactional approval endpoints.

The current shared allocation module writes segment result rows aggregated per route/segment, without `trip_id`. The team has agreed that the UI needs to distinguish the same route's separate scheduled trips, so Member 1 / Member 4 should align the schema, result model and repository before real comparison integration.

The current allocation repository treats `session_buses` as a selection/display flag and may borrow an active bus outside the selected pool. The current Member 2 demo enforces the selected bus pool. Member 4 should confirm and align the production rule.

## Security note

The backend datasource configuration now reads `DB_URL`, `DB_USER`, and `DB_PASSWORD` from environment variables, with local development defaults only. A database credential was visible in a team screenshot shared in conversation. The database owner should rotate it in the provider dashboard and share the replacement privately. Never commit credentials or real `.env` files.
