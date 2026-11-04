# VaahanSetu — Comprehensive Security Audit Report

**Audit Date:** November 4, 2026  
**Security Classification:** Confidential / Internal Engineering Audit  
**Target Codebase:** `C:\Users\MRUNAL\Projects\VaahanSetu-integration`  
**Scope:** Authentication, Authorization, Token Lifecycle, Password Storage, CORS, Information Disclosure, Concurrency, and Secret Management.

---

## 1. Executive Security Assessment

The VaahanSetu application demonstrates a sound foundational security architecture for a local prototype, including authentic BCrypt password hashing, role segregation between Administrative and Officer functions, and containerized database isolation. 

However, an independent security audit has identified **three high-severity architectural vulnerabilities**, **two medium-severity design gaps**, and **three low-severity hygiene issues** that prevent immediate production deployment without remediation.

---

## 2. Vulnerability & Risk Matrix

| Finding ID | Severity | Category | Title | Affected Component |
| :--- | :--- | :--- | :--- | :--- |
| **SEC-01** | **HIGH** | Authorization / IDOR | Insecure Direct Object Reference on Passenger Journeys | `PassengerJourneyController.java` (`GET /journeys/{id}`, `PATCH /journeys/{id}/status`) |
| **SEC-02** | **HIGH** | Session Management | Infinite Token Lifetime & In-Memory Token Volatility | `AuthService.java` (`activeSessions` ConcurrentHashMap) |
| **SEC-03** | **HIGH** | Concurrency / Data Integrity | Race Condition in Duplicate Journey Prevention | `PassengerJourneyController.java`, `JourneyRepository.java` |
| **SEC-04** | **MEDIUM** | Network Security | Permissive Wildcard Cross-Origin Resource Sharing (`CORS *`) | All `@CrossOrigin(origins = "*")` Controllers |
| **SEC-05** | **MEDIUM** | Architecture | Lack of Centralized Spring Security Filter Chain | Ad-hoc manual controller authentication checks |
| **SEC-06** | **LOW** | Information Disclosure | Exception Message Leakage in Global Error Handler | `ApiExceptionHandler.java` (`handleGeneral`) |
| **SEC-07** | **LOW** | Configuration | Hardcoded Default Credentials in Seed Scripts | `database/setup/local_seed.sql`, `application.properties` |
| **SEC-08** | **LOW** | Protocol Security | Plaintext HTTP Protocol without TLS/HTTPS Enforcement | Spring Boot embedded Tomcat (`server.port=8080`) |

---

## 3. Detailed Security Findings

### SEC-01: Insecure Direct Object Reference (IDOR) on Passenger Journeys (HIGH)
- **Description:** Passenger endpoints (`GET /api/passenger/journeys/{id}` and `PATCH /api/passenger/journeys/{id}/status`) are completely unauthenticated. Journey IDs are generated sequentially (`1, 2, 3...`).
- **Attack Vector:** Any external actor can iterate through integer IDs to retrieve travel itineraries, boarding stops, and departure times, or arbitrarily cancel or alter the status of legitimate passengers' trips.
- **Requirement Violation:** SRS NFR-S2 ("passengers may submit and manage only their own journey records"), SRS NFR-S6 ("protected from unauthorized access").
- **Remediation:** Issue a cryptographically random, unguessable passenger access secret (or UUID `passenger_reference`) upon journey registration. Require this token in request headers or path parameters to view or update journey status.

---

### SEC-02: Infinite Token Lifetime & In-Memory Token Volatility (HIGH)
- **Description:** Tokens are generated as `UUID.randomUUID().toString()` and stored in an in-memory `ConcurrentHashMap<String, Map<String, Object>> activeSessions`.
- **Flaw:**
  1. **No Expiry Mechanism:** The previous integration summary claimed an "8-hour token validity window". Code inspection of `AuthService.java` confirms there is **zero timestamp recording, zero eviction logic, and zero timeout enforcement**. Tokens remain valid indefinitely until the server is restarted.
  2. **Non-Persistent / Single-Node Bound:** If the backend process restarts or crashes, all active sessions are immediately lost, requiring all officers and administrators to re-authenticate. In a multi-instance production deployment, sessions would not be shared across instances.
- **Requirement Violation:** SRS NFR-S3 ("Session token timeout & expiration").
- **Remediation:** Transition to signed HMAC-SHA256 JWTs with an embedded `exp` (expiration claim) or store session metadata in a Redis cache with explicit TTL (Time-To-Live).

---

### SEC-03: Race Condition in Duplicate Journey Prevention (HIGH)
- **Description:** `isDuplicateJourney` performs a `SELECT COUNT(*)` for matching journeys within a 2-minute window. If count is 0, `saveJourney` executes an `INSERT`.
- **Flaw:** Under concurrent traffic (e.g. rapid double-clicking or scripted automated submissions), two threads can simultaneously execute the `SELECT COUNT(*)` check before either has committed an `INSERT`. Both checks return 0, and both threads proceed to insert duplicate journeys.
- **Root Cause:** Lack of a unique database constraint or transactional row-level lock.
- **Requirement Violation:** SRS F9, SRS NFR-S7.
- **Remediation:** Enforce a database-level partial unique index or advisory lock:
  ```sql
  CREATE UNIQUE INDEX idx_pj_prevent_duplicates 
  ON passenger_journeys (session_id, trip_id, boarding_stop_id, destination_stop_id, passenger_count)
  WHERE journey_status IN ('REGISTERED', 'BOARDED');
  ```

---

### SEC-04: Permissive Wildcard Cross-Origin Resource Sharing (MEDIUM)
- **Description:** All Spring REST controllers specify `@CrossOrigin(origins = "*")`.
- **Risk:** In an enterprise or cloud environment, wildcard CORS allows hostile websites visited by an authenticated officer or administrator to execute cross-site requests to internal endpoints (`/api/officer/allocations/approve`) using ambient browser permissions.
- **Remediation:** Replace wildcard annotations with a centralized CORS configuration restricting origins strictly to designated transit authority domains (e.g. `https://transit.example.gov`).

---

### SEC-05: Lack of Centralized Spring Security Filter Chain (MEDIUM)
- **Description:** Authentication is enforced via ad-hoc procedural method calls (`if (!isAuthenticated(auth)) return unauthorized();`) inside each controller method.
- **Risk:** There is no declarative security configuration. If a developer creates a new endpoint and omits the manual check, that endpoint defaults to unauthenticated public access.
- **Remediation:** Configure a standard Spring Security `SecurityFilterChain` bean to enforce URL-pattern-based authorization rules (`/api/admin/** -> hasRole('ADMIN')`, `/api/officer/** -> hasAnyRole('OFFICER', 'ADMIN')`).

---

### SEC-06: Sensitive Information Exposure in Error Responses (LOW)
- **Description:** In `ApiExceptionHandler.java`, general exceptions are returned via:
  ```java
  return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("message", "An unexpected error occurred: " + ex.getMessage()));
  ```
- **Risk:** If a database error occurs (e.g. `PSQLException`), internal table names, column names, or SQL query fragments could be exposed in the HTTP response.
- **Remediation:** Sanitize general error messages to a generic string (e.g. `"An unexpected internal error occurred. Please contact system support."`) while logging detailed stack traces internally to backend logs.

---

### SEC-07: Hardcoded Default Credentials in Seed Data (LOW)
- **Description:** Default development credentials are provided in seed scripts and properties files for evaluation convenience.
- **Risk:** If seed scripts are accidentally run in a staging or cloud environment, well-known credentials would provide immediate administrative access.
- **Remediation:** Ensure seed scripts are strictly segregated to local Docker profiles and never bundled or executed in production deployment pipelines.

---

### SEC-08: Plaintext HTTP Communication (LOW)
- **Description:** Backend Tomcat listens over plaintext HTTP on port 8080.
- **Risk:** Bearer tokens and login credentials transmitted across an unencrypted network are susceptible to packet sniffing and man-in-the-middle (MitM) inspection.
- **Remediation:** Enforce TLS 1.3 via reverse proxy (e.g. NGINX, Cloudflare, or AWS ALB) terminating HTTPS in front of the application.

---

## 4. Verification of Password Hashing Implementation

- **BCrypt Implementation:** Verified in `AuthService.java` using `org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder`.
- **Work Factor:** Standard default cost factor (strength 10).
- **Password Storage:** Verified in `users` table:
  - Column: `password_hash VARCHAR(255) NOT NULL`.
  - Passwords are never stored in plaintext.
  - Authentic BCrypt hashes verified for local development accounts (`admin` and `officer1`).
- **No Password Exposure:** Automated tests and startup scripts verify password matching without printing passwords or hash strings to console logs.

---

## 5. Security Verdict & Deployment Gate

**Security Verdict:** **CONDITIONAL PASS FOR LOCAL PROTOTYPE / FAIL FOR PRODUCTION**

The application is safe for local evaluation, teammate demonstrations, and developer workstation execution. It is **NOT** ready for public cloud deployment until SEC-01 (IDOR on journeys), SEC-02 (Token lifecycle), and SEC-03 (Duplicate journey concurrency) are remediated.
