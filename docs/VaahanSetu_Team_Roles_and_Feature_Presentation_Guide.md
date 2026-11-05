# VaahanSetu: Team Roles, Feature Ownership & Presentation Guide
### Group No. 6 — CS1207 Object Oriented Programming Lab
**Academic Evaluation, Demonstration & Viva Defense Guide**

---

## 1. Overview & Presentation Strategy

When presenting **VaahanSetu**, the team should position the project as an **enterprise-grade, production-deployed transit optimization platform** addressing two major real-world public transportation challenges:
1. **Severe Passenger Overcrowding** during peak hours, surges, and special events.
2. **Bus Fleet Underutilization & Inefficiency** caused by static scheduling, rigid capacity allocation, and neglected driver rest requirements.

### Presentation Format & Flow (10 – 12 Minutes Total)
1. **Introduction & Motivation (1 min)**: State the problem and explain the architectural transition from legacy desktop mockups to a reactive, cloud-native full-stack system.
2. **Member 1 (Diksha Misra) — Security & System Architecture (2 mins)**
3. **Member 2 (Pasupuleti Varshitha) — Fleet Management & Dynamic Session Planning (2 mins)**
4. **Member 3 (Gurram Amrutha Sri) — Passenger Portal & Search Intent Logging (2 mins)**
5. **Member 4 (Krishna Patidar) — Allocation Engine & Constraint Solver (2.5 mins)**
6. **Member 5 (Mrunal Sachin Patil) — Demand Management, Explainability & Live Approval (2.5 mins)**
7. **Conclusion & Q&A Defense (1 min)**

---

## 2. Team Member Role Distribution & Scripted Talking Points

---

### Member 1: Diksha Misra (25CSB0A17)
**Primary Subsystem:** Authentication, Role-Based Access Control (RBAC), Security & System Architecture  
**Use Case Owned:** Use Case U1 (*Authenticate & Enforce Role-Based Access Control*)  
**Source Code Owned:**
- [`AuthController.java`](file:///C:/Users/MRUNAL/Projects/VaahanSetu-integration/backend/src/main/java/com/vaahansetu/controller/AuthController.java)
- [`AuthService.java`](file:///C:/Users/MRUNAL/Projects/VaahanSetu-integration/backend/src/main/java/com/vaahansetu/service/AuthService.java)
- JWT Token Validation Filter & BCrypt Password Encryption
- Security Handshake in [`admin.js`](file:///C:/Users/MRUNAL/Projects/VaahanSetu-integration/frontend/admin.js#L26-L132)

#### What to Say during the Presentation:
> *"Good morning/afternoon, professors. I am Diksha Misra. My primary responsibility in VaahanSetu was architecting the **System Security, Authentication, and Role-Based Access Control (RBAC)** infrastructure.*
>
> *In a public transit management system, separating administrative controls from public passenger access is critical. I implemented a stateless, token-based authentication mechanism using cryptographically signed **JSON Web Tokens (JWT)** with HMAC-SHA256.*
>
> *When an administrator signs into the Admin Portal (`admin.html`), our backend validates credentials against salted BCrypt hashes stored in PostgreSQL. Upon successful authentication, the server generates a JWT containing claims for `ADMIN` or `OFFICER`. This token is sent in the `Authorization: Bearer <token>` header for all operational actions—such as modifying fleet configurations or approving new schedules—while keeping commuter inquiry endpoints completely accessible to the public without unnecessary authentication friction.*
>
> *I also ensured clean cross-origin resource sharing (CORS) across our distributed Render deployment, preventing unauthorized cross-site scripting while allowing seamless communication between our frontend portal and backend REST services."*

---

### Member 2: Pasupuleti Varshitha (25CSB0A33)
**Primary Subsystem:** Fleet Inventory, Bus Capacity, Dynamic Driver Rest Windows & Active Router  
**Use Case Owned:** Use Case U2 (*Manage Fleet Inventory & Rest Windows*) & Use Case U3 (*Active Router Session Planning*)  
**Source Code Owned:**
- [`OfficerService.java` (getAllBuses)](file:///C:/Users/MRUNAL/Projects/VaahanSetu-integration/backend/src/main/java/com/vaahansetu/service/OfficerService.java#L80-L135)
- [`OfficerRepository.java` (findEngineBuses)](file:///C:/Users/MRUNAL/Projects/VaahanSetu-integration/backend/src/main/java/com/vaahansetu/repository/OfficerRepository.java#L58-L115)
- Fleet table & Session selection in [`admin.js`](file:///C:/Users/MRUNAL/Projects/VaahanSetu-integration/frontend/admin.js#L223-L263) & [`admin.html`](file:///C:/Users/MRUNAL/Projects/VaahanSetu-integration/frontend/admin.html#L75-L130)

#### What to Say during the Presentation:
> *"Hello everyone, I am Pasupuleti Varshitha. I took ownership of the **Fleet & Bus Management** subsystem, dynamic **Driver Rest Window enforcement**, and the **Active Router Session Planning** module.*
>
> *Earlier prototypes had hardcoded bus capacities and fixed strings for rest times, causing data mismatches between frontend and backend. I connected our fleet management directly to our PostgreSQL database, which models 20 real buses with registered seating capacities ranging from 30 to 60 seats.*
>
> *More importantly, human drivers require mandatory rest to prevent fatigue and accidents. Each bus record in our database includes `rest_start`, `rest_end`, and `rest_duration_minutes`. On the Admin Dashboard under **Fleet & Buses**, you can see that our system dynamically calculates availability in real-time. If a bus is currently within its rest interval (e.g., Bus #101 resting between 12:00 and 12:30), the system badges it as `Unavailable (Resting)` and strictly forbids scheduling it on any trip during that window.*
>
> *Additionally, in **Session Planning**, transit officers configure the active planning horizon. We previously had an issue where deselecting Route 2 did not remove it from scheduled departures. I fixed the query logic by joining `session_routes` so that as soon as an officer unchecks Route 2 and saves, Route 2 immediately vanishes from all operational departures and allocation inputs."*

---

### Member 3: Gurram Amrutha Sri (25CSB0A44)
**Primary Subsystem:** Passenger Portal, Date-Aware Inquiries, Search Intent Tracking & Common Demand Sync  
**Use Case Owned:** Use Case U4 (*Passenger Portal & Inquiries*)  
**Source Code Owned:**
- [`PassengerJourneyController.java`](file:///C:/Users/MRUNAL/Projects/VaahanSetu-integration/backend/src/main/java/com/vaahansetu/controller/PassengerJourneyController.java)
- [`index.html`](file:///C:/Users/MRUNAL/Projects/VaahanSetu-integration/frontend/index.html) & [`app.js`](file:///C:/Users/MRUNAL/Projects/VaahanSetu-integration/frontend/app.js)
- Search intent tracking (`POST /api/passenger-journeys/intent`)
- Travel date selector and date-aware demand queries

#### What to Say during the Presentation:
> *"Greetings, I am Gurram Amrutha Sri. My focus was developing the **Passenger Portal** and integrating commuter actions into our **Common Passenger Demand System**.*
>
> *Public transit demand begins well before a passenger boards a bus—it begins with **passenger travel intent**. We introduced an intelligent travel-date selector and real origin-destination stop dropdowns covering 365 real TSRTC stops across 24 Hyderabad corridors.*
>
> *When a commuter selects their route, boarding stop, and destination stop, the frontend automatically dispatches a search intent signal to `/api/passenger-journeys/intent`. This records an `UNCONFIRMED` journey in the database without artificially creating false confirmed bookings. When the commuter books their ticket, the status updates to `REGISTERED`.*
>
> *Crucially, I resolved the date-filtering issue: previously, selecting different dates did not fetch date-specific demand. Now, our `GET /demand?date=YYYY-MM-DD` endpoint dynamically filters journeys and adjustments for the exact service date selected. The passenger can also inspect the demand breakdown card to see real booking inquiries and authorized operational offsets in real time."*

---

### Member 4: Krishna Patidar (25CSB0A64)
**Primary Subsystem:** Constraint-Aware Greedy Allocation Engine, Heuristic Optimization & Cartesian Join Fix  
**Use Case Owned:** Use Case U5 (*Calculate and Compare Bus Allocation*)  
**Source Code Owned:**
- [`AllocationEngine.java`](file:///C:/Users/MRUNAL/Projects/VaahanSetu-integration/backend/src/main/java/com/vaahansetu/allocation/AllocationEngine.java)
- [`OfficerRepository.java` (LATERAL join query)](file:///C:/Users/MRUNAL/Projects/VaahanSetu-integration/backend/src/main/java/com/vaahansetu/repository/OfficerRepository.java#L160-L240)
- Automated Unit Tests: [`AllocationEngineTest.java`](file:///C:/Users/MRUNAL/Projects/VaahanSetu-integration/backend/src/test/java/com/vaahansetu/AllocationEngineTest.java) & [`JourneyServiceTest.java`](file:///C:/Users/MRUNAL/Projects/VaahanSetu-integration/backend/src/test/java/com/vaahansetu/JourneyServiceTest.java)

#### What to Say during the Presentation:
> *"Hello everyone, I am Krishna Patidar. I engineered the core **Allocation Engine** optimization solver and resolved the critical database SQL join anomaly.*
>
> *First, during testing, we noticed that passenger demand was displaying in the hundreds on the Admin Dashboard but in the tens on the Passenger Portal. I audited `OfficerRepository.java` and discovered a severe **SQL Cartesian Product bug**: `passenger_journeys` and `segment_demand_adjustments` were joined in the same `FROM` clause without isolation, which multiplied rows against each other. I refactored the query to use **independent LATERAL subqueries**. This completely eliminated the multiplication bug, ensuring demand displays accurately in the tens across all interfaces.*
>
> *Second, for the Allocation Engine itself, we implemented a pure, constraint-aware **Greedy Heuristic Algorithm**. Operating purely in domain memory without database I/O, the engine evaluates peak passenger loads across all trip segments. It sorts trips from hardest to easiest (highest peak load first) and assigns the conflict-free bus that minimizes unmet demand.*
>
> *The engine strictly enforces two hard real-world operational constraints:
> 1. **Fixed Driver Rest Windows**: A bus cannot be scheduled on any trip that intersects its mandatory rest period.
> 2. **30-Minute Turnaround Buffer**: A bus completing Trip A cannot be scheduled on Trip B unless there is at least a 30-minute window between Trip A's arrival and Trip B's departure for cleaning, depot transit, and boarding.*
>
> *I verified this with automated unit test suites in `AllocationEngineTest.java`, confirming that surges dynamically upgrade buses from 30-seaters to 60-seaters while respecting all rest constraints."*

---

### Member 5: Mrunal Sachin Patil (25CSB0A67)
**Primary Subsystem:** Demand Management, Explainability, Before-vs-After Operational Impact & Live Persistence  
**Use Case Owned:** Use Case U6 (*Administrative Demand Adjustments*) & Use Case U9 (*Approve & Persist Schedule Allocation*)  
**Source Code Owned:**
- [`admin.html` (Allocation 5-Stage Dashboard)](file:///C:/Users/MRUNAL/Projects/VaahanSetu-integration/frontend/admin.html#L205-L270)
- Demand Adjustment API in [`AdminController.java`](file:///C:/Users/MRUNAL/Projects/VaahanSetu-integration/backend/src/main/java/com/vaahansetu/controller/AdminController.java#L68-L125)
- Allocation presentation rendering & live persistence in [`admin.js`](file:///C:/Users/MRUNAL/Projects/VaahanSetu-integration/frontend/admin.js#L550-L680)

#### What to Say during the Presentation:
> *"Good day, I am Mrunal Sachin Patil. I took responsibility for **eliminating the artificial 2-minute simulation clock**, introducing **Authorized Demand Management**, establishing **Plain-English Decision Explainability**, and implementing **Schedule Persistence**.*
>
> *In previous iterations, there was a '+2 Minutes Step' simulation button that generated random artificial numbers. In real transit management, transit authorities do not want random numbers—they need real operational control. We removed the simulation clock entirely and replaced it with **Administrative Demand Management**.*
>
> *If a concert, cricket match, or weather disruption causes sudden crowd surges, an authorized officer selects the trip and applies a signed passenger adjustment (e.g., `+10` or `+20`). This writes directly to the single source of truth in PostgreSQL and immediately synchronizes across Scheduled Departures, the Passenger Portal, and the Allocation Engine.*
>
> *Furthermore, algorithms should never be black boxes. When an officer clicks **Run Allocation Engine**, our UI presents a comprehensive **5-Section Presentation Flow**:
> 1. **Demand Analysis**: Showing total demand evaluated and unmet demand.
> 2. **Fleet Availability**: Showing eligible versus resting buses.
> 3. **Proposed Bus Assignments**: Each row features a **Decision Explanation** in clear, natural language—explaining why a bus was retained, upgraded, or reassigned based on load and occupancy.
> 4. **Operational Comparison Strip**: Demonstrating the before-and-after impact (e.g., reducing unmet demand to zero).
> 5. **Approval & Persistence**: When the officer clicks **Approve & Persist Allocation**, the new assignments are written directly to `scheduled_trips` in PostgreSQL, instantly updating live departures."*

---

## 3. Step-by-Step Live Demonstration Walkthrough

When you share your screen for the evaluation, follow this exact sequence:

```
+----------------------------------------------------------------------------------------------------+
|                                    DEMONSTRATION RUN-SHEET                                         |
+----------------------------------------------------------------------------------------------------+
| 1. Show Production URLs & Architecture                                                             |
|    - Open: https://vaahansetu-portal.onrender.com/admin.html                                        |
|    - Highlight: Live on Render cloud, connected to Neon Serverless PostgreSQL with SSL             |
|                                                                                                    |
| 2. Admin Sign-In & Fleet / Rest Windows (Diksha & Varshitha)                                       |
|    - Login: admin / admin123                                                                       |
|    - Open "Fleet & Buses": Show 20 buses, capacities (60 seats), rest windows (12:00-12:30),       |
|      and dynamic availability badges ("Available" vs "Unavailable: Resting")                       |
|                                                                                                    |
| 3. Dynamic Route Filtering in Session Planning (Varshitha & Amrutha)                               |
|    - Open "Session Planning": Point out real origin & destination stop names                       |
|    - Uncheck Route 2 -> Click "Save Session Configuration"                                         |
|    - Open "Scheduled Trips": Show that Route 2 departures immediately vanish from the schedule     |
|                                                                                                    |
| 4. Passenger Portal & Common Demand (Amrutha & Mrunal)                                             |
|    - Open: https://vaahansetu-portal.onrender.com/index.html in a side window                      |
|    - Select Travel Date and Route 10 -> Show demand count (e.g., 20 pax in tens, not hundreds!)    |
|    - Pick origin and destination stops to trigger search intent logging                            |
|                                                                                                    |
| 5. Administrative Demand Surge (Mrunal & Krishna)                                                  |
|    - Go to Admin Portal -> "Demand Management" tab                                                 |
|    - Select Trip #1001 -> Click "+10 Rush" -> Click "Save Demand Adjustment"                       |
|    - Go to "Scheduled Trips": Trip #1001 peak demand immediately jumps by +10                      |
|                                                                                                    |
| 6. Allocation Engine Execution & Decision Explainability (Krishna & Mrunal)                        |
|    - Go to "Allocation Engine" tab -> Click "Run Allocation Engine"                                |
|    - Walk through the 5 sections:                                                                  |
|      * Section 1: Demand Analysis cards (Total demand, unmet demand eliminated)                    |
|      * Section 2: Fleet Availability (Buses eligible vs resting)                                   |
|      * Section 3: Proposed Table -> Highlight the plain-English Decision Explanation column       |
|      * Section 4: Operational State Comparison strip                                               |
|      * Section 5: Click "Approve & Persist Allocation" -> Show success notification                |
|    - Switch to "Scheduled Trips": Verify approved bus registrations now appear on live trips!      |
+----------------------------------------------------------------------------------------------------+
```

---

## 4. Top Technical Questions & Answers for Viva / Examiners

### Q1: Why did you choose a Greedy Heuristic instead of Integer Linear Programming (ILP) or Brute Force?
**Answer (Krishna):**
> *"In metropolitan transit operations, scheduling decisions must be made in sub-second time. While ILP provides theoretical global optimality, its worst-case computational complexity is NP-hard ($O(2^n)$), which leads to solver timeouts or unacceptable latency as routes and trips scale. Brute force would require evaluating $m! \times n$ permutations. 
> 
> Our constraint-aware Greedy heuristic sorts trips by difficulty (highest peak demand first) and assigns the conflict-free bus that minimizes unmet demand while right-sizing capacity. It operates in $O(T \log T + T \cdot B)$ time, executing in under 20 milliseconds for 90 trips and 20 buses, making it ideal for real-time operational replanning."*

---

### Q2: How did you fix the mismatch where Admin showed demand in the hundreds while Passenger Portal showed tens?
**Answer (Krishna & Amrutha):**
> *"The root cause was a **SQL Cartesian Product join bug** in `OfficerRepository.findSegmentDemandForTrip`. The original SQL joined `passenger_journeys` and `segment_demand_adjustments` in the same `FROM` clause without independent grouping. When a trip had 5 journeys and 4 adjustments, the database produced $5 \times 4 = 20$ cross-product rows per segment, multiplying counts artificially into the hundreds.
> 
> We resolved this by refactoring the query to use **independent LATERAL subqueries**. One subquery aggregates bookings and inquiries; the second subquery aggregates adjustments. They are summed additively: $\text{Effective Demand} = \text{Base} + \text{Adjustment}$. This completely eliminated the multiplication bug, ensuring accurate demand in the tens across both portals."*

---

### Q3: Why did you remove the 2-minute simulation clock?
**Answer (Mrunal):**
> *"The legacy 2-minute simulation clock was an artificial desktop artifact that used random number generators to increment/decrement passenger counts. In a real-world transit application, operators do not work with artificial virtual time or random fake data.
> 
> We replaced it with an **Authorized Demand Management System** linked to our Single Source of Truth. Real passenger demand now originates from two authentic sources: commuter inquiries/bookings in the Passenger Portal, and signed operational offsets ($\pm \Delta$) authorized by transit officers (e.g., event surges or weather offsets). This ensures the application operates with meaningful, audit-ready operational data."*

---

### Q4: How does the system enforce driver rest periods and turnaround times?
**Answer (Varshitha & Krishna):**
> *"Each bus in our PostgreSQL database has `rest_start` and `rest_end` columns. When the Allocation Engine evaluates whether a bus $B$ can serve Trip $T$, it checks:
> 1. **Fixed Rest Window Check**: Whether Trip $T$'s interval $[t_{\text{dep}}, t_{\text{arr}}]$ overlaps with $[R_{\text{start}}(B), R_{\text{end}}(B)]$. If there is any intersection, the bus is rejected as ineligible.
> 2. **30-Minute Turnaround Check**: If bus $B$ is already assigned to another trip ending at $t_1$, the next trip cannot depart before $t_1 + 30\text{ minutes}$. This prevents scheduling overlaps and allows sufficient buffer for passenger disembarkation, vehicle cleaning, and depot staging."*

---

### Q5: How do you prevent duplicate counting between searches and bookings?
**Answer (Amrutha & Diksha):**
> *"We utilize explicit lifecycle statuses within the `passenger_journeys` table:
> - When a passenger searches for a trip, it is logged with `journey_status = 'UNCONFIRMED'` (search intent).
> - When a booking is submitted, it is recorded with `journey_status = 'REGISTERED'`.
> - Our common demand aggregation query groups by journey ID and status, distinguishing inquiries from confirmed bookings and preventing search intent from being counted as a double booking."*

---

### Q6: How does the system handle concurrent allocation approval without corrupting the database?
**Answer (Diksha & Mrunal):**
> *"The approval endpoint `/api/officer/allocations/approve` is wrapped in Spring's `@Transactional` annotation. When an officer approves Run #$N$, the database transaction atomically sets previous active assignments to `SUPERSEDED`, inserts the new assignments into `trip_assignments`, updates `scheduled_trips.assigned_bus_id`, and marks the run status as `APPROVED`. If any failure occurs during the batch write, the entire transaction rolls back, leaving the existing operational schedule intact."*
