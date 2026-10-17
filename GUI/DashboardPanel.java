package GUI;

import LIB.Inventory;
import LIB.UserAccount;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

class DashboardPanel extends RefreshablePanel {
    private final UserAccount user;
    private final JLabel itemTypesValue;
    private final JLabel totalUnitsValue;
    private final JLabel lowStockValue;
    private final DefaultListModel<String> lowStockModel = new DefaultListModel<>();
    private final JLabel statusLabel = new JLabel(" ");

    DashboardPanel(UserAccount user, Consumer<String> navigator) {
        this.user = user;
        setLayout(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        JPanel top = new JPanel(new BorderLayout());
        JLabel heading = new JLabel("Dashboard");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 22f));
        top.add(heading, BorderLayout.NORTH);
        top.add(new JLabel("Workspace: " + DataAccess.workspaceName(user)), BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 16));
        JPanel cardsRow = new JPanel(new GridLayout(1, 3, 16, 0));
        itemTypesValue = addCard(cardsRow, "Item types");
        totalUnitsValue = addCard(cardsRow, "Total units in stock");
        lowStockValue = addCard(cardsRow, "Low-stock items");
        center.add(cardsRow, BorderLayout.NORTH);

        JPanel lowPanel = new JPanel(new BorderLayout(0, 6));
        lowPanel.add(new JLabel("Items that need replenishing:"), BorderLayout.NORTH);
        lowPanel.add(new JScrollPane(new JList<>(lowStockModel)), BorderLayout.CENTER);
        center.add(lowPanel, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        JButton viewInventory = new JButton("View inventory");
        viewInventory.addActionListener(event -> navigator.accept("Inventory"));
        JPanel buttons = new JPanel();
        buttons.add(viewInventory);
        bottom.add(buttons, BorderLayout.WEST);
        statusLabel.setForeground(new Color(0xB00020));
        bottom.add(statusLabel, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private JLabel addCard(JPanel row, String caption) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xC9D6E8)),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        JLabel value = new JLabel("0");
        value.setFont(value.getFont().deriveFont(Font.BOLD, 28f));
        card.add(value, BorderLayout.CENTER);
        card.add(new JLabel(caption), BorderLayout.SOUTH);
        row.add(card);
        return value;
    }

    @Override
    void refresh() {
        try {
            List<Inventory> items = DataAccess.inventoryFor(user);
            int units = 0;
            int low = 0;
            lowStockModel.clear();
            for (Inventory item : items) {
                units += item.getQuantity();
                if (item.isLowStock()) {
                    low++;
                    lowStockModel.addElement(item.getItemName() + " - " + item.getQuantity() + " "
                            + item.getUnit() + " left (threshold " + item.getLowStockThreshold() + ")");
                }
            }
            itemTypesValue.setText(String.valueOf(items.size()));
            totalUnitsValue.setText(String.valueOf(units));
            lowStockValue.setText(String.valueOf(low));
            statusLabel.setText(" ");
        } catch (SQLException exception) {
            statusLabel.setText("Could not load data: " + exception.getMessage());
        }
    }
}