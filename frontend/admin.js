// VaahanSetu Administrator Portal Logic
const API_BASE = window.VAAHAN_API_URL || (
    (window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1")
        ? "http://localhost:8080/api"
        : `${window.location.origin}/api`
);

const state = {
    token: sessionStorage.getItem('vaahan_auth_token') || '',
    user: JSON.parse(sessionStorage.getItem('vaahan_user') || 'null'),
    simClockMinutes: 600, // 10:00 AM in minutes
    lastAllocationProposal: null,
    trips: [],
    buses: [],
    routes: []
};

function formatMinutesToTime(mins) {
    const h = Math.floor(mins / 60) % 24;
    const m = mins % 60;
    const pad = (n) => String(n).padStart(2, '0');
    return `${pad(h)}:${pad(m)}:00`;
}

// Authentication Helpers
function getAuthHeaders() {
    const headers = { 'Content-Type': 'application/json' };
    if (state.token) {
        headers['Authorization'] = `Bearer ${state.token}`;
    }
    return headers;
}

function checkAuth() {
    if (state.token && state.user) {
        document.getElementById('loginSection').hidden = true;
        document.getElementById('adminDashboard').hidden = false;
        document.getElementById('logoutBtn').hidden = false;
        loadDashboardData();
    } else {
        document.getElementById('loginSection').hidden = false;
        document.getElementById('adminDashboard').hidden = true;
        document.getElementById('logoutBtn').hidden = true;
    }
}

// Tab Switching
document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));

        btn.classList.add('active');
        const targetId = btn.getAttribute('data-tab');
        const targetEl = document.getElementById(targetId);
        if (targetEl) targetEl.classList.add('active');
    });
});

// Sign In Form Handler
document.getElementById('adminLoginForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const username = document.getElementById('adminUsername').value.trim();
    const password = document.getElementById('adminPassword').value.trim();
    const feedback = document.getElementById('loginFeedback');
    feedback.textContent = 'Authenticating...';
    feedback.className = 'feedback-msg text-info';

    try {
        const response = await fetch(`${API_BASE}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });

        if (!response.ok) {
            const err = await response.json().catch(() => ({}));
            throw new Error(err.message || 'Invalid username or password');
        }

        const data = await response.json();
        state.token = data.token;
        state.user = data;
        sessionStorage.setItem('vaahan_auth_token', data.token);
        sessionStorage.setItem('vaahan_user', JSON.stringify(data));

        feedback.textContent = 'Login successful!';
        feedback.className = 'feedback-msg text-success';
        setTimeout(checkAuth, 400);
    } catch (err) {
        feedback.textContent = `Login failed: ${err.message}`;
        feedback.className = 'feedback-msg text-danger';
    }
});

// Logout Handler
document.getElementById('logoutBtn').addEventListener('click', () => {
    state.token = '';
    state.user = null;
    sessionStorage.removeItem('vaahan_auth_token');
    sessionStorage.removeItem('vaahan_user');
    checkAuth();
});

// Load Dashboard Data
async function loadDashboardData() {
    await Promise.all([
        fetchActiveSession(),
        fetchBuses(),
        fetchRoutes(),
        fetchTrips()
    ]);
    renderSessionConfig();
}

// Fetch Active Session Clock
async function fetchActiveSession() {
    try {
        const res = await fetch(`${API_BASE}/officer/sessions/active`, {
            headers: getAuthHeaders()
        });
        if (res.ok) {
            const session = await res.json();
            if (session && typeof session.simulationElapsedMinutes === 'number') {
                state.simClockMinutes = 600 + session.simulationElapsedMinutes;
                const newTime = formatMinutesToTime(state.simClockMinutes);
                document.getElementById('statSimTime').textContent = newTime;
                document.getElementById('simClockDisplay').textContent = newTime;
            }
        }
    } catch (err) {
        console.warn('Could not sync active session clock:', err);
    }
}

// Fetch Buses
async function fetchBuses() {
    const tbody = document.querySelector('#busesTable tbody');
    tbody.innerHTML = '<tr><td colspan="5" class="text-center">Loading fleet data...</td></tr>';
    try {
        const res = await fetch(`${API_BASE}/officer/buses?time=${formatMinutesToTime(state.simClockMinutes)}`, {
            headers: getAuthHeaders()
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const buses = await res.json();
        state.buses = buses;
        document.getElementById('statBusCount').textContent = buses.length;

        if (buses.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-center">No buses registered.</td></tr>';
            return;
        }

        tbody.innerHTML = buses.map(bus => {
            const restWindow = (bus.restWindowStart && bus.restWindowEnd)
                ? `${bus.restWindowStart} – ${bus.restWindowEnd}`
                : 'None assigned';
            const statusBadge = bus.available !== false
                ? '<span class="status-badge badge-active">Available</span>'
                : '<span class="status-badge badge-resting">Resting / In Use</span>';

            return `
                <tr>
                    <td><strong>#${bus.busId}</strong></td>
                    <td>${bus.registrationNumber || '-'}</td>
                    <td>${bus.capacity} seats</td>
                    <td>${restWindow}</td>
                    <td>${statusBadge}</td>
                </tr>
            `;
        }).join('');
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center text-danger">Error loading buses: ${err.message}</td></tr>`;
    }
}

// Fetch Routes
async function fetchRoutes() {
    try {
        const res = await fetch(`${API_BASE}/officer/routes`, {
            headers: getAuthHeaders()
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const routes = await res.json();
        state.routes = routes;
        document.getElementById('statRouteCount').textContent = routes.length;
    } catch (err) {
        console.error('Failed to fetch routes:', err);
    }
}

// Fetch Trips
async function fetchTrips() {
    const dateInput = document.getElementById('tripDateFilter').value || '2026-11-04';
    const tbody = document.querySelector('#tripsTable tbody');
    const simTripSelect = document.getElementById('simTripSelect');

    tbody.innerHTML = '<tr><td colspan="7" class="text-center">Loading scheduled departures...</td></tr>';

    try {
        const res = await fetch(`${API_BASE}/officer/trips?date=${dateInput}`, {
            headers: getAuthHeaders()
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const trips = await res.json();
        state.trips = trips;
        document.getElementById('statTripCount').textContent = trips.length;

        // Populate Sim Trip select
        simTripSelect.innerHTML = '<option value="">Select trip</option>' +
            trips.map(t => {
                const timeLabel = t.departureTime ? (t.departureTime.length > 16 ? t.departureTime.substring(11, 16) : t.departureTime) : 'Dep';
                return `<option value="${t.tripId}">Trip #${t.tripId} (${t.routeCode || 'Route'} @ ${timeLabel})</option>`;
            }).join('');
        if (trips.length > 0 && !simTripSelect.value) {
            simTripSelect.selectedIndex = 1;
        }

        if (trips.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center">No trips scheduled for this date.</td></tr>';
            return;
        }

        tbody.innerHTML = trips.map(t => {
            const busReg = t.currentBusRegistration || t.assignedBusNumber;
            const busCap = t.currentBusCapacity || t.busCapacity;
            const assignedBus = busReg ? `Bus #${busReg}` : '<span class="text-muted">Unassigned</span>';
            const cap = busCap ? `${busCap} seats` : '-';
            const depLabel = t.departureTime ? (t.departureTime.length > 16 ? t.departureTime.substring(11, 16) : t.departureTime) : '-';
            const arrLabel = t.expectedArrivalTime ? (t.expectedArrivalTime.length > 16 ? t.expectedArrivalTime.substring(11, 16) : t.expectedArrivalTime) : (t.arrivalTime || '-');
            return `
                <tr>
                    <td><strong>#${t.tripId}</strong></td>
                    <td><span class="route-tag">${t.routeCode || t.routeId}</span></td>
                    <td>${depLabel}</td>
                    <td>${arrLabel}</td>
                    <td>${assignedBus}</td>
                    <td>${cap}</td>
                    <td>
                        <button class="action-btn-small" onclick="loadTripDemand(${t.tripId})">View Demand</button>
                    </td>
                </tr>
            `;
        }).join('');
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="7" class="text-center text-danger">Error loading trips: ${err.message}</td></tr>`;
    }
}

// Load Trip Demand Drilldown
window.loadTripDemand = async function(tripId) {
    const panel = document.getElementById('tripDemandPanel');
    const details = document.getElementById('tripDemandDetails');
    panel.hidden = false;
    details.innerHTML = 'Loading demand data...';

    try {
        const res = await fetch(`${API_BASE}/officer/demand?tripId=${tripId}`, {
            headers: getAuthHeaders()
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const demand = await res.json();

        if (demand.length === 0) {
            details.innerHTML = `<em>No segment demand recorded yet for Trip #${tripId}.</em>`;
            return;
        }

        details.innerHTML = demand.map(d => {
            const baseD = d.registeredDemand ?? d.baseDemand ?? 0;
            const simAdj = d.simulatedAdjustment ?? 0;
            const effD = d.effectiveDemand ?? d.adjustedDemand ?? baseD;
            const adjLabel = simAdj !== 0 ? (simAdj > 0 ? `+${simAdj}` : `${simAdj}`) : '0';
            return `
                <div class="segment-card">
                    <div class="segment-title">${d.startStopName} &rarr; ${d.endStopName}</div>
                    <div class="segment-stats">
                        <span>Base Demand: <strong>${baseD}</strong></span>
                        <span>Adjustment: <strong>${adjLabel}</strong></span>
                        <span>Effective Demand: <strong>${effD}</strong></span>
                        <span>Current Bus Cap: <strong>${d.currentCapacity || 'None'}</strong></span>
                    </div>
                </div>
            `;
        }).join('');
    } catch (err) {
        details.innerHTML = `<span class="text-danger">Failed to load demand: ${err.message}</span>`;
    }
};

// Simulation Controls
document.getElementById('stepClockBtn').addEventListener('click', async () => {
    state.simClockMinutes += 2;
    const newTime = formatMinutesToTime(state.simClockMinutes);
    document.getElementById('statSimTime').textContent = newTime;
    document.getElementById('simClockDisplay').textContent = newTime;

    const logEl = document.getElementById('simLog');
    try {
        const res = await fetch(`${API_BASE}/admin/simulation/step`, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify({
                action: 'ADVANCE_CLOCK',
                minutes: 2,
                currentTime: newTime
            })
        });

        const data = await res.json().catch(() => ({}));
        if (data && typeof data.simulationElapsedMinutes === 'number') {
            state.simClockMinutes = 600 + data.simulationElapsedMinutes;
            const updatedTime = formatMinutesToTime(state.simClockMinutes);
            document.getElementById('statSimTime').textContent = updatedTime;
            document.getElementById('simClockDisplay').textContent = updatedTime;
        }
        logEl.textContent = `[${newTime}] Clock stepped +2 mins. Backend response: ${JSON.stringify(data, null, 2)}\n` + logEl.textContent;
        // Refresh fleet availability
        fetchBuses();
    } catch (err) {
        logEl.textContent = `[${newTime}] Error advancing clock: ${err.message}\n` + logEl.textContent;
    }
});

document.getElementById('applyDemandAdjustmentBtn').addEventListener('click', async () => {
    const tripId = document.getElementById('simTripSelect').value;
    const delta = parseInt(document.getElementById('simDemandDelta').value, 10);
    const logEl = document.getElementById('simLog');

    if (!tripId) {
        alert('Please select a target trip first.');
        return;
    }

    try {
        const res = await fetch(`${API_BASE}/admin/simulation/step`, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify({
                action: 'ADJUST_DEMAND',
                tripId: parseInt(tripId, 10),
                demandDelta: delta
            })
        });

        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            throw new Error(data.message || `HTTP ${res.status}`);
        }
        logEl.textContent = `[Adjustment] Trip #${tripId} adjusted by ${delta > 0 ? '+' : ''}${delta}. Response: ${JSON.stringify(data, null, 2)}\n` + logEl.textContent;
        window.loadTripDemand(tripId);
    } catch (err) {
        logEl.textContent = `[Adjustment Error] ${err.message}\n` + logEl.textContent;
    }
});

// Allocation Engine Controls
document.getElementById('runAllocationBtn').addEventListener('click', async () => {
    const loading = document.getElementById('allocationLoading');
    const panel = document.getElementById('allocationResultsPanel');
    const tbody = document.querySelector('#allocationTable tbody');
    const feedback = document.getElementById('approvalFeedback');
    feedback.textContent = '';

    loading.hidden = false;
    panel.hidden = true;

    try {
        const res = await fetch(`${API_BASE}/officer/allocations`, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify({
                sessionId: 1,
                serviceDate: '2026-11-04'
            })
        });

        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const proposal = await res.json();
        state.lastAllocationProposal = proposal;

        loading.hidden = true;
        panel.hidden = false;

        document.getElementById('allocStatusBadge').textContent = proposal.status || 'PROPOSED';
        document.getElementById('allocAssignedCount').textContent = proposal.assignments ? proposal.assignments.length : 0;
        document.getElementById('allocBaselineUnmet').textContent = proposal.baselineUnmetDemand ?? 0;
        document.getElementById('allocProposedUnmet').textContent = proposal.proposedUnmetDemand ?? 0;

        if (!proposal.assignments || proposal.assignments.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center">No assignments generated.</td></tr>';
            return;
        }

        tbody.innerHTML = proposal.assignments.map(a => {
            const isChanged = a.baselineBusId !== a.proposedBusId;
            const changeTag = isChanged 
                ? '<span class="status-badge badge-changed">Reassigned for Demand</span>'
                : '<span class="status-badge badge-same">Retained</span>';

            return `
                <tr>
                    <td><strong>#${a.tripId}</strong></td>
                    <td>${a.departureTime || '-'}</td>
                    <td>${a.baselineBusNumber ? 'Bus #' + a.baselineBusNumber : 'None'}</td>
                    <td>${a.baselineCapacity || 0}</td>
                    <td><strong>${a.proposedBusNumber ? 'Bus #' + a.proposedBusNumber : 'Unassigned'}</strong></td>
                    <td><strong>${a.proposedCapacity || 0}</strong></td>
                    <td>${changeTag}</td>
                </tr>
            `;
        }).join('');
    } catch (err) {
        loading.hidden = true;
        alert(`Allocation computation failed: ${err.message}`);
    }
});

document.getElementById('approveAllocationBtn').addEventListener('click', async () => {
    if (!state.lastAllocationProposal || !state.lastAllocationProposal.allocationRunId) {
        alert('No active allocation proposal to approve. Please run allocation first.');
        return;
    }

    const feedback = document.getElementById('approvalFeedback');
    feedback.textContent = 'Persisting approved allocation to database...';
    feedback.className = 'feedback-msg text-info';

    try {
        const res = await fetch(`${API_BASE}/officer/allocations/approve`, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify({
                allocationRunId: state.lastAllocationProposal.allocationRunId
            })
        });

        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const result = await res.json();

        feedback.textContent = `Allocation #${state.lastAllocationProposal.allocationRunId} successfully APPROVED and persisted into operational schedule!`;
        feedback.className = 'feedback-msg text-success';
        document.getElementById('allocStatusBadge').textContent = 'APPROVED';
        document.getElementById('allocStatusBadge').className = 'metric-value badge-approved';

        // Refresh trips and buses
        fetchTrips();
        fetchBuses();
    } catch (err) {
        feedback.textContent = `Approval failed: ${err.message}`;
        feedback.className = 'feedback-msg text-danger';
    }
});

// Button triggers
document.getElementById('refreshBusesBtn').addEventListener('click', fetchBuses);
document.getElementById('refreshTripsBtn').addEventListener('click', fetchTrips);

// Session Configuration (SRS F4 / U2)
function renderSessionConfig() {
    const routesContainer = document.getElementById('sessionRoutesList');
    const busesContainer = document.getElementById('sessionBusesList');

    if (routesContainer && state.routes) {
        if (state.routes.length === 0) {
            routesContainer.innerHTML = '<p class="text-muted">No routes available.</p>';
        } else {
            routesContainer.innerHTML = state.routes.map(r => `
                <div class="checkbox-item" style="padding: 6px 0;">
                    <label style="display: flex; align-items: center; gap: 8px; cursor: pointer;">
                        <input type="checkbox" class="session-route-check" value="${r.routeId}" checked>
                        <span><strong>${r.routeNumber || 'Route #' + r.routeId}</strong>: ${r.origin || 'Origin'} &rarr; ${r.destination || 'Destination'}</span>
                    </label>
                </div>
            `).join('');
        }
    }

    if (busesContainer && state.buses) {
        if (state.buses.length === 0) {
            busesContainer.innerHTML = '<p class="text-muted">No buses available.</p>';
        } else {
            busesContainer.innerHTML = state.buses.map(b => `
                <div class="checkbox-item" style="padding: 6px 0;">
                    <label style="display: flex; align-items: center; gap: 8px; cursor: pointer;">
                        <input type="checkbox" class="session-bus-check" value="${b.busId}" checked>
                        <span><strong>${b.registrationNumber || 'Bus #' + b.busId}</strong> (${b.capacity} seats, ${b.status || 'ACTIVE'})</span>
                    </label>
                </div>
            `).join('');
        }
    }
}

const saveSessionConfigBtn = document.getElementById('saveSessionConfigBtn');
if (saveSessionConfigBtn) {
    saveSessionConfigBtn.addEventListener('click', async () => {
        const feedback = document.getElementById('sessionConfigFeedback');
        feedback.textContent = 'Saving session configuration...';
        feedback.className = 'feedback-msg text-info';

        const selectedRoutes = Array.from(document.querySelectorAll('.session-route-check:checked'))
            .map(cb => parseInt(cb.value, 10));
        const selectedBuses = Array.from(document.querySelectorAll('.session-bus-check:checked'))
            .map(cb => parseInt(cb.value, 10));

        if (selectedRoutes.length === 0 || selectedBuses.length === 0) {
            feedback.textContent = 'Please select at least one route and one bus for the session.';
            feedback.className = 'feedback-msg text-danger';
            return;
        }

        try {
            const res = await fetch(`${API_BASE}/officer/sessions/1/selection`, {
                method: 'PUT',
                headers: getAuthHeaders(),
                body: JSON.stringify({
                    routeIds: selectedRoutes,
                    busIds: selectedBuses
                })
            });

            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            feedback.textContent = `Session configuration saved (${selectedRoutes.length} routes, ${selectedBuses.length} buses active).`;
            feedback.className = 'feedback-msg text-success';
        } catch (err) {
            feedback.textContent = `Failed to save session configuration: ${err.message}`;
            feedback.className = 'feedback-msg text-danger';
        }
    });
}

const refreshSessionConfigBtn = document.getElementById('refreshSessionConfigBtn');
if (refreshSessionConfigBtn) {
    refreshSessionConfigBtn.addEventListener('click', async () => {
        await Promise.all([fetchRoutes(), fetchBuses()]);
        renderSessionConfig();
        const feedback = document.getElementById('sessionConfigFeedback');
        if (feedback) {
            feedback.textContent = 'Session data refreshed.';
            feedback.className = 'feedback-msg text-info';
        }
    });
}

// Initialize on page load
checkAuth();
