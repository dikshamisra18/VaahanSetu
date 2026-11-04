package vaahansetu.desktop.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.*;
import vaahansetu.desktop.model.UserRole;
import vaahansetu.desktop.service.DesktopServices;

public final class OfficerConsoleFrame extends JFrame {
    private final BusesPanel busesPanel;
    private final RoutesPanel routesPanel;
    private final TripsPanel tripsPanel;
    private final AllocationPanel allocationPanel;
    private final DesktopServices services;
    private final boolean isLive;
    private final String backendUrl;

    public OfficerConsoleFrame(String displayName, UserRole role, DesktopServices services) {
        this(displayName, role, services, services != null && services.auth().getClass().getName().contains("HttpAuthService"), "http://localhost:8080/api");
    }

    public OfficerConsoleFrame(String displayName, UserRole role, DesktopServices services, boolean isLive, String backendUrl) {
        super("VaahanSetu - Officer Console " + (isLive ? "[LIVE: " + backendUrl + "]" : "[DEMO / OFFLINE MODE]"));
        this.services = services;
        this.isLive = isLive;
        this.backendUrl = backendUrl;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new java.awt.Dimension(1000, 650));
        setLocationRelativeTo(null);

        busesPanel = new BusesPanel(services.buses());
        routesPanel = new RoutesPanel(services.routes());
        tripsPanel = new TripsPanel(services);
        allocationPanel = new AllocationPanel(services, role, () -> {
            try {
                tripsPanel.refreshTable();
                busesPanel.refreshTable();
            } catch (Exception exception) {
                UiSupport.error(this, exception);
            }
        });

        JPanel header = new JPanel(new BorderLayout(10, 8));
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));
        JLabel title = new JLabel("VaahanSetu  |  Officer Console");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));

        JLabel statusBadge = new JLabel(isLive ? "  \u25CF LIVE BACKEND  " : "  \u25CF DEMO / OFFLINE MODE  ");
        statusBadge.setOpaque(true);
        if (isLive) {
            statusBadge.setBackground(new Color(220, 245, 225));
            statusBadge.setForeground(new Color(20, 120, 30));
            statusBadge.setBorder(BorderFactory.createLineBorder(new Color(160, 220, 175), 1));
        } else {
            statusBadge.setBackground(new Color(255, 238, 215));
            statusBadge.setForeground(new Color(180, 95, 0));
            statusBadge.setBorder(BorderFactory.createLineBorder(new Color(240, 200, 150), 1));
        }
        statusBadge.setFont(statusBadge.getFont().deriveFont(Font.BOLD, 11f));

        JLabel who = new JLabel(displayName + "  (" + role + ")");
        JButton logout = new JButton("Log out");
        logout.addActionListener(e -> {
            new LoginFrame(services, isLive, backendUrl).setVisible(true);
            dispose();
        });
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.add(statusBadge);
        right.add(who);
        right.add(logout);
        header.add(title, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Buses", busesPanel);
        tabs.addTab("Routes & Stops", routesPanel);
        tabs.addTab("Trips & Assignments", tripsPanel);
        tabs.addTab("Demand & Allocation", allocationPanel);
        tabs.addChangeListener(event -> {
            int selected = tabs.getSelectedIndex();
            try {
                if (selected == 0) busesPanel.refreshTable();
                if (selected == 1) routesPanel.refreshRoutes();
                if (selected == 2) tripsPanel.refreshTable();
                if (selected == 3) allocationPanel.refreshData();
            } catch (Exception exception) {
                UiSupport.error(this, exception);
            }
        });
        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }
}
