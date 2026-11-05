// VaahanSetu Administrator Portal Logic
const API_BASE = window.VAAHAN_API_URL || (
    (window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1")
        ? "http://localhost:8080/api"
        : `${window.location.origin}/api`
);

const state = {
    token: sessionStorage.getItem('vaahan_auth_token') || '',
    user: JSON.parse(sessionStorage.getItem('vaahan_user') || 'null'),
    activeSession: null,
    simClockMinutes: 600, // Dynamic, synchronized with DB active planning session
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
    if (!state.token || !state.user) {
        window.location.href = 'index.html';
        return;
    }
    // Update user profile display
    const userRoleEl = document.getElementById('userRole');
    const userAvatarEl = document.getElementById('userAvatar');
    if (userRoleEl) userRoleEl.textContent = state.user.role || 'ADMIN';
    if (userAvatarEl) userAvatarEl.textContent = (state.user.username || 'A').substring(0, 1).toUpperCase();

    // Setup Logout
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', () => {
            sessionStorage.removeItem('vaahan_auth_token');
            sessionStorage.removeItem('vaahan_user');
            window.location.href = 'index.html';
        });
    }

    // Setup Navigation Tabs
    initTabs();

    // Initial Data Fetch
    initDashboard();
}

function initTabs() {
    const tabBtns = document.querySelectorAll('.nav-tab');
    const tabContents = document.querySelectorAll('.tab-content');

    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            tabBtns.forEach(b => b.classList.remove('active'));
            tabContents.forEach(c => c.classList.remove('active'));

            btn.classList.add('active');
            const target = btn.getAttribute('data-tab');
            const targetSection = document.getElementById(target);
            if (targetSection) targetSection.classList.add('active');
        });
    });
}

// Initial Data Load
async function initDashboard() {
    await fetchActiveSession();
    await Promise.all([
        fetchBuses(),
        fetchRoutes(),
        fetchTrips()
    ]);
    renderSessionConfig();
}

// Fetch Active Session Clock & Details
async function fetchActiveSession() {
    try {
        const res = await fetch(`${API_BASE}/officer/sessions/active`, {
            headers: getAuthHeaders()
        });
        if (res.ok) {
            const session = await res.json();
            state.activeSession = session;
            if (session) {
                let baseMinutes = 600;
                if (session.startedAt) {
                    try {
                        const d = new Date(session.startedAt);
                        if (!isNaN(d.getTime())) {
                            baseMinutes = d.getUTCHours() * 60 + d.getUTCMinutes();
                        }
                        const dateFilter = document.getElementById('tripDateFilter');
                        if (dateFilter && !dateFilter.value) {
                            dateFilter.value = session.startedAt.substring(0, 10);
                        }
                    } catch (e) {
                        console.warn('Could not parse session startedAt:', e);
                    }
                }
                const elapsed = typeof session.simulationElapsedMinutes === 'number' ? session.simulationElapsedMinutes : 0;
                state.simClockMinutes = baseMinutes + elapsed;
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
    const filterEl = document.getElementById('tripDateFilter');
    let dateInput = filterEl ? filterEl.value : '';
    if (!dateInput && state.activeSession && state.activeSession.startedAt) {
        dateInput = state.activeSession.startedAt.substring(0, 10);
        if (filterEl) filterEl.value = dateInput;
    }

    const tbody = document.querySelector('#tripsTable tbody');
    const simTripSelect = document.getElementById('simTripSelect');

    tbody.innerHTML = '<tr><td colspan="7" class="text-center">Loading scheduled departures...</td></tr>';

    try {
        const url = dateInput ? `${API_BASE}/officer/trips?date=${encodeURIComponent(dateInput)}` : `${API_BASE}/officer/trips`;
        const res = await fetch(url, {
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

// Load Segment Demand for Trip Modal/Panel
window.loadTripDemand = async function(tripId) {
    const details = document.getElementById('demandDetails');
    details.innerHTML = '<div class="text-muted">Loading segment demand...</div>';

    // Switch to Trips tab
    document.querySelectorAll('.nav-tab').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
    document.querySelector('[data-tab="tab-trips"]').classList.add('active');
    document.getElementById('tab-trips').classList.add('active');

    try {
        const res = await fetch(`${API_BASE}/officer/trips/${tripId}/demand`, {
            headers: getAuthHeaders()
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const demand = await res.json();

        if (demand.length === 0) {
            details.innerHTML = `<p class="text-muted">No segment demand registered for Trip #${tripId}.</p>`;
            return;
        }

        details.innerHTML = `<h4>Demand Profile: Trip #${tripId} (${demand[0]?.routeCode || 'Route'})</h4>` +
        demand.map(d => {
            const baseD = d.registeredDemand ?? d.baseDemand ?? 0;
            const simAdj = d.simulatedAdjustment ?? 0;
            const effD = d.effectiveDemand ?? d.adjustedDemand ?? baseD;
            const adjLabel = simAdj !== 0 ? (simAdj > 0 ? `+${simAdj}` : `${simAdj}`) : '0';
            return `
                <div class="segment-card">
                    <div class="segment-name">${d.segmentIndex || '#'}. ${d.startStopName || 'Start'} &rarr; ${d.endStopName || 'End'}</div>
                    <div class="segment-metrics">
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
            let baseMins = 600;
            if (state.activeSession?.startedAt) {
                try {
                    const d = new Date(state.activeSession.startedAt);
                    if (!isNaN(d.getTime())) baseMins = d.getUTCHours() * 60 + d.getUTCMinutes();
                } catch (ignored) {}
            }
            state.simClockMinutes = baseMins + data.simulationElapsedMinutes;
            const updatedTime = formatMinutesToTime(state.simClockMinutes);
            document.getElementById('statSimTime').textContent = updatedTime;
            document.getElementById('simClockDisplay').textContent = updatedTime;
        }
        logEl.textContent = `[${newTime}] Clock stepped +2 mins. Response: ${data.message || JSON.stringify(data)}\n` + logEl.textContent;
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
        logEl.textContent = `[Adjustment] Trip #${tripId} adjusted by ${delta > 0 ? '+' : ''}${delta}. Response: ${data.message || 'Updated'}\n` + logEl.textContent;
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
        const dateInput = document.getElementById('tripDateFilter')?.value
            || (state.activeSession?.startedAt ? state.activeSession.startedAt.substring(0, 10) : '');
        const sessionId = state.activeSession?.sessionId || 1;

        const res = await fetch(`${API_BASE}/officer/allocations`, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify({
                sessionId: sessionId,
                serviceDate: dateInput
            })
        });

        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.message || `HTTP ${res.status}`);
        }
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
            const isChanged = a.previousBusId !== a.proposedBusId;
            const changeTag = isChanged 
                ? '<span class="status-badge badge-changed">Reassigned for Demand</span>'
                : '<span class="status-badge badge-same">Retained</span>';

            const prevReg = a.previousBusRegistration || (a.previousBusId ? 'Bus #' + a.previousBusId : 'None');
            const propReg = a.proposedBusRegistration || (a.proposedBusId ? 'Bus #' + a.proposedBusId : 'Unassigned');

            return `
                <tr>
                    <td><strong>#${a.tripId}</strong></td>
                    <td>${a.departureTime ? (a.departureTime.length > 16 ? a.departureTime.substring(11, 16) : a.departureTime) : '-'}</td>
                    <td>${prevReg}</td>
                    <td>${a.previousCapacity ?? a.baselineCapacity ?? '-'}</td>
                    <td><strong>${propReg}</strong></td>
                    <td><strong>${a.proposedCapacity ?? '-'}</strong></td>
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

        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.message || `HTTP ${res.status}`);
        }
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

    const selectedRoutes = state.activeSession?.selectedRouteIds || [];
    const selectedBuses = state.activeSession?.selectedBusIds || [];

    if (routesContainer && state.routes) {
        if (state.routes.length === 0) {
            routesContainer.innerHTML = '<p class="text-muted">No routes available.</p>';
        } else {
            routesContainer.innerHTML = state.routes.map(r => {
                const isChecked = selectedRoutes.length === 0 || selectedRoutes.includes(r.routeId);
                const rCode = r.routeCode || r.routeNumber || ('Route #' + r.routeId);
                const rOrigin = r.originName || r.origin || 'Origin';
                const rDest = r.destinationName || r.destination || 'Destination';
                return `
                <div class="checkbox-item" style="padding: 6px 0;">
                    <label style="display: flex; align-items: center; gap: 8px; cursor: pointer;">
                        <input type="checkbox" class="session-route-check" value="${r.routeId}" ${isChecked ? 'checked' : ''}>
                        <span><strong>${rCode}</strong>: ${rOrigin} &rarr; ${rDest}</span>
                    </label>
                </div>
            `;
            }).join('');
        }
    }

    if (busesContainer && state.buses) {
        if (state.buses.length === 0) {
            busesContainer.innerHTML = '<p class="text-muted">No buses available.</p>';
        } else {
            busesContainer.innerHTML = state.buses.map(b => {
                const isChecked = selectedBuses.length === 0 || selectedBuses.includes(b.busId);
                const bReg = b.registrationNumber || ('Bus #' + b.busId);
                return `
                <div class="checkbox-item" style="padding: 6px 0;">
                    <label style="display: flex; align-items: center; gap: 8px; cursor: pointer;">
                        <input type="checkbox" class="session-bus-check" value="${b.busId}" ${isChecked ? 'checked' : ''}>
                        <span><strong>${bReg}</strong> (${b.capacity} seats, ${b.status || 'ACTIVE'})</span>
                    </label>
                </div>
            `;
            }).join('');
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

        const sessionId = state.activeSession?.sessionId || 1;

        try {
            const res = await fetch(`${API_BASE}/admin/sessions/${sessionId}/selection`, {
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
