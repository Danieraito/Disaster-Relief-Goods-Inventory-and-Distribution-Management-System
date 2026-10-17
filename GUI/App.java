package GUI;

import LIB.DatabaseConnection;
import java.sql.Connection;
import java.sql.SQLException;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class App {
    private App() {
    }

    public static void main(String[] args) {
        try (Connection ignored = DatabaseConnection.getConnection()) {
        } catch (SQLException exception) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "Could not connect to the database. Check DB_URL, DB_USER, DB_PASSWORD, "
                            + "and confirm database/schema.sql has been applied.\n\n"
                            + exception.getMessage(),
                    "BarangayCare", JOptionPane.ERROR_MESSAGE));
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new LoginFrame().setVisible(true);
        });
    }
}