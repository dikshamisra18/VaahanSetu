# Member 2 — backend integration requirements

This file distinguishes endpoints found in the uploaded repository from endpoints the Swing application still needs. Endpoint names below are proposals until the backend team freezes them in `docs/project-contract.md`.

## Existing endpoints found in the latest repository

| Method | Path | Current purpose |
|---|---|---|
| GET | `/api/routes` | List active routes |
| GET | `/api/routes/{routeId}/stops` | List active stops in route order |
| GET | `/api/routes/{routeId}/buses` | List buses allocated to one route |
| POST | `/api/passenger-journeys` | Register a passenger journey |
| GET | `/api/passenger-journeys/{journeyId}` | Retrieve a journey |
| GET | `/api/passenger-journeys/demand` | Current repository's route-level passenger count (not segment-level) |
| PATCH | `/api/passenger-journeys/{journeyId}/status` | Change passenger journey status |

The existing passenger journey controller currently accepts `routeId` and selects a trip in the backend; it does not yet match the final passenger request contract that explicitly sends `tripId`. This should be aligned by Member 1 / Member 3, not hidden in Swing.

The existing `GET /api/routes/{routeId}/buses` query currently returns bus ID, registration, capacity, location, trip ID and trip times. It does **not** return `rest_duration_minutes`, `rest_start`, or `rest_end`, and there is no fleet-wide bus endpoint in the latest backend. The backend `Bus` Java model has been updated in this handoff to include those three fields, but Member 1 still needs to expose them from a bus endpoint.

## Officer API requirements still missing from latest uploaded backend

| Method | Proposed path | Required response / behaviour | Owner to coordinate |
|---|---|---|---|
| POST | `/api/auth/login` | Authenticated user ID, display name, role, token/session | M1 |
| GET | `/api/officer/sessions/active` | Active session ID, name, elapsed simulation minutes | M1 |
| GET | `/api/officer/buses` | Bus list including `restDurationMinutes`, `restStart`, `restEnd` | M1 |
| GET | `/api/officer/routes` | Predefined active routes | M1 |
| GET | `/api/officer/trips` | Scheduled trips and current assignments | M1 |
| GET | `/api/officer/trips/{tripId}/demand` | One row per consecutive route segment, with registered, simulated, effective demand and current bus capacity | M4/M1 |
| PUT | `/api/officer/sessions/{sessionId}/selection` | Save selected route IDs and bus IDs | M1 |
| POST | `/api/admin/simulation/step` | Admin-only, selected trip + segment IDs; add persisted signed adjustments and advance clock by 2 minutes | M5/M1 |
| POST | `/api/officer/allocations` | Generate proposal; persist baseline and proposed summaries, assignments and segment results | M4/M1 |
| GET | `/api/officer/allocations/{runId}` | Retrieve the comparison and per-trip assignments | M4/M1 |
| POST | `/api/officer/allocations/{runId}/approve` | Validate freshness and transactionally apply assignments | M4/M1 |

## Bus model contract

The database migration `database/migrations/001_add_rest_slots_to_buses.sql` adds `rest_start TIME` and `rest_end TIME`. The backend Bus model should expose:

- `restDurationMinutes`: minimum rest after a completed trip.
- `restStart`: optional recurring fixed daily rest-window start (ISO local time, e.g. `13:00:00`).
- `restEnd`: optional recurring fixed daily rest-window end (ISO local time, e.g. `13:30:00`).

Both fixed-rest fields should be null together when a bus has no fixed daily rest window. Overnight windows are valid where `restEnd` is earlier than `restStart`.

## Important model consistency issue to resolve with Member 4

The current allocation module's `SegmentRow` and `allocation_segment_results` write one aggregated row per `(route, segment)` across trips. The agreed UI contract requires segment results to be distinguishable by scheduled `tripId`, because the same route can have multiple departures. Member 4 / Member 1 should add `trip_id` to allocation segment results and return it in the result DTO before the Swing comparison view is connected to real results.

The current `AllocationRepository` also loads all buses and treats `session_buses` as a selection/display flag, with comments allowing active buses outside the selected pool to be borrowed. The team must confirm whether the agreed rule is strict selected-pool allocation or this borrowing behaviour. The Member 2 demo currently restricts the mock allocation pool to selected buses.

## Integration rule

Do not build database queries into Swing panels. Implement API-backed classes behind the interfaces in `service/` and inject them through `DesktopServices`. Keep `MockServices` available as a demo fallback until all listed endpoints are implemented and verified.
