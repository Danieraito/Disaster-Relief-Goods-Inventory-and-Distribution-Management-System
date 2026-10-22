package GUI;

import GUI.Theme.Tone;
import LIB.Barangay;
import LIB.BarangayRepository;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/** The main window: brand bar, navigation tabs and the page area. */
final class Shell extends JFrame {
    private final PageHost host = new PageHost();
    private final JScrollPane scroll = new JScrollPane(host);
    private final NavTabs nav;
    private Page current;
    private int generation;

    Shell() {
        super("BarangayCare · Relief Goods Management System");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        nav = new NavTabs();
        root.add(topBar(), BorderLayout.NORTH);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(28);
        scroll.getViewport().setBackground(Theme.BG);
        root.add(scroll, BorderLayout.CENTER);
        setContentPane(root);
        setSize(1380, 880);
        setMinimumSize(new Dimension(1100, 700));
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------ top bar

    private JComponent topBar() {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Theme.BORDER);
                g.fillRect(0, getHeight() - 1, getWidth(), 1);
            }
        };
        bar.setBackground(Color.WHITE);
        JLabel brand = UI.label("BarangayCare", Theme.bold(21), Theme.TEAL);
        Row account = new Row(18);
        account.right();
        if (Session.isAdmin()) {
            account.put(workspaceMenu());
        }
        account.put(UI.label(Session.user.getName() + " · " + Session.user.getRoleLabel(), Theme.regular(12), Theme.MUTED));
        account.put(UI.link("Change password", this::changePassword));
        account.put(UI.link("Sign out", this::signOut));
        Cols first = new Cols(20, 0, 1);
        first.pad(14, 32, 4, 32);
        first.put(brand, account);
        bar.add(first, BorderLayout.NORTH);
        JPanel tabs = new JPanel(new BorderLayout());
        tabs.setOpaque(false);
        tabs.setBorder(new EmptyBorder(0, 32, 0, 32));
        tabs.add(nav, BorderLayout.CENTER);
        bar.add(tabs, BorderLayout.CENTER);
        return bar;
    }

    private Component workspaceMenu() {
        Btn button = UI.link(workspaceLabel() + "  ▾");
        button.addActionListener(event -> Task.run(() -> new BarangayRepository().findAll(), barangays -> {
            JPopupMenu menu = new JPopupMenu();
            menu.setBorder(new LineBorder(Theme.BORDER));
            for (Barangay barangay : barangays) {
                javax.swing.JMenuItem item = new javax.swing.JMenuItem(barangay.getName() + " · "
                        + barangay.getMunicipality());
                item.setFont(Theme.regular(13));
                item.addActionListener(pick -> {
                    Session.workspace = barangay;
                    Session.disasterId = null;
                    button.setText(workspaceLabel() + "  ▾");
                    open(Navigation.root("Dashboard"));
                });
                menu.add(item);
            }
            menu.show(button, 0, button.getHeight());
        }, error -> Dialogs.error(this, error)));
        return button;
    }

    static String workspaceLabel() {
        return Session.workspace == null ? "No workspace"
                : "Barangay " + Session.workspace.getName() + " · " + Session.workspace.getMunicipality();
    }

    private void changePassword() {
        Input current = new Input("");
        javax.swing.JPasswordField old = password();
        javax.swing.JPasswordField fresh = password();
        javax.swing.JPasswordField again = password();
        VBox form = new VBox(12);
        form.put(new Field("Current password *", old), new Field("New password * (8 characters or more)", fresh),
                new Field("Repeat new password *", again));
        boolean saved = Dialogs.form(this, "Change password", "Choose a password only you know.", form, "Save password", () -> {
            if (new String(fresh.getPassword()).length() < 8) {
                return "Use at least 8 characters for the new password.";
            }
            if (!new String(fresh.getPassword()).equals(new String(again.getPassword()))) {
                return "The two new passwords do not match.";
            }
            try {
                new LIB.UserRepository().changeOwnPassword(Session.user, new String(old.getPassword()),
                        new String(fresh.getPassword()));
                return null;
            } catch (Exception error) {
                return Task.message(error);
            }
        }, 520);
        if (saved) {
            Dialogs.message(this, "Password changed", "Your new password is active the next time you sign in.", Tone.TEAL);
        }
    }

    static javax.swing.JPasswordField password() {
        javax.swing.JPasswordField field = new javax.swing.JPasswordField();
        field.setBorder(null);
        field.setOpaque(false);
        field.setFont(Theme.regular(13));
        field.setForeground(Theme.TEXT);
        field.setCaretColor(Theme.TEAL);
        field.setPreferredSize(new Dimension(100, 22));
        return field;
    }

    private void signOut() {
        if (Dialogs.confirm(this, "Sign out", "Sign out of BarangayCare on this computer?", "Sign out", false)) {
            Session.clear();
            dispose();
            new LoginFrame().setVisible(true);
        }
    }

    // ------------------------------------------------------------------ pages

    /** Opens a page: shows "Loading…", runs load() in the background, then draws build(). */
    void open(Page page) {
        page.shell = this;
        current = page;
        int ticket = ++generation;
        nav.select(page.module());
        display(page, loading());
        Task.run(() -> {
            page.load();
            return Boolean.TRUE;
        }, done -> {
            if (ticket == generation) {
                try {
                    display(page, page.build());
                } catch (RuntimeException error) {
                    error.printStackTrace();
                    display(page, failure(page, error));
                }
            }
        }, error -> {
            if (ticket == generation) {
                if (!(error instanceof IllegalArgumentException) && !(error instanceof IllegalStateException)
                        && !(error instanceof SecurityException)) {
                    error.printStackTrace();
                }
                display(page, failure(page, error));
            }
        });
    }

    void display(Page page, JComponent content) {
        VBox wrapper = new VBox(18);
        wrapper.put(UI.small("Workspace / " + page.crumb() + " · " + workspaceLabel()), content,
                UI.gap(6), UI.small("Restricted barangay records · Handle household information with care"));
        host.setContent(wrapper);
        scroll.getVerticalScrollBar().setValue(0);
    }

    private JComponent loading() {
        VBox box = new VBox(0).pad(120, 0, 0, 0);
        JLabel label = UI.label("Loading…", Theme.regular(14), Theme.MUTED);
        label.setHorizontalAlignment(JLabel.CENTER);
        box.put(label);
        return box;
    }

    private JComponent failure(Page page, Throwable error) {
        VBox box = new VBox(14);
        box.put(UI.alert(Tone.DANGER, "This screen could not be loaded", Task.message(error),
                UI.secondary("Try again", page::reload)));
        return box;
    }

    Page currentPage() {
        return current;
    }

    // ------------------------------------------------------------------ navigation tabs

    private final class NavTabs extends JComponent {
        private final List<String> modules = new ArrayList<>();
        private final int[] starts = new int[12];
        private final int[] widths = new int[12];
        private String selected = "Dashboard";

        NavTabs() {
            for (String module : new String[] { "Dashboard", "Inventory", "Disasters", "Beneficiaries", "Donations",
                "Distribution", "Reports", "Audit Logs" }) {
                modules.add(module);
            }
            if (Session.isAdmin()) {
                modules.add("User administration");
            }
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent event) {
                    for (int index = 0; index < modules.size(); index++) {
                        if (event.getX() >= starts[index] - 10 && event.getX() <= starts[index] + widths[index] + 10) {
                            Shell.this.open(Navigation.root(modules.get(index)));
                            return;
                        }
                    }
                }
            });
        }

        void select(String module) {
            selected = module;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(900, 42);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.g2(g);
            int x = 0;
            for (int index = 0; index < modules.size(); index++) {
                String module = modules.get(index);
                boolean active = module.equals(selected);
                g2.setFont(active ? Theme.semibold(13) : Theme.regular(13));
                FontMetrics fm = g2.getFontMetrics();
                starts[index] = x;
                widths[index] = fm.stringWidth(module);
                g2.setColor(active ? Theme.TEAL : Theme.MUTED);
                g2.drawString(module, x, (getHeight() - 3 - fm.getHeight()) / 2 + fm.getAscent() + 2);
                if (active) {
                    g2.setColor(Theme.TEAL);
                    g2.fillRect(x, getHeight() - 3, widths[index], 3);
                }
                x += widths[index] + 30;
            }
            g2.dispose();
        }
    }
}

/** Maps a navigation tab name to the page that opens first. */
final class Navigation {
    private Navigation() {
    }

    static Page root(String module) {
        switch (module) {
            case "Inventory":
                return new InventoryPage();
            case "Disasters":
                return new DisastersPage();
            case "Beneficiaries":
                return new BeneficiariesPage();
            case "Donations":
                return new DonationsPage();
            case "Distribution":
                return new DistributionPage();
            case "Reports":
                return new ReportsPage();
            case "Audit Logs":
                return new AuditPage();
            case "User administration":
                return new UsersPage();
            default:
                return new DashboardPage();
        }
    }
}
