package vaahansetu.desktop.ui;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import vaahansetu.desktop.demo.MockServices;
import vaahansetu.desktop.service.DesktopServices;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Use Swing's default look and feel if the system one is unavailable.
            }
            DesktopServices services = new MockServices().create();
            new LoginFrame(services).setVisible(true);
        });
    }
}
