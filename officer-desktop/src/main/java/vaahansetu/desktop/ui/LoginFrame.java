package vaahansetu.desktop.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.*;
import vaahansetu.desktop.service.DesktopServices;

public final class LoginFrame extends JFrame {
    private final DesktopServices services;
    private final JTextField username = new JTextField("admin", 18);
    private final JPasswordField password = new JPasswordField("admin123", 18);

    public LoginFrame(DesktopServices services) {
        super("VaahanSetu - Officer Console Login");
        this.services = services;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(460, 330);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(24, 30, 24, 30));
        JLabel title = new JLabel("VaahanSetu Officer Console", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 23f));
        JLabel subtitle = new JLabel("Bus planning, passenger demand and allocation", SwingConstants.CENTER);
        JPanel head = new JPanel(new GridLayout(0, 1, 0, 7));
        head.add(title);
        head.add(subtitle);
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

        JLabel demo = new JLabel("DEMO ONLY  |  Admin: admin / admin123  |  Officer: officer / officer123", SwingConstants.CENTER);
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
                    services.auth().currentRole(), services).setVisible(true);
            dispose();
        } catch (Exception exception) {
            UiSupport.error(this, exception);
        }
    }
}
