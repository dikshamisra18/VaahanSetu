package vaahansetu.desktop.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import vaahansetu.desktop.model.Bus;
import vaahansetu.desktop.model.Route;
import vaahansetu.desktop.model.ScheduledTrip;
import vaahansetu.desktop.service.DesktopServices;

/** Read-only view of predefined scheduled trips and their currently applied assignments. */
public final class TripsPanel extends JPanel {
    private final DesktopServices services;
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{
            "Trip ID", "Route", "Service Date", "Departure", "Arrival", "Status",
            "Current Bus", "Capacity", "Rest Duration", "Rest Start", "Rest End"
    }, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JLabel status = new JLabel("Assignments change only after an approved allocation is applied.");

    public TripsPanel(DesktopServices services) {
        this.services = services;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(24);
        JButton refresh = new JButton("Refresh trips");
        refresh.addActionListener(event -> refreshTable());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Predefined scheduled departures and current bus assignments"));
        top.add(refresh);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        refreshTable();
    }

    public void refreshTable() {
        try {
            services.trips().refresh();
            model.setRowCount(0);
            Map<Long, Route> routeById = new HashMap<>();
            for (Route route : services.routes().listRoutes()) routeById.put(route.routeId(), route);
            Map<Long, Bus> busById = new HashMap<>();
            for (Bus bus : services.buses().listBuses()) busById.put(bus.busId(), bus);
            for (ScheduledTrip trip : services.trips().listTrips()) {
                Route route = routeById.get(trip.getRouteId());
                Bus bus = busById.get(trip.getCurrentBusId());
                model.addRow(new Object[]{trip.getTripId(), route == null ? "?" : route.routeCode() + " - " + route.routeName(),
                        trip.getServiceDate(), trip.getDepartureTime(), trip.getArrivalTime(), trip.getStatus(),
                        bus == null ? "UNASSIGNED" : bus.registrationNumber(),
                        bus == null ? "—" : bus.capacity(),
                        bus == null ? "—" : bus.restDurationMinutes(),
                        bus == null ? "—" : showTime(bus.restStart()),
                        bus == null ? "—" : showTime(bus.restEnd())});
            }
            status.setText("Baseline/current assignments. Proposed assignments are shown in Demand & Allocation until approved.");
        } catch (Exception exception) {
            UiSupport.error(this, exception);
            status.setText("Unable to refresh trips: " + exception.getMessage());
        }
    }

    private String showTime(LocalTime time) { return time == null ? "—" : time.toString(); }
}
