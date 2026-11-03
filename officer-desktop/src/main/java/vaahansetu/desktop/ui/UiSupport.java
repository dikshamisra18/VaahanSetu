package vaahansetu.desktop.ui;

import java.awt.Component;
import javax.swing.JOptionPane;
import vaahansetu.desktop.service.ServiceException;

final class UiSupport {
    private UiSupport() { }

    static void error(Component parent, Exception exception) {
        String message = exception.getMessage();
        JOptionPane.showMessageDialog(parent,
                message == null || message.isBlank() ? "An unexpected error occurred." : message,
                "VaahanSetu", JOptionPane.ERROR_MESSAGE);
    }

    static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "VaahanSetu", JOptionPane.INFORMATION_MESSAGE);
    }

    static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, "Confirm action",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    static ServiceException serviceError(String message) {
        return new ServiceException(message);
    }
}
