package vaahansetu.desktop.ui;

import java.awt.BorderLayout;
import java.time.LocalTime;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import vaahansetu.desktop.model.Bus;
import vaahansetu.desktop.service.BusService;

/** Read-only display of predefined buses, including both rest constraints. */
public final class BusesPanel extends JPanel {
    private final BusService service;
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{
            "Bus ID", "Registration", "Capacity", "Status", "Current Location",
            "Rest Duration (min)", "Rest Start", "Rest End"
    }, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JLabel message = new JLabel("Predefined fleet data. Bus records are managed in PostgreSQL.");

    public BusesPanel(BusService service) {
        this.service = service;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {70, 115, 75, 100, 130, 115, 95, 95};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.setRowHeight(25);
        JButton refresh = new JButton("Refresh buses");
        refresh.addActionListener(event -> refreshTable());
        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(new JLabel("Bus fleet and availability information"), BorderLayout.WEST);
        top.add(refresh, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        message.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));
        add(message, BorderLayout.SOUTH);
        refreshTable();
    }

    public void refreshTable() {
        try {
            model.setRowCount(0);
            for (Bus bus : service.listBuses()) {
                model.addRow(new Object[]{bus.busId(), bus.registrationNumber(), bus.capacity(),
                        bus.status(), bus.currentLocation(), bus.restDurationMinutes(),
                        showTime(bus.restStart()), showTime(bus.restEnd())});
            }
            message.setText("Rest Duration = minimum rest after a trip. Rest Start / Rest End = recurring daily rest window.");
        } catch (Exception exception) {
            model.setRowCount(0);
            message.setText("Unable to load buses: " + exception.getMessage());
        }
    }

    private String showTime(LocalTime time) {
        return time == null ? "—" : time.toString();
    }
}
