package vaahansetu.desktop.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.*;
import vaahansetu.desktop.service.DesktopServices;

public final class LoginFrame extends JFrame {
    private final DesktopServices services;
    private final boolean isLive;
    private final String backendUrl;
    private final JTextField username = new JTextField("admin", 18);
    private final JPasswordField password = new JPasswordField("admin123", 18);

    public LoginFrame(DesktopServices services) {
        this(services, services != null && services.auth().getClass().getName().contains("HttpAuthService"), "http://localhost:8080/api");
    }

    public LoginFrame(DesktopServices services, boolean isLive, String backendUrl) {
        super("VaahanSetu - Officer Console Login " + (isLive ? "[LIVE]" : "[DEMO MODE]"));
        this.services = services;
        this.isLive = isLive;
        this.backendUrl = backendUrl;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(480, 360);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        JLabel title = new JLabel("VaahanSetu Officer Console", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel subtitle = new JLabel("Bus planning, passenger demand and allocation", SwingConstants.CENTER);
        JPanel head = new JPanel(new GridLayout(0, 1, 0, 5));
        head.add(title);
        head.add(subtitle);

        JLabel statusLabel = new JLabel(isLive
                ? "● LIVE: Connected to " + backendUrl
                : "● DEMO / OFFLINE MODE (Mock Data)", SwingConstants.CENTER);
        statusLabel.setForeground(isLive ? new java.awt.Color(20, 130, 40) : new java.awt.Color(190, 100, 0));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 12f));
        head.add(statusLabel);

        root.add(head, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 12));
        form.add(new JLabel("Username"));
        form.add(username);
        form.add(new JLabel("Password"));
        form.add(password);
        JButton signIn = new JButton("Sign in");
        signIn.addActionListener(e -> signIn());
        password.addActionListener(e -> signIn());
        JPanel center = new JPanel(new BorderLayout(8, 12));
        center.add(form, BorderLayout.CENTER);
        center.add(signIn, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);

        JLabel demo = new JLabel("Admin: admin / admin123  |  Officer: officer1 / officer123", SwingConstants.CENTER);
        demo.setForeground(new java.awt.Color(85, 100, 115));
        root.add(demo, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void signIn() {
        try {
            boolean success = services.auth().login(username.getText().trim(), new String(password.getPassword()));
            if (!success) {
                password.setText("");
                JOptionPane.showMessageDialog(this, "Incorrect username or password.",
                        "Sign-in failed", JOptionPane.ERROR_MESSAGE);
                password.requestFocusInWindow();
                return;
            }
            new OfficerConsoleFrame(services.auth().currentDisplayName(),
                    services.auth().currentRole(), services, isLive, backendUrl).setVisible(true);
            dispose();
        } catch (Exception exception) {
            UiSupport.error(this, exception);
        }
    }
}
