package vaahansetu.desktop.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import vaahansetu.desktop.model.*;
import vaahansetu.desktop.service.DesktopServices;

/** Planning, segment demand, admin simulation, baseline/proposed comparison and approval. */
public final class AllocationPanel extends JPanel {
    private final DesktopServices services;
    private final JPanel topStack = new JPanel();
    private final UserRole role;
    private final Runnable afterApply;

    private final DefaultListModel<Route> routeListModel = new DefaultListModel<>();
    private final DefaultListModel<Bus> busListModel = new DefaultListModel<>();
    private final JList<Route> routeList = new JList<>(routeListModel);
    private final JList<Bus> busList = new JList<>(busListModel);
    private final JComboBox<ScheduledTrip> tripBox = new JComboBox<>();
    private final DefaultListModel<SegmentDemand> segmentListModel = new DefaultListModel<>();
    private final JList<SegmentDemand> segmentList = new JList<>(segmentListModel);

    private final DefaultTableModel demandModel = readOnlyModel(
            "Segment Start", "Segment End", "Registered", "Simulated Δ", "Effective Demand",
            "Current Bus", "Current Capacity", "Demand Status");
    private final DefaultTableModel comparisonModel = readOnlyModel(
            "Trip", "Route", "Segment", "Baseline Demand", "Baseline Capacity",
            "Baseline Unmet", "Proposed Demand", "Proposed Capacity", "Proposed Unmet", "Proposed Unused");
    private final DefaultTableModel assignmentModel = readOnlyModel(
            "Trip", "Route", "Previous Bus", "Proposed Bus", "Type", "Reason");

    private final JTable demandTable = new JTable(demandModel);
    private final JTable comparisonTable = new JTable(comparisonModel);
    private final JTable assignmentTable = new JTable(assignmentModel);

    private final JLabel sessionLabel = new JLabel("Planning session: loading...");
    private final JLabel simulationClock = new JLabel("Simulation clock: T+00:00");
    private final JLabel baselineSummary = new JLabel("BASELINE: not calculated");
    private final JLabel proposedSummary = new JLabel("PROPOSED: not calculated");
    private final JLabel status = new JLabel("Demo mode: values shown here are in-memory sample data until backend APIs are connected.");

    private final JButton saveSelectionButton = new JButton("Save planning selection");
    private final JButton refreshButton = new JButton("Refresh demand");
    private final JButton simulateButton = new JButton("Admin: Simulate +2 Minutes");
    private final JButton runButton = new JButton("Run Allocation (Proposal Only)");
    private final JButton approveButton = new JButton("Approve & Apply");

    private AllocationResult pending;

    public AllocationPanel(DesktopServices services, UserRole role, Runnable afterApply) {
        this.services = services;
        this.role = role;
        this.afterApply = afterApply;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));

        buildPlanningPanel();
        buildControls();
        buildResultArea();
        add(topStack, BorderLayout.NORTH);

        simulateButton.setVisible(role == UserRole.ADMIN);
        approveButton.setEnabled(false);
        saveSelectionButton.addActionListener(event -> savePlanningSelection());
        refreshButton.addActionListener(event -> refreshDemand());
        simulateButton.addActionListener(event -> simulate());
        runButton.addActionListener(event -> runAllocation());
        approveButton.addActionListener(event -> approveAndApply());
        tripBox.addActionListener(event -> refreshDemand());

        refreshData();
    }

    private void buildPlanningPanel() {
        JPanel routePanel = new JPanel(new BorderLayout(4, 4));
        routePanel.setBorder(BorderFactory.createTitledBorder("Routes included in this planning session"));
        routeList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        routePanel.add(new JScrollPane(routeList), BorderLayout.CENTER);

        JPanel busPanel = new JPanel(new BorderLayout(4, 4));
        busPanel.setBorder(BorderFactory.createTitledBorder("Available bus pool for this planning session"));
        busList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        busPanel.add(new JScrollPane(busList), BorderLayout.CENTER);

        JPanel lists = new JPanel(new GridLayout(1, 2, 8, 4));
        lists.add(routePanel);
        lists.add(busPanel);
        lists.setPreferredSize(new Dimension(900, 125));

        JPanel heading = new JPanel(new BorderLayout(8, 4));
        heading.add(sessionLabel, BorderLayout.WEST);
        heading.add(saveSelectionButton, BorderLayout.EAST);
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(heading, BorderLayout.NORTH);
        panel.add(lists, BorderLayout.CENTER);
        topStack.add(panel);
    }

    private void buildControls() {
        JPanel demandControls = new JPanel(new BorderLayout(8, 4));
        demandControls.setBorder(BorderFactory.createTitledBorder("Demand and simulation controls"));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.add(new JLabel("Scheduled trip:"));
        tripBox.setPreferredSize(new Dimension(300, 26));
        top.add(tripBox);
        top.add(refreshButton);
        top.add(simulateButton);
        top.add(simulationClock);
        demandControls.add(top, BorderLayout.NORTH);

        JPanel segmentPicker = new JPanel(new BorderLayout(4, 4));
        segmentPicker.add(new JLabel("Select segment(s) to simulate (admin only):"), BorderLayout.WEST);
        segmentList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        segmentList.setVisibleRowCount(2);
        JScrollPane pickerScroll = new JScrollPane(segmentList);
        pickerScroll.setPreferredSize(new Dimension(620, 54));
        segmentPicker.add(pickerScroll, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(runButton);
        actions.add(approveButton);
        segmentPicker.add(actions, BorderLayout.EAST);
        demandControls.add(segmentPicker, BorderLayout.CENTER);
        demandControls.setPreferredSize(new Dimension(1100, 115));

        JPanel summaries = new JPanel(new GridLayout(1, 2, 8, 4));
        baselineSummary.setBorder(BorderFactory.createTitledBorder("Baseline / current assignments"));
        proposedSummary.setBorder(BorderFactory.createTitledBorder("Proposed allocation"));
        baselineSummary.setFont(baselineSummary.getFont().deriveFont(Font.BOLD, 12f));
        proposedSummary.setFont(proposedSummary.getFont().deriveFont(Font.BOLD, 12f));
        summaries.add(baselineSummary);
        summaries.add(proposedSummary);
        summaries.setPreferredSize(new Dimension(1100, 88));
        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(demandControls, BorderLayout.NORTH);
        center.add(summaries, BorderLayout.SOUTH);
        topStack.add(center);
    }

    private void buildResultArea() {
        demandTable.setFillsViewportHeight(true);
        comparisonTable.setFillsViewportHeight(true);
        assignmentTable.setFillsViewportHeight(true);
        demandTable.setAutoCreateRowSorter(true);
        comparisonTable.setAutoCreateRowSorter(true);
        assignmentTable.setAutoCreateRowSorter(true);
        demandTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        comparisonTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        assignmentTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        setPreferredWidths(demandTable, 125, 125, 90, 90, 115, 105, 95, 145);
        setPreferredWidths(comparisonTable, 55, 65, 135, 95, 100, 90, 95, 100, 90, 90);
        setPreferredWidths(assignmentTable, 60, 65, 100, 100, 105, 330);
        demandTable.setRowHeight(23);
        comparisonTable.setRowHeight(23);
        assignmentTable.setRowHeight(23);

        JTabbedPane resultTabs = new JTabbedPane();
        resultTabs.addTab("Selected trip — segment demand", new JScrollPane(demandTable));
        resultTabs.addTab("Baseline vs proposed — segment comparison", new JScrollPane(comparisonTable));
        resultTabs.addTab("Bus assignment changes", new JScrollPane(assignmentTable));
        resultTabs.setPreferredSize(new Dimension(1100, 370));

        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        status.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));
        bottom.add(resultTabs, BorderLayout.CENTER);
        bottom.add(status, BorderLayout.SOUTH);
        add(bottom, BorderLayout.CENTER);
    }

    private void setPreferredWidths(JTable table, int... widths) {
        for (int i = 0; i < widths.length && i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    public void refreshData() {
        try {
            sessionLabel.setText("Planning session: " + services.planning().activeSessionName()
                    + " (ID " + services.planning().activeSessionId() + ")");
            simulationClock.setText("Simulation clock: " + formatTime(services.simulation().elapsedMinutes()));
            loadPlanningLists();
            refreshTripOptions();
            refreshDemand();
        } catch (Exception exception) {
            UiSupport.error(this, exception);
            status.setText("Unable to load planning data: " + exception.getMessage());
        }
    }

    private void loadPlanningLists() throws Exception {
        Set<Long> selectedRouteIds = services.planning().selectedRouteIds();
        Set<Long> selectedBusIds = services.planning().selectedBusIds();
        routeListModel.clear();
        List<Route> routes = services.routes().listRoutes();
        for (Route route : routes) routeListModel.addElement(route);
        busListModel.clear();
        List<Bus> buses = services.buses().listBuses();
        for (Bus bus : buses) { if (bus.status() == BusStatus.ACTIVE) busListModel.addElement(bus); }
        selectRoutes(selectedRouteIds);
        selectBuses(selectedBusIds);
    }

    private void selectRoutes(Set<Long> ids) {
        List<Integer> selected = new ArrayList<>();
        for (int i = 0; i < routeListModel.size(); i++) {
            if (ids.contains(routeListModel.get(i).routeId())) selected.add(i);
        }
        routeList.setSelectedIndices(selected.stream().mapToInt(Integer::intValue).toArray());
    }

    private void selectBuses(Set<Long> ids) {
        List<Integer> selected = new ArrayList<>();
        for (int i = 0; i < busListModel.size(); i++) {
            Bus bus = busListModel.get(i);
            if (ids.contains(bus.busId())) selected.add(i);
        }
        busList.setSelectedIndices(selected.stream().mapToInt(Integer::intValue).toArray());
    }

    private void savePlanningSelection() {
        try {
            Set<Long> routeIds = new LinkedHashSet<>();
            for (Route route : routeList.getSelectedValuesList()) routeIds.add(route.routeId());
            Set<Long> busIds = new LinkedHashSet<>();
            for (Bus bus : busList.getSelectedValuesList()) busIds.add(bus.busId());
            services.planning().saveSelection(routeIds, busIds);
            pending = null;
            approveButton.setEnabled(false);
            clearResults();
            refreshTripOptions();
            refreshDemand();
            status.setText("Planning selection saved. Allocation must be run again for the selected routes and buses.");
            UiSupport.info(this, "Planning session selections saved.");
        } catch (Exception exception) {
            UiSupport.error(this, exception);
        }
    }

    private void refreshTripOptions() {
        Object previous = tripBox.getSelectedItem();
        tripBox.removeAllItems();
        try {
            Set<Long> selectedRouteIds = services.planning().selectedRouteIds();
            for (ScheduledTrip trip : services.trips().listTrips()) {
                if (selectedRouteIds.contains(trip.getRouteId())) tripBox.addItem(trip);
            }
            if (previous instanceof ScheduledTrip previousTrip) {
                for (int i = 0; i < tripBox.getItemCount(); i++) {
                    if (tripBox.getItemAt(i).getTripId() == previousTrip.getTripId()) {
                        tripBox.setSelectedIndex(i);
                        return;
                    }
                }
            }
            if (tripBox.getItemCount() > 0) tripBox.setSelectedIndex(0);
        } catch (Exception exception) {
            UiSupport.error(this, exception);
        }
    }

    private void refreshDemand() {
        ScheduledTrip selected = (ScheduledTrip) tripBox.getSelectedItem();
        demandModel.setRowCount(0);
        segmentListModel.clear();
        if (selected == null) {
            status.setText("No scheduled trips are available for the selected planning routes.");
            return;
        }
        try {
            List<SegmentDemand> rows = services.demand().demandForTrip(selected.getTripId());
            for (SegmentDemand row : rows) {
                segmentListModel.addElement(row);
                demandModel.addRow(new Object[]{row.startStopName(), row.endStopName(),
                        row.registeredDemand(), signed(row.simulatedAdjustment()), row.effectiveDemand(),
                        row.currentBus(), row.currentCapacity(), row.statusLabel()});
            }
            if (segmentListModel.size() > 0) segmentList.setSelectionInterval(0, segmentListModel.size() - 1);
            status.setText("Demand is calculated per route segment. Simulated adjustments are separate from passenger registrations.");
        } catch (Exception exception) {
            UiSupport.error(this, exception);
        }
    }

    private void simulate() {
        if (role != UserRole.ADMIN) {
            UiSupport.info(this, "Only an administrator can run the simulation.");
            return;
        }
        ScheduledTrip selectedTrip = (ScheduledTrip) tripBox.getSelectedItem();
        if (selectedTrip == null) {
            UiSupport.info(this, "Select a scheduled trip first.");
            return;
        }
        List<SegmentDemand> selectedRows = segmentList.getSelectedValuesList();
        if (selectedRows.isEmpty()) {
            UiSupport.info(this, "Select at least one segment to simulate.");
            return;
        }
        List<SegmentKey> selectedKeys = selectedRows.stream().map(SegmentDemand::key).toList();
        String prompt = "Apply one simulation step (+/- 5 to 10 passengers) to " + selectedKeys.size()
                + " selected segment(s) of trip " + selectedTrip.getTripId() + "?\n\n"
                + "One click represents two simulated minutes. Actual passenger registrations will not be changed.";
        if (!UiSupport.confirm(this, prompt)) return;

        simulateButton.setEnabled(false);
        try {
            SimulationStepResult result = services.simulation().simulateTwoMinutes(selectedTrip.getTripId(), selectedKeys);
            simulationClock.setText("Simulation clock: " + formatTime(result.elapsedMinutes()));
            pending = null;
            approveButton.setEnabled(false);
            clearResults();
            refreshDemand();
            status.setText("SIMULATED: " + result.message() + " A fresh allocation proposal is required.");
            runAllocation();
        } catch (Exception exception) {
            UiSupport.error(this, exception);
        } finally {
            simulateButton.setEnabled(true);
        }
    }

    private void runAllocation() {
        runButton.setEnabled(false);
        approveButton.setEnabled(false);
        pending = null;
        clearResults();
        try {
            pending = services.allocation().runAllocation();
            renderResult(pending);
            approveButton.setEnabled("PROPOSED".equals(pending.status()));
            status.setText("Proposal " + pending.allocationRunId() + " created. Review the baseline/proposed comparison. Current assignments have not changed.");
        } catch (Exception exception) {
            UiSupport.error(this, exception);
            status.setText("Allocation could not be calculated: " + exception.getMessage());
        } finally {
            runButton.setEnabled(true);
        }
    }

    private void renderResult(AllocationResult result) {
        baselineSummary.setText(formatMetrics("BASELINE", result.baselineMetrics()));
        proposedSummary.setText(formatMetrics("PROPOSED", result.proposedMetrics()));
        comparisonModel.setRowCount(0);
        int rows = Math.min(result.baselineSegments().size(), result.proposedSegments().size());
        for (int i = 0; i < rows; i++) {
            SegmentAllocationResult before = result.baselineSegments().get(i);
            SegmentAllocationResult after = result.proposedSegments().get(i);
            comparisonModel.addRow(new Object[]{before.tripId(), before.routeCode(),
                    before.startStopName() + " → " + before.endStopName(),
                    before.demand(), before.allocatedCapacity(), before.unmet(),
                    after.demand(), after.allocatedCapacity(), after.unmet(), after.unused()});
        }
        assignmentModel.setRowCount(0);
        for (AllocationAssignment row : result.assignments()) {
            assignmentModel.addRow(new Object[]{row.tripId(), row.routeCode(), row.previousBus(),
                    row.proposedBus(), row.type(), row.reason()});
        }
    }

    private String formatMetrics(String title, AllocationMetrics m) {
        return "<html><b>" + title + "</b><br>Segment demand: " + m.totalSegmentDemand()
                + "<br>Accommodated: " + m.accommodatedPassengerSegments()
                + " &nbsp; | &nbsp; Unmet: " + m.unmetPassengerSegments()
                + "<br>Unused seat-segments: " + m.unusedSeatSegments() + "</html>";
    }

    private void approveAndApply() {
        if (pending == null || !"PROPOSED".equals(pending.status())) {
            UiSupport.info(this, "Run allocation first. There is no current proposal to apply.");
            return;
        }
        if (!UiSupport.confirm(this, "Approve and apply allocation proposal " + pending.allocationRunId()
                + "?\nThe backend should validate and update assignments transactionally.")) return;
        approveButton.setEnabled(false);
        try {
            services.allocation().approveAndApply(pending.allocationRunId());
            pending = null;
            services.trips().refresh();
            refreshDemand();
            afterApply.run();
            status.setText("Proposal approved and applied. Trips & Assignments has been refreshed.");
            UiSupport.info(this, "Proposal applied successfully in the current demo service.");
        } catch (Exception exception) {
            approveButton.setEnabled(pending != null && "PROPOSED".equals(pending.status()));
            UiSupport.error(this, exception);
        }
    }

    private void clearResults() {
        comparisonModel.setRowCount(0);
        assignmentModel.setRowCount(0);
        baselineSummary.setText("BASELINE: not calculated");
        proposedSummary.setText("PROPOSED: not calculated");
    }

    private String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }
    private String formatTime(int minutes) { return String.format("T+%02d:%02d", minutes / 60, minutes % 60); }
}
