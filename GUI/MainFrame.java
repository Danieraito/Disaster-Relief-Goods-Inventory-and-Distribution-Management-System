package GUI;

import LIB.UserAccount;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

/** Main window: navigation bar on the left, the selected screen on the right. */
public class MainFrame extends JFrame {
    private static final Color SIDEBAR = new Color(0x1F3A5F);
    private static final Color SELECTED = new Color(0x2E6DA4);

    private final UserAccount user;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, RefreshablePanel> screens = new LinkedHashMap<>();
    private final Map<String, JButton> navButtons = new LinkedHashMap<>();

    public MainFrame(UserAccount user) {
        super("BarangayCare - " + DataAccess.workspaceName(user));
        this.user = user;
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // Screens. Only Dashboard and Inventory are built so far.
        screens.put("Dashboard", new DashboardPanel(user, this::show));
        screens.put("Inventory", new InventoryPanel(user));
        screens.put("Disasters", new PlaceholderPanel("Disasters"));
        screens.put("Beneficiaries", new PlaceholderPanel("Beneficiaries"));
        screens.put("Donations", new PlaceholderPanel("Donations"));
        screens.put("Distribution", new PlaceholderPanel("Distribution"));
        screens.put("Reports", new PlaceholderPanel("Reports"));
        screens.put("Audit Logs", new PlaceholderPanel("Audit Logs"));
        if (user.canManageUsers()) {
            screens.put("User Administration", new PlaceholderPanel("User Administration"));
        }
        for (Map.Entry<String, RefreshablePanel> entry : screens.entrySet()) {
            content.add(entry.getValue(), entry.getKey());
        }

        setLayout(new BorderLayout());
        add(buildSidebar(), BorderLayout.WEST);
        add(content, BorderLayout.CENTER);

        setSize(1100, 680);
        setLocationRelativeTo(null);
        show("Dashboard");
    }

    private JScrollPane buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(SIDEBAR);
        sidebar.setBorder(BorderFactory.createEmptyBorder(16, 12, 16, 12));

        JLabel brand = new JLabel("BarangayCare");
        brand.setForeground(Color.WHITE);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 20f));
        sidebar.add(brand);

        JLabel who = new JLabel("<html>" + user.getName() + "<br>" + DataAccess.roleLabel(user) + "</html>");
        who.setForeground(new Color(0xC9D6E8));
        sidebar.add(Box.createRigidArea(new Dimension(0, 6)));
        sidebar.add(who);
        sidebar.add(Box.createRigidArea(new Dimension(0, 20)));

        for (String name : screens.keySet()) {
            JButton button = new JButton(name);
            button.setAlignmentX(LEFT_ALIGNMENT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            button.setHorizontalAlignment(JButton.LEFT);
            button.setForeground(Color.WHITE);
            button.setBackground(SIDEBAR);
            button.setOpaque(true);
            button.setBorderPainted(false);
            button.setFocusPainted(false);
            button.addActionListener(e -> show(name));
            navButtons.put(name, button);
            sidebar.add(button);
            sidebar.add(Box.createRigidArea(new Dimension(0, 4)));
        }

        sidebar.add(Box.createVerticalGlue());
        JButton logout = new JButton("Log out");
        logout.setAlignmentX(LEFT_ALIGNMENT);
        logout.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        logout.addActionListener(e -> {
            dispose();
            new LoginFrame().setVisible(true);
        });
        sidebar.add(logout);

        sidebar.setPreferredSize(new Dimension(220, sidebar.getPreferredSize().height));
        JScrollPane scrollPane = new JScrollPane(sidebar);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setPreferredSize(new Dimension(220, 0));
        return scrollPane;
    }

    /** Shows one screen by name and reloads its data. */
    void show(String name) {
        RefreshablePanel screen = screens.get(name);
        if (screen == null) {
            return;
        }
        screen.refresh();
        cards.show(content, name);
        for (Map.Entry<String, JButton> entry : navButtons.entrySet()) {
            entry.getValue().setBackground(entry.getKey().equals(name) ? SELECTED : SIDEBAR);
        }
    }
}
