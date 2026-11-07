package GUI;

import GUI.Theme.Tone;
import LIB.Barangay;
import LIB.BarangayRepository;
import LIB.LoginController;
import LIB.UserAccount;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.WindowConstants;

/** The sign-in screen: a teal brand panel on the left and the sign-in form on the right. */
final class LoginFrame extends JFrame {
    private static final Preferences PREFS = Preferences.userNodeForPackage(LoginFrame.class);

    private final Dropdown<Barangay> workspace = new Dropdown<>(new ArrayList<>(), barangay ->
            "Barangay " + barangay.getName() + " · " + barangay.getMunicipality());
    private final Dropdown<UserAccount.Role> role = new Dropdown<>(java.util.Arrays.asList(UserAccount.Role.ADMIN,
            UserAccount.Role.BARANGAY_OFFICIAL, UserAccount.Role.ENCODER), LoginFrame::roleLabel);
    private final Input email = new Input("").placeholder("name@barangay.example");
    private final JPasswordField password = Shell.password();
    private final Check remember = new Check("Remember this device");
    private final VBox errorBox = new VBox(0);
    private final Btn signIn = UI.primary("Sign in to workspace");

    LoginFrame() {
        super("BarangayCare · Sign in");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JPanel root = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.fill = java.awt.GridBagConstraints.BOTH;
        c.weighty = 1;
        c.gridy = 0;
        c.gridx = 0;
        c.weightx = 0.45;
        root.add(hero(), c);
        c.gridx = 1;
        c.weightx = 0.55;
        root.add(formSide(), c);
        setContentPane(root);
        setSize(1280, 800);
        setMinimumSize(new Dimension(1000, 700));
        setLocationRelativeTo(null);
        role.setSelected(UserAccount.Role.BARANGAY_OFFICIAL);
        String lastRole = PREFS.get("role", null);
        if (lastRole != null) {
            try {
                role.setSelected(UserAccount.Role.valueOf(lastRole));
            } catch (IllegalArgumentException ignored) {
                // keep the default role
            }
        }
        email.setText(PREFS.get("email", ""));
        remember.setSelected(!email.getText().isEmpty());
        getRootPane().setDefaultButton(signIn);
        signIn.addActionListener(event -> attempt());
        Task.run(() -> new BarangayRepository().findAll(), barangays -> {
            workspace.setOptions(barangays);
            String lastWorkspace = PREFS.get("workspace", null);
            Barangay pick = barangays.isEmpty() ? null : barangays.get(0);
            for (Barangay barangay : barangays) {
                if (barangay.getBarangayId().equals(lastWorkspace)) {
                    pick = barangay;
                }
            }
            workspace.setSelected(pick);
        }, error -> showError("The workspaces could not be loaded", Task.message(error)));
    }

    static String roleLabel(UserAccount.Role role) {
        switch (role) {
            case ADMIN:
                return "Admin";
            case BARANGAY_OFFICIAL:
                return "Barangay Official";
            default:
                return "Encoder";
        }
    }

    // ------------------------------------------------------------------ left panel

    private JPanel hero() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.TEAL_DARK);
        VBox top = new VBox(0).pad(64, 68, 0, 60);
        top.put(UI.label("BarangayCare", Theme.bold(22), Color.WHITE), UI.gap(18),
                UI.label("RELIEF GOODS MANAGEMENT SYSTEM", Theme.regular(10.5f), new Color(0xC9E3E0)), UI.gap(58),
                new Wrap("Every resource accounted for. Every family in view.", Theme.semibold(56), Color.WHITE).maxWidth(540),
                UI.gap(26),
                new Wrap("A shared workspace for Barangay officials to receive, track and release relief goods with confidence.",
                        Theme.regular(14.5f), new Color(0xDCEDEB)).maxWidth(520),
                UI.gap(36), points(), UI.gap(30),
                UI.label("Authorized personnel only · All activity is recorded", Theme.regular(11.5f), new Color(0xB9D8D4)));
        panel.add(top, BorderLayout.NORTH);
        return panel;
    }

    private Component points() {
        VBox box = new VBox(20) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Gfx.g2(g);
                Gfx.roundBox(g2, getWidth(), getHeight(), 18, new Color(255, 255, 255, 26), null);
                g2.dispose();
            }
        };
        box.pad(30, 34, 32, 34);
        String[][] rows = { { "Receive donations", "Stock increases with a recorded receipt." },
            { "Understand household needs", "Connect families to an active disaster." },
            { "Release with accountability", "Verify assistance, deduct stock and retain proof." } };
        for (String[] row : rows) {
            VBox item = new VBox(5);
            item.put(UI.label(row[0], Theme.semibold(13.5f), Color.WHITE),
                    UI.label(row[1], Theme.regular(12.5f), new Color(0xD3E8E5)));
            box.put(item);
        }
        return box;
    }

    // ------------------------------------------------------------------ right panel

    private Component formSide() {
        VBox form = new VBox(18);
        form.put(UI.left(UI.pill("Authorized barangay personnel", Tone.NEUTRAL)),
                UI.label("Welcome back", Theme.semibold(30), Theme.TEXT),
                UI.muted("Sign in with your assigned role to continue."), UI.gap(4),
                new Field("Workspace", workspace), new Field("Role", role, "Your account must have the selected role assigned."),
                new Field("Email address", email), new Field("Password", password));
        form.put(new Cols(12, 1, 0).put(remember, UI.link("Forgot password?", this::forgot)));
        form.put(UI.left(signIn), errorBox, UI.gap(4), UI.strong("Access by role"),
                new DataTable(new String[] { "Role", "Workspace access" }, 1, 1.5)
                        .row("Admin", "All modules, accounts & roles")
                        .row("Barangay Official", "Manage records, confirm releases & export reports")
                        .row("Encoder", "Enter records and prepare drafts; no release or deletion"));
        JPanel side = new JPanel(null) {
            @Override
            public void doLayout() {
                int width = Math.min(640, Math.max(360, getWidth() - 140));
                int height = Lay.h(form, width);
                form.setBounds(Math.max(40, (getWidth() - width) / 2), 56, width, height);
            }

            @Override
            public Dimension getPreferredSize() {
                int width = Math.min(640, Math.max(360, getParent() == null ? 700 : getParent().getWidth() - 140));
                return new Dimension(width + 140, Lay.h(form, width) + 110);
            }
        };
        side.setBackground(Color.WHITE);
        side.add(form);
        JScrollPane scroll = new JScrollPane(side);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getViewport().setBackground(Color.WHITE);
        return scroll;
    }

    // ------------------------------------------------------------------ actions

    private void forgot() {
        Dialogs.message(this, "Forgot your password?", "Ask an Admin to reset it. Admins can issue a temporary "
                + "password from User administration, and you can change it after you sign in.", Tone.INFO);
    }

    private void showError(String title, String message) {
        errorBox.removeAll();
        errorBox.put(UI.alert(Tone.DANGER, title, message));
        errorBox.revalidate();
        errorBox.repaint();
        revalidate();
    }

    private void attempt() {
        String login = email.getText().trim();
        String secret = new String(password.getPassword());
        Barangay chosen = workspace.getSelected();
        UserAccount.Role chosenRole = role.getSelected();
        if (chosen == null) {
            showError("Choose a workspace", "Select the barangay workspace you work in.");
            return;
        }
        if (login.isEmpty() || secret.isEmpty()) {
            showError("Role or password not recognized", "Enter your email address and password.");
            return;
        }
        signIn.setEnabled(false);
        errorBox.removeAll();
        Task.run(() -> new LoginController().signIn(login, secret, chosenRole, chosen), (Optional<UserAccount> result) -> {
            signIn.setEnabled(true);
            if (!result.isPresent()) {
                showError("Role or password not recognized",
                        "Check your credentials and assigned role. Contact your Admin if access is unavailable.");
                return;
            }
            if (remember.isSelected()) {
                PREFS.put("email", login);
                PREFS.put("role", chosenRole.name());
                PREFS.put("workspace", chosen.getBarangayId());
            } else {
                PREFS.remove("email");
            }
            Session.user = result.get();
            Session.workspace = chosen;
            Shell shell = new Shell();
            shell.setVisible(true);
            shell.open(new DashboardPage());
            dispose();
        }, error -> {
            signIn.setEnabled(true);
            showError("Sign-in is not available", Task.message(error));
        });
    }
}
