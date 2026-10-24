package GUI;

import GUI.Theme.Tone;
import LIB.AdminAccount;
import LIB.Barangay;
import LIB.BarangayRepository;
import LIB.DatabaseConnection;
import LIB.DemoData;
import LIB.SchemaUpdater;
import LIB.UserRepository;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.sql.Connection;
import java.util.UUID;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.LineBorder;

/** Starts BarangayCare: connect to the database, prepare it, then show the sign-in screen. */
public final class App {
    private enum State { NEEDS_TABLES, NEEDS_SETUP, READY }

    private static JFrame splash;

    private App() {
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        SwingUtilities.invokeLater(App::start);
    }

    static void start() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // the default look and feel is fine for the dialogs
        }
        Theme.init();
        UIManager.put("ToolTip.font", Theme.regular(12));
        UIManager.put("ScrollBar.width", 11);
        connect(null);
    }

    private static void connect(String problem) {
        if (problem != null || !DatabaseConnection.isConfigured()) {
            closeSplash();
            if (!ConnectionDialog.show(problem)) {
                System.exit(0);
            }
        }
        showSplash("Connecting to the database…");
        Task.run(App::prepare, App::next, error -> {
            closeSplash();
            connect(Task.message(error));
        });
    }

    private static State prepare() throws Exception {
        try (Connection connection = DatabaseConnection.getConnection()) {
            if (!SchemaUpdater.isInitialized(connection)) {
                return State.NEEDS_TABLES;
            }
        }
        SchemaUpdater.ensureUpToDate();
        boolean empty = new UserRepository().isEmpty() || new BarangayRepository().findAll().isEmpty();
        return empty ? State.NEEDS_SETUP : State.READY;
    }

    private static void next(State state) {
        if (state == State.NEEDS_TABLES) {
            closeSplash();
            if (!Dialogs.confirm(null, "Create the BarangayCare tables?",
                    "This database has no BarangayCare tables yet. Create them now from database/schema.sql?",
                    "Create tables", false)) {
                System.exit(0);
            }
            showSplash("Creating tables…");
            Task.run(() -> {
                try (Connection connection = DatabaseConnection.getConnection()) {
                    SchemaUpdater.createBaseSchema(connection);
                }
                SchemaUpdater.ensureUpToDate();
                return State.NEEDS_SETUP;
            }, App::next, error -> {
                closeSplash();
                connect("The tables could not be created: " + Task.message(error));
            });
            return;
        }
        closeSplash();
        if (state == State.NEEDS_SETUP) {
            FirstRunDialog.show();
        }
        new LoginFrame().setVisible(true);
    }

    private static void showSplash(String message) {
        closeSplash();
        splash = new JFrame();
        splash.setUndecorated(true);
        splash.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(new LineBorder(Theme.BORDER));
        VBox box = new VBox(10).pad(40, 44, 40, 44);
        box.put(UI.label("BarangayCare", Theme.bold(26), Theme.TEAL), UI.muted("Relief goods management system"),
                UI.gap(10), UI.label(message, Theme.regular(13), Theme.BODY));
        panel.add(box);
        splash.setContentPane(panel);
        splash.setSize(new Dimension(440, 220));
        splash.setLocationRelativeTo(null);
        splash.setVisible(true);
    }

    private static void closeSplash() {
        if (splash != null) {
            splash.dispose();
            splash = null;
        }
    }

    /** Headless-safe check used by the build scripts. */
    static boolean hasDisplay() {
        return !GraphicsEnvironment.isHeadless();
    }
}

/** Asks for the database connection settings and saves them to db.properties. */
final class ConnectionDialog {
    private ConnectionDialog() {
    }

    static boolean show(String problem) {
        DatabaseConnection.Settings current = DatabaseConnection.settings();
        Input url = new Input(current.url);
        Input user = new Input(current.user);
        javax.swing.JPasswordField secret = Shell.password();
        secret.setText(current.password);
        VBox form = new VBox(12);
        if (problem != null) {
            form.put(UI.alert(Tone.DANGER, "Could not connect", problem));
        }
        form.put(new Field("JDBC URL", url, "Example: jdbc:mysql://host:3306/disaster_relief_inventory?ssl-mode=REQUIRED"),
                new Field("Database user", user), new Field("Database password", secret));
        String[] failure = new String[1];
        boolean saved = Dialogs.form(null, "Database connection", "Enter the MySQL connection used by BarangayCare. "
                + "It is saved in db.properties next to the app (keep that file out of git).", form,
                "Test and save", () -> {
                    try {
                        DatabaseConnection.test(url.getText(), user.getText(), new String(secret.getPassword()));
                        DatabaseConnection.saveSettings(url.getText(), user.getText(), new String(secret.getPassword()));
                        return null;
                    } catch (Exception error) {
                        failure[0] = Task.message(error);
                        return failure[0];
                    }
                }, 620);
        return saved;
    }
}

/** First start on an empty database: create the barangay workspace and its first Admin. */
final class FirstRunDialog {
    private FirstRunDialog() {
    }

    static void show() {
        Input barangay = new Input("");
        Input municipality = new Input("");
        Input province = new Input("");
        Input name = new Input("");
        Input email = new Input("");
        javax.swing.JPasswordField password = Shell.password();
        javax.swing.JPasswordField again = Shell.password();
        Check demo = new Check("Also load illustrative training data (fictional records, for practice only)");
        VBox form = new VBox(12);
        form.put(new Cols(12, 1, 1).put(new Field("Barangay name *", barangay), new Field("Municipality *", municipality)),
                new Field("Province *", province), UI.rule(),
                new Cols(12, 1, 1).put(new Field("Admin full name *", name), new Field("Admin email *", email)),
                new Cols(12, 1, 1).put(new Field("Password * (8+ characters)", password),
                        new Field("Repeat password *", again)), demo);
        boolean[] created = new boolean[1];
        boolean accepted;
        do {
            accepted = Dialogs.form(null, "Welcome to BarangayCare", "This database is empty. Create the barangay "
                    + "workspace and the first Admin account. You can add more users afterwards.", form,
                    "Create workspace", () -> {
                        if (barangay.getText().trim().isEmpty() || municipality.getText().trim().isEmpty()
                                || province.getText().trim().isEmpty() || name.getText().trim().isEmpty()
                                || email.getText().trim().isEmpty()) {
                            return "Fill in every field marked with *.";
                        }
                        if (!email.getText().contains("@")) {
                            return "Enter a valid email address for the Admin.";
                        }
                        if (new String(password.getPassword()).length() < 8) {
                            return "Use at least 8 characters for the password.";
                        }
                        if (!new String(password.getPassword()).equals(new String(again.getPassword()))) {
                            return "The two passwords do not match.";
                        }
                        return null;
                    }, 700);
            if (!accepted) {
                System.exit(0);
            }
            String problem = create(barangay.getText(), municipality.getText(), province.getText(), name.getText(),
                    email.getText(), new String(password.getPassword()), demo.isSelected());
            created[0] = problem == null;
            if (problem != null) {
                Dialogs.message(null, "Setup did not finish", problem, Tone.DANGER);
            }
        } while (!created[0]);
    }

    private static String create(String barangayName, String municipality, String province, String adminName,
            String email, String password, boolean withDemo) {
        try {
            AdminAccount admin = new AdminAccount(UUID.randomUUID().toString(), adminName.trim(), email.trim(),
                    email.trim(), password);
            UserRepository users = new UserRepository();
            Barangay workspace;
            java.util.List<Barangay> existing = new BarangayRepository().findAll();
            workspace = existing.isEmpty()
                    ? new BarangayRepository().create(barangayName.trim(), municipality.trim(), province.trim(), null)
                    : existing.get(0);
            if (users.isEmpty()) {
                users.create(admin, null);
            }
            if (withDemo) {
                DemoData.seed(workspace, admin, message -> { });
            }
            return null;
        } catch (Exception error) {
            return Task.message(error);
        }
    }
}
