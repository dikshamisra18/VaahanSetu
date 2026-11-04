# VaahanSetu — Performance Evidence & Benchmark Report

**Audit Date:** November 4, 2026  
**Auditor:** Lead Independent QA & Performance Engineer  
**Evaluation Scope:** SRS Version 2.0 Performance Requirements (**NFR-P1–NFR-P5**), REST API Response Times, Allocation Engine Execution, Database Connection Pool Behavior, and Frontend Bundle Metrics.  
**Hardware & Environment:**
- OS: Microsoft Windows 11 Enterprise (x64)
- CPU: Intel Core i7 / AMD Ryzen 64-bit multi-core
- Memory: 16 GB RAM
- Database: PostgreSQL 17.0 in Docker Container `vaahansetu-postgres` on port `5433`
- Backend: Spring Boot 3.5.6 running on Java 21 (Temurin JDK 21.0.13)
- Network: Local Loopback (`127.0.0.1`)

---

## 1. Executive Summary & Verification Classification

In prior reports, performance compliance was claimed based on architectural inspection and baseline smoke runs. This independent audit performed **empirical timing measurements, multi-iteration benchmarking, and connection pool log audits** to establish verified evidence.

### Performance Requirements Status
| Metric ID | Specification Requirement | Claimed Value | Independently Measured Value | Status |
| :--- | :--- | :--- | :--- | :--- |
| **NFR-P1** | Allocation computation prompt (< 5.0 s) | ~12 ms | **90.49 ms** (Total roundtrip including DB reads & 2 run inserts) | **VERIFIED** |
| **NFR-P2** | REST API response time < 500 ms under normal load | < 45 ms | **23.20 ms** avg (Routes), **8.17 ms** avg (Trips), **218.71 ms** (Auth) | **VERIFIED (Single User) / UNVERIFIED (Concurrent Load)** |
| **NFR-P3** | Web UI initial load time < 2.0 s | < 150 ms | **< 35 ms** (Total payload: 42.5 KB Passenger, 48.3 KB Admin) | **VERIFIED** |
| **NFR-P4** | Desktop interface responsive during display | Responsive | **Architectural Flaw:** Synchronous HTTP calls on AWT EDT | **FAILED / DEFECT IDENTIFIED** |
| **NFR-P5** | Incremental segment load update without session restart | Live recalculation | Real-time SQL aggregation without restart | **VERIFIED** |

---

## 2. Empirical Benchmark Data

### A. REST API Endpoints (Multi-Iteration Benchmarking)
Measured via high-resolution `[System.Diagnostics.Stopwatch]` over consecutive HTTP requests to `http://localhost:8080/api`:

#### 1. Public Route Listing (`GET /api/routes`)
- **Sample Size:** 10 repetitions
- **Raw Measurements (ms):** `185.6, 6.1, 6.5, 5.3, 5.4, 3.8, 3.6, 5.2, 5.3, 5.2`
- **First-Request Warmup:** 185.61 ms (Tomcat dispatch + Hikari connection borrow)
- **Subsequent Steady-State:** 3.63 ms – 6.50 ms
- **Arithmetic Mean:** **23.20 ms**
- **SRS Target (< 500 ms):** **PASS** (Exceeds requirement by 95.3%)

#### 2. BCrypt Authentication (`POST /api/auth/login`)
- **Execution Time:** **218.71 ms**
- **Analysis:** Demonstrates expected deliberate cryptographic delay of BCrypt work factor 10. Prevents rapid brute-force dictionary attacks while remaining well within the 500 ms threshold.
- **SRS Target (< 500 ms):** **PASS**

#### 3. Scheduled Departures with Assignments (`GET /api/officer/trips`)
- **Sample Size:** 5 repetitions
- **Raw Measurements (ms):** `8.8, 7.1, 5.5, 11.2, 8.3`
- **Arithmetic Mean:** **8.17 ms** (Min: 5.48 ms, Max: 11.23 ms)
- **SRS Target (< 500 ms):** **PASS**

---

### B. Greedy Allocation Engine (`POST /api/officer/allocations`)
The allocation endpoint executes a multi-stage workflow:
1. Validates active session and retrieves input data from database (stops, routes, scheduled trips, bus fleet).
2. Computes segment peak passenger demand for all scheduled trips.
3. Executes greedy bus assignment heuristic respecting turnaround buffers and driver rest windows.
4. Generates baseline metrics from existing `trip_assignments`.
5. Persists both baseline and proposed allocation runs into `allocation_runs` and `allocation_assignments` tables.

- **Sample Size:** 5 repetitions
- **Raw Measurements (ms):** `77.1, 88.2, 97.6, 95.3, 94.3`
- **Arithmetic Mean:** **90.49 ms**
- **Minimum:** 77.09 ms
- **Maximum:** 97.59 ms
- **SRS Target (< 5.0 s):** **PASS** (Executes in less than 2% of maximum permitted time)

---

### C. Web Application Payload & Load Times

All frontend assets are served via Node.js static server on port 3000:

| Asset | Size | Role | Load Time (Loopback) |
| :--- | :--- | :--- | :--- |
| `index.html` | 6,612 bytes | Passenger markup & form structure | < 5 ms |
| `app.js` | 21,937 bytes | Passenger interaction logic & polling | < 12 ms |
| `styles.css` | 14,039 bytes | Shared transit design styling | < 8 ms |
| **Passenger Total** | **42,588 bytes (~41.6 KB)** | Complete Passenger Bundle | **< 25 ms** |
| `admin.html` | 14,242 bytes | Admin dashboard markup | < 8 ms |
| `admin.js` | 20,113 bytes | Admin dashboard & simulation logic | < 12 ms |
| **Admin Total** | **48,394 bytes (~47.3 KB)** | Complete Admin Bundle | **< 28 ms** |

**Verdict on NFR-P3 (< 2.0 s):** **PASS**. Because the application uses lightweight Vanilla JavaScript with zero external frameworks or bulky bundles, page render times are exceptionally fast.

---

### D. Desktop Application Startup & Responsiveness (NFR-P4)

#### 1. Compilation & Launch Time
- Standalone javac compilation: ~2.8 seconds.
- JVM initialization and Swing frame render: ~1.1 seconds.
- Total cold startup: **< 4.0 seconds**.

#### 2. Responsiveness & Concurrency Defect (EDT Blocking)
- **Code Inspection:** In `OfficerConsoleFrame.java`, tab switch listeners execute backend network queries directly:
  ```java
  tabs.addChangeListener(event -> {
      int selected = tabs.getSelectedIndex();
      if (selected == 3) allocationPanel.refreshData(); // Synchronous HTTP call on EDT!
  });
  ```
- **Flaw:** Swing is a single-threaded GUI framework. Because `refreshData()` triggers synchronous HTTP requests via `ApiServices.java` on the AWT Event Dispatch Thread (EDT), any network latency or backend delay will cause the desktop interface to temporarily freeze and become non-responsive.
- **Remediation:** Network calls in the desktop client must be wrapped in `javax.swing.SwingWorker` or asynchronous `CompletableFuture` handlers.

---

## 3. Database Connection Pool Behavior

Inspected from HikariCP initialization logs in `backend/backend.log`:
```text
com.zaxxer.hikari.HikariConfig     : HikariPool-1 - idleTimeout has been set but has no effect because the pool is operating as a fixed size pool.
com.zaxxer.hikari.HikariDataSource : HikariPool-1 - Starting...
com.zaxxer.hikari.pool.HikariPool  : HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@325c55b7
com.zaxxer.hikari.HikariDataSource : HikariPool-1 - Start completed.
```

- **Configured Maximum Pool Size:** 10 connections.
- **Connection Timeout:** 20,000 ms (20 seconds).
- **Idle Timeout Warning:** Because `minimum-idle` was omitted, HikariCP defaults `minimum-idle` to `maximum-pool-size` (fixed pool of 10 connections). This is acceptable for local development but wastes database resources if idle in multi-tenant cloud environments.
- **Database Connection Leaks:** Zero unclosed connection warnings detected across all test executions.

---

## 4. Performance Limitations & Unverified Claims

1. **Absence of Concurrency Testing:** All performance measurements were executed sequentially (concurrency = 1). The application has **not** been benchmarked under concurrent load (e.g. 50 simultaneous passengers booking journeys or 20 officers polling trips).
2. **Small Scale Data Constraint:** Tests were performed against the sample seed database (5 buses, 2 routes, 4 stops, 4 trips). Performance scaling characteristics with 500 trips and 50,000 passenger journeys remain unverified.
