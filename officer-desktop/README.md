# VaahanSetu — Member 2 Officer Desktop (Java Swing)

This folder contains the Member 2 desktop application. It is a standalone Java 17+ Swing module. It currently runs with in-memory demo services so the UI can be built and demonstrated while the shared officer REST APIs are completed.

## Run on Windows

1. Install JDK 17 or newer.
2. Open this `officer-desktop` folder in VS Code.
3. Choose **Terminal → New Terminal**.
4. Run:

   ```powershell
   .\compile.bat
   ```

Demo accounts:

- Admin: `admin` / `admin123`
- Officer: `officer` / `officer123`

These credentials are local mock accounts only. They are not production authentication.

## Run smoke tests

On Windows PowerShell:

```powershell
.\test.bat
```

On Linux with a display server:

```bash
./test.sh
```

On a headless Linux machine with Xvfb installed:

```bash
xvfb-run -a ./test.sh
```

The smoke test checks route-segment demand calculation, separation of registered and simulated demand, the two-minute simulation clock, baseline/proposed output, and proposal approval using mock data.

## Screens and behaviour

- **Login:** demo ADMIN and OFFICER roles.
- **Buses:** read-only predefined fleet view, including capacity, status, minimum post-trip rest duration, fixed daily rest start, and fixed daily rest end.
- **Routes & Stops:** read-only predefined routes and stops in travel order.
- **Trips & Assignments:** read-only scheduled trips and currently applied bus assignments. Rest data for the assigned bus is also displayed.
- **Demand & Allocation:** active planning session, multi-select route and bus scope, per-trip segment demand, ADMIN-only manual `Simulate +2 Minutes`, baseline-versus-proposed comparison, assignment reasons, and separate `Approve & Apply` action.

The demo simulation changes selected segment adjustments only. It does not create passenger journey records or alter registered demand. One button click advances the demo clock by two simulated minutes. A new proposal is generated after the demand is refreshed. Approval is separate.

## Important: demo vs. real integration

All services supplied in this folder are mock/in-memory services. Data resets when the application restarts. The UI is not yet connected to the shared PostgreSQL database or the live Spring Boot backend.

The latest shared backend currently contains public route and passenger-journey endpoints, but does not yet expose the complete officer API contract required by this desktop application. See `docs/BACKEND_INTEGRATION_REQUIREMENTS.md` before replacing the mock service wiring.

Do not present mock data as live database results or the demo greedy allocation as Member 4's production algorithm.

## Code structure

```text
src/main/java/vaahansetu/desktop/
├── model/       # UI-facing data models and result DTOs
├── service/     # Interfaces consumed by Swing screens
├── demo/        # In-memory demonstration implementations
└── ui/          # Login, dashboard, buses, routes, trips, allocation screens
```

The main entry point is `vaahansetu.desktop.ui.Main`. Replace implementations behind `DesktopServices` with API-backed implementations once the team publishes and confirms the REST endpoints. Keep the Swing screens consuming the service interfaces instead of adding SQL to button listeners.

## Member 2 boundaries

Member 2 owns the officer desktop UI, its display models, validation, role-aware controls, and API client/integration. The shared database schema, passenger React UI, production demand algorithm, allocation engine, simulation persistence, and transactional approval remain shared backend/team responsibilities.
