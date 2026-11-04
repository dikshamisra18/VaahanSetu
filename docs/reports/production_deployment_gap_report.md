# VaahanSetu — Production Deployment Gap & Readiness Audit Report

**Audit Date:** November 4, 2026  
**Auditor:** Lead Independent QA, Systems Architect & Deployment Engineer  
**Scope:** Production Readiness Evaluation, Defect Catalog, Test Coverage Gaps, Teammate Preservation Audit, and Prioritized Action Plan.  
**Target Environment:** Local Docker PostgreSQL (`localhost:5433`) vs. Cloud Production Target.

---

## 1. Executive Summary & Final Verdicts

This report provides an unvarnished, evidence-based final determination regarding the production readiness of **VaahanSetu**. While the application is a fully integrated, functional, and demonstrable system on a local development workstation (57/57 automated assertions passing), it is **NOT READY for public cloud or live production deployment**.

### Final Independent Verdicts

| Dimension | Verdict | Summary Assessment |
| :--- | :--- | :--- |
| **A. Local Functional Integration Readiness** | **READY** | All three interfaces (Passenger Web, Admin Web, Officer Desktop) integrate with the Spring Boot backend and local PostgreSQL database on port 5433. All 57 local test checks pass. |
| **B. SRS Compliance Confidence** | **HIGH (90%)** | 27/37 requirements fully satisfied; 4 incompletely tested; 6 partially implemented with minor edge-case gaps (automated recurring simulation timer and unconfirmed journey auto-expiry). |
| **C. Security Readiness** | **NOT READY** | Blocked by high-severity findings: IDOR on passenger journeys (SEC-01), infinite in-memory tokens without expiry (SEC-02), and duplicate journey race conditions (SEC-03). |
| **D. Performance Readiness** | **CONDITIONAL** | Exceptional single-user latency (< 25 ms REST, < 95 ms allocation). However, lack of concurrent load testing and Swing EDT network blocking preclude enterprise certification. |
| **E. Production Deployment Readiness** | **NOT READY / BLOCKED** | **5 Critical Blockers** must be resolved before deploying to a public cloud or production transit network. |

---

## 2. Production Deployment Blockers (Critical Path)

The following 5 blockers must be resolved prior to production deployment:

### Blocker 1: Insecure Direct Object Reference (IDOR) on Passenger Journeys
- **Risk:** High (Data tampering & unauthorized itinerary exposure).
- **Detail:** `GET /api/passenger/journeys/{id}` and `PATCH /api/passenger/journeys/{id}/status` do not authenticate or authorize access. Any person or bot guessing sequential IDs (`1, 2, 3...`) can inspect passenger travel history or cancel and tamper with active journeys.
- **Minimum Fix:** Issue a cryptographically random, unguessable UUID token (`passengerReference`) upon journey registration. Require this reference header or secret query param to view or mutate the journey.

### Blocker 2: In-Memory Token Management without Expiration
- **Risk:** High (Session hijacking, denial of service, memory leak).
- **Detail:** `AuthService.java` stores tokens in an in-memory `ConcurrentHashMap` with no expiration timestamps. Tokens never expire. If the backend restarts, all users are disconnected. In a multi-instance cloud deployment, sessions cannot be shared across nodes.
- **Minimum Fix:** Implement stateless signed JWTs with explicit 2-hour expiration claims (`exp`) and secret key signing, or persist sessions to Redis/database.

### Blocker 3: Duplicate Journey Concurrency Race Condition
- **Risk:** High (Double booking, data corruption, capacity distortion).
- **Detail:** `isDuplicateJourney` performs an application-level `SELECT COUNT(*)` followed by an `INSERT`. Concurrent requests arriving within milliseconds will both pass the check and insert duplicates.
- **Minimum Fix:** Add a database-level partial unique constraint or execute the insert within an explicit database transaction with row-level locking.

### Blocker 4: Desktop Interface Freezing via Swing EDT Network Calls
- **Risk:** Medium (Application unresponsiveness, poor operator experience).
- **Detail:** Tab switching in `OfficerConsoleFrame.java` triggers synchronous HTTP requests on the AWT Event Dispatch Thread. A 1–2 second network delay causes the desktop GUI to freeze.
- **Minimum Fix:** Wrap desktop network requests in `javax.swing.SwingWorker` with visual loading spinners.

### Blocker 5: Lack of Automated Background Expiry for Stale Journeys
- **Risk:** Medium (Capacity estimation distortion).
- **Detail:** If a passenger books a journey but never shows up, the journey remains in `REGISTERED` status indefinitely, distorting demand calculations for future trips.
- **Minimum Fix:** Implement a Spring `@Scheduled` background task (e.g. running every 5 minutes) that transitions unconfirmed journeys past departure time into `EXPIRED`.

---

## 3. Teammate Contribution Preservation Audit

An independent comparison of the repository's Git history confirms the following:

| Teammate Contribution | Original Responsibility | Verification Finding | Integrity Status |
| :--- | :--- | :--- | :--- |
| **Member 1 (Passenger Web)** | Passenger UI markup, dropdowns, passenger count, stop sequence check | All original classes, HTML elements, and UI styling in `index.html` and `app.js` are preserved. Backward-compatible dual route mapping (`/api/passenger-journeys` & `/api/passenger/journeys`) ensures no breakage. | **100% Preserved** |
| **Member 2 (Officer Desktop)** | Swing GUI panels (`BusesPanel`, `RoutesPanel`, `TripsPanel`, `AllocationPanel`), `MockServices` | All 4 panels, models, and mock fallback services remain intact. All 20 original `Member2SmokeCheck` assertions pass without failure. Added live API client (`ApiServices.java`) without modifying core teammate domain logic. | **100% Preserved** |
| **Member 3 (Allocation Heuristic)** | Greedy bus assignment heuristic with rest window and turnaround rules | Algorithms in `AllocationEngine.java` replicate teammate constraints exactly: 30-min turnaround buffer, driver rest window exclusion, peak segment load sorting. | **100% Preserved** |

---

## 4. Test Coverage Gaps & Reconciliation

### Claimed vs. Actual Assertion Reconciliation
- **Backend Unit Tests:** 4 assertions in `JourneyServiceTest.java`. (Actual: 4. Pass: 4).
- **Officer Desktop Tests:** 34 assertions (20 in `Member2SmokeCheck.java` + 14 in `ApiServicesSmokeCheck.java`). (Actual: 34. Pass: 34).
- **End-to-End Workflow Tests:** 19 assertions in `test-e2e.ps1`. (Actual: 19. Pass: 19).
- **Total Assertions:** **57 / 57 Verified Executing and Passing.**

### Identified Test Coverage Gaps
1. **Zero Concurrency Tests:** No automated tests simulate multiple concurrent HTTP requests.
2. **Zero Fleet Starvation Tests:** Existing tests only test demand $\le 10$ pax against 45-seat buses. No test verifies system behavior when total passenger demand exceeds total fleet capacity.
3. **Zero Token Invalidation Tests:** No test verifies token rejection after logout or server restart.
4. **Zero Database Disconnection Tests:** No test verifies graceful retry or connection reconnection if PostgreSQL drops.

---

## 5. Prioritized Remediation Action Plan

If approved to proceed with fixes in the next phase, the following prioritized steps will resolve all blockers and achieve complete production certification:

### Phase 1: Security & Concurrency Hardening (Priority: IMMEDIATE)
1. **Secure Passenger Journeys:** Update `saveJourney` to generate a random 16-character alphanumeric `passenger_reference`. Require this reference for `GET` and `PATCH` requests to eliminate IDOR.
2. **Implement Database Unique Constraint:** Add a partial unique index on `passenger_journeys` to eliminate the concurrent duplicate race condition.
3. **Add Token Expiry:** Update `AuthService.java` to record creation timestamps and reject tokens older than 4 hours.

### Phase 2: Operational Completeness (Priority: HIGH)
4. **Automated Unconfirmed Journey Expiry:** Add `@Scheduled(cron = "0 */5 * * * *")` sweep in `JourneyService.java` marking stale unconfirmed journeys as `EXPIRED`.
5. **Asynchronous Desktop GUI:** Refactor `OfficerConsoleFrame.java` tab updates into `SwingWorker` tasks with background thread execution.
6. **Automated Simulation Loop:** Add an optional automated timer toggle in `AdminController.java` to periodically advance the simulation clock without requiring repeated manual button clicks.

### Phase 3: Production Infrastructure & Hardening (Priority: MEDIUM)
7. **Environment-Based CORS:** Restrict CORS origins to configured domain properties rather than wildcard `*`.
8. **Sanitize Exception Responses:** Update `ApiExceptionHandler.java` to return generic error messages for unhandled internal exceptions.
9. **Configure TLS / HTTPS:** Deploy reverse proxy terminating TLS 1.3 in front of the backend.
