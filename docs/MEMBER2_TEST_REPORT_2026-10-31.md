# Member 2 Test Report — 2026-10-31

## Summary

The Member 2 Java Swing module compiles and its database-free demo checks pass. The existing allocation-engine checks also pass. The Swing window starts successfully under a virtual display. This verifies the local/mock demonstration path, not live PostgreSQL or REST integration.

## Test results

### Member 2 desktop smoke checks — 20 passed

- Bus model exposes minimum post-trip rest duration.
- Bus model exposes fixed daily rest start and end.
- Bus identifies a configured fixed rest window.
- ADMIN sees the two-minute simulation button.
- OFFICER does not see the admin-only simulation button.
- Trip 1001 has three consecutive route segments.
- Example A–B, B–C and C–D registered demand is 3, 6 and 4 respectively.
- Segment demand is associated with its own scheduled trip.
- Allocation creates a proposal.
- Running a proposal does not immediately change current bus assignments.
- Baseline and proposed segment results are returned.
- One simulation click advances the clock by two minutes.
- Simulation does not change registered passenger demand.
- Simulation adjustments are stored separately in the mock service.
- Effective demand applies adjustments and clamps at zero.
- Simulation invalidates an older proposal before approval.
- Trips remain available after proposal approval.
- Applying a fresh proposal updates the current bus assignment.

Run command used:

```bash
cd officer-desktop
xvfb-run -a ./test.sh
```

### Allocation engine checks — 12 passed

The existing checks cover per-segment demand, unmet-demand reduction, capacity-based bus choice, post-trip rest duration, fixed daily rest windows, bus reuse across non-overlapping trips, and exclusion of maintenance buses.

Run command used from repository root:

```bash
javac -encoding UTF-8 -d /tmp/vaahansetu-allocation-test \
  src/allocation/core/AllocationModels.java \
  src/allocation/core/AllocationEngine.java \
  tests/allocation/core/AllocationEngineCheck.java
java -cp /tmp/vaahansetu-allocation-test allocation.core.AllocationEngineCheck
```

### Compilation and GUI launch

- All `officer-desktop/src/main/java` files compiled with Java 21: PASS.
- GUI launch under `xvfb-run` for a five-second smoke window: PASS (the process remained open until the intentional timeout; no startup exception was printed).
- `javac -Xlint:all`: no compilation errors; serialization warnings remain on Swing classes and service-bearing panels.

## Not verified in this environment

- The Spring Boot backend could not be built or tested because Maven is not installed in this environment.
- No usable `psql` client or live PostgreSQL connection was available, so the schema, migration and constraint SQL tests were not executed against a database.
- The repository contains empty backend placeholders for `AllocationController`, `JourneyController`, `AllocationStatusService`, `JourneyService`, `RouteService`, `AllocationRepository`, `AllocationStatusResponse`, `JourneyResponse`, `ApiExceptionHandler`, and `JourneyServiceTest`.
- The Swing module therefore continues to use `MockServices`; it is not yet live-data-backed.

## Backend integration blockers

The shared backend currently exposes route and passenger journey endpoints, but not the complete officer API contract. The team still needs to implement authentication/authorization, fleet/bus details (including `restDurationMinutes`, `restStart`, `restEnd`), active planning session and selections, trips/current assignments, trip-specific segment demand, persisted simulation steps, allocation comparison, and transactional approval.

The backend `Bus` POJO already contains `restDurationMinutes`, `restStart`, and `restEnd`; the route-specific bus query does not yet return these fields and there is no fleet-wide officer bus endpoint.

The allocation module's segment result is currently aggregated per route/segment without a scheduled `tripId`; the shared result schema/API must distinguish trips that use the same route. The production rule for strict selected-bus-pool allocation versus borrowing an unselected bus also needs confirmation.

The current passenger journey endpoint accepts a `routeId` and chooses a trip on the server, whereas the agreed passenger flow is for the passenger to select a specific scheduled trip. Member 1 / Member 3 should align the passenger API contract.

## Conclusion

Member 2's local Swing prototype is verified for the mock/demo path. Full project completion still requires the backend and database integration tasks above. Do not describe the Swing module as connected to the live database until those endpoints are implemented and tested.
