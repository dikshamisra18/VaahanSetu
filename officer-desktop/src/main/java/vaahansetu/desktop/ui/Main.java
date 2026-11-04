package vaahansetu.desktop.ui;

import java.util.Arrays;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import vaahansetu.desktop.api.ApiServices;
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

            String backendUrl = "http://localhost:8080/api";
            if (args != null && args.length > 0 && args[0].startsWith("http")) {
                backendUrl = args[0];
            } else if (System.getProperty("backend.url") != null) {
                backendUrl = System.getProperty("backend.url");
            } else if (System.getenv("VAAHAN_API_URL") != null) {
                backendUrl = System.getenv("VAAHAN_API_URL");
            } else if (System.getenv("API_URL") != null) {
                backendUrl = System.getenv("API_URL");
            }
            boolean forceDemo = Arrays.asList(args).contains("--demo") || Arrays.asList(args).contains("--mock");

            DesktopServices services;
            boolean isLive = false;
            if (!forceDemo && ApiServices.isAvailable(backendUrl)) {
                System.out.println("Connecting to live backend at " + backendUrl);
                services = new ApiServices(backendUrl).create();
                isLive = true;
            } else {
                if (forceDemo) {
                    System.out.println("Starting in forced demo mode (in-memory MockServices)");
                } else {
                    System.out.println("Backend at " + backendUrl + " not reachable. Falling back to MockServices demo mode.");
                }
                services = new MockServices().create();
                isLive = false;
            }

            new LoginFrame(services, isLive, backendUrl).setVisible(true);
        });
    }
}
