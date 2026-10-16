package GUI;

import LIB.AdminAccount;
import LIB.Barangay;
import LIB.BarangayOfficialAccount;
import LIB.Database;
import LIB.UserRepository;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Starts BarangayCare: prepares the database, then shows the login screen. */
public class App {
    public static void main(String[] args) {
        try {
            Database.initialize(true);
            createDemoAccountsIfEmpty();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Could not open the database:\n" + e.getMessage(),
                    "BarangayCare", JOptionPane.ERROR_MESSAGE);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // The default look is fine if the system look is not available.
            }
            new LoginFrame().setVisible(true);
        });
    }

    // Demo logins so the program can be tried right away. Change or remove before real use.
    private static void createDemoAccountsIfEmpty() throws Exception {
        UserRepository users = new UserRepository();
        if (!users.findAll().isEmpty()) {
            return;
        }
        users.save(new AdminAccount("USR-ADMIN", "System Admin", "admin",
                "admin@barangaycare.local", "Admin#2026"));
        Barangay barangay = new LIB.BarangayRepository().findById("BRG-001").orElse(null);
        if (barangay != null) {
            users.save(new BarangayOfficialAccount("USR-OFF1", "Maria Santos", "msantos",
                    "msantos@barangaycare.local", "Official#2026", barangay));
        }
    }
}
