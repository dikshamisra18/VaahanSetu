package vaahansetu.desktop.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import vaahansetu.desktop.model.Route;
import vaahansetu.desktop.model.RouteStop;
import vaahansetu.desktop.service.RouteService;

/** Read-only view of predefined routes and their ordered stops. */
public final class RoutesPanel extends JPanel {
    private final RouteService service;
    private final DefaultTableModel routesModel = new DefaultTableModel(new Object[]{
            "Route ID", "Code", "Route Name", "Origin", "Destination", "Duration (min)"
    }, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final DefaultTableModel stopsModel = new DefaultTableModel(new Object[]{"Sequence", "Stop ID", "Stop Name"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable routesTable = new JTable(routesModel);
    private final JTable stopsTable = new JTable(stopsModel);
    private final JLabel selectedRouteLabel = new JLabel("Select a route to view its ordered stops.");

    public RoutesPanel(RouteService service) {
        this.service = service;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        routesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        routesTable.setFillsViewportHeight(true);
        stopsTable.setFillsViewportHeight(true);
        routesTable.setAutoCreateRowSorter(true);
        routesTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        routesTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) refreshStops();
        });
        JButton refresh = new JButton("Refresh routes");
        refresh.addActionListener(event -> refreshRoutes());
        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(new JLabel("Predefined active routes"), BorderLayout.WEST);
        top.add(refresh, BorderLayout.EAST);
        JPanel left = new JPanel(new BorderLayout(5, 5));
        left.add(new JLabel("Routes"), BorderLayout.NORTH);
        left.add(new JScrollPane(routesTable), BorderLayout.CENTER);
        JPanel right = new JPanel(new BorderLayout(5, 5));
        right.add(selectedRouteLabel, BorderLayout.NORTH);
        right.add(new JScrollPane(stopsTable), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.55);
        split.setPreferredSize(new Dimension(1000, 550));
        add(top, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        refreshRoutes();
    }

    public void refreshRoutes() {
        Long keep = selectedRouteId();
        routesModel.setRowCount(0);
        try {
            for (Route route : service.listRoutes()) {
                routesModel.addRow(new Object[]{route.routeId(), route.routeCode(), route.routeName(),
                        route.originName(), route.destinationName(), route.estimatedDurationMinutes()});
            }
            if (routesModel.getRowCount() > 0) {
                int rowToSelect = 0;
                if (keep != null) {
                    for (int i = 0; i < routesModel.getRowCount(); i++) {
                        if (((Number) routesModel.getValueAt(i, 0)).longValue() == keep) rowToSelect = i;
                    }
                }
                routesTable.setRowSelectionInterval(rowToSelect, rowToSelect);
            } else {
                stopsModel.setRowCount(0);
                selectedRouteLabel.setText("No active routes available.");
            }
        } catch (Exception exception) {
            UiSupport.error(this, exception);
        }
    }

    private Long selectedRouteId() {
        int row = routesTable.getSelectedRow();
        if (row < 0) return null;
        return ((Number) routesModel.getValueAt(routesTable.convertRowIndexToModel(row), 0)).longValue();
    }

    private void refreshStops() {
        stopsModel.setRowCount(0);
        Long routeId = selectedRouteId();
        if (routeId == null) return;
        try {
            Route route = service.listRoutes().stream().filter(r -> r.routeId() == routeId).findFirst().orElse(null);
            selectedRouteLabel.setText(route == null ? "Ordered stops" : route.routeCode() + " — stops in travel order");
            for (RouteStop stop : service.stopsOfRoute(routeId)) {
                stopsModel.addRow(new Object[]{stop.sequence(), stop.stopId(), stop.stopName()});
            }
        } catch (Exception exception) {
            UiSupport.error(this, exception);
        }
    }
}
