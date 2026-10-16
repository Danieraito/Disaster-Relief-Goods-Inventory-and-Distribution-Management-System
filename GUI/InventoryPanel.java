package GUI;

import LIB.Barangay;
import LIB.Inventory;
import LIB.InventoryBatch;
import LIB.InventoryController;
import LIB.UserAccount;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/** Inventory screen: list items, add an item, and view an item's stock batches. */
class InventoryPanel extends RefreshablePanel {
    private final UserAccount user;
    private final InventoryController controller = new InventoryController();
    private final DefaultTableModel tableModel = new DefaultTableModel(new String[] {
        "Item ID", "Item", "Category", "Quantity", "Unit", "Low-stock at", "Status", "Location", "Barangay"
    }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final JLabel statusLabel = new JLabel(" ");
    private List<Inventory> items = new ArrayList<>();

    InventoryPanel(UserAccount user) {
        this.user = user;
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        JLabel heading = new JLabel("Inventory");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 22f));
        add(heading, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        JButton addButton = new JButton("Add item");
        addButton.setEnabled(user.canEnterRecords());
        addButton.addActionListener(e -> addItem());
        JButton batchesButton = new JButton("View batches");
        batchesButton.addActionListener(e -> showBatches());
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refresh());
        buttons.add(addButton);
        buttons.add(batchesButton);
        buttons.add(refreshButton);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(buttons, BorderLayout.WEST);
        bottom.add(statusLabel, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    @Override
    void refresh() {
        try {
            items = DataAccess.inventoryFor(user);
            tableModel.setRowCount(0);
            for (Inventory item : items) {
                tableModel.addRow(new Object[] {
                    item.getItemId(), item.getItemName(), item.getCategory(), item.getQuantity(),
                    item.getUnit(), item.getLowStockThreshold(),
                    item.isLowStock() ? "LOW STOCK" : "OK",
                    item.getStorageLocation(),
                    item.getBarangay() == null ? "" : item.getBarangay().getName()
                });
            }
            statusLabel.setText(items.size() + " item(s)");
        } catch (SQLException e) {
            statusLabel.setText("Could not load inventory: " + e.getMessage());
        }
    }

    private void showBatches() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select an item first.");
            return;
        }
        Inventory item = items.get(row);
        DefaultTableModel model = new DefaultTableModel(new String[] {
            "Batch ID", "Received", "Expiry", "Received qty", "Remaining qty"
        }, 0);
        for (InventoryBatch batch : item.getBatches()) {
            model.addRow(new Object[] {
                batch.getBatchId(), batch.getReceivedDate(), batch.getExpiryDate(),
                batch.getReceivedQuantity(), batch.getRemainingQuantity()
            });
        }
        JTable batchTable = new JTable(model);
        JScrollPane scroll = new JScrollPane(batchTable);
        scroll.setPreferredSize(new java.awt.Dimension(560, 180));
        JOptionPane.showMessageDialog(this, scroll, "Batches - " + item.getItemName(),
                JOptionPane.PLAIN_MESSAGE);
    }

    private void addItem() {
        try {
            // Admins choose the barangay; other users add to their own workspace.
            Barangay target = user.getBarangay();
            List<Barangay> choices = new ArrayList<>();
            JComboBox<String> barangayBox = null;
            if (user.getRole() == UserAccount.Role.ADMIN) {
                choices = DataAccess.BARANGAYS.findAll();
                if (choices.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "There is no barangay workspace yet.");
                    return;
                }
                String[] names = new String[choices.size()];
                for (int i = 0; i < names.length; i++) {
                    names[i] = choices.get(i).getName();
                }
                barangayBox = new JComboBox<>(names);
            }

            JTextField nameField = new JTextField();
            JTextField categoryField = new JTextField("Food");
            JTextField unitField = new JTextField();
            JTextField thresholdField = new JTextField("0");
            JTextField locationField = new JTextField();
            JTextField quantityField = new JTextField("0");
            JTextField expiryField = new JTextField();

            JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
            if (barangayBox != null) {
                form.add(new JLabel("Barangay"));
                form.add(barangayBox);
            }
            form.add(new JLabel("Item name"));
            form.add(nameField);
            form.add(new JLabel("Category"));
            form.add(categoryField);
            form.add(new JLabel("Unit (e.g. sack, can)"));
            form.add(unitField);
            form.add(new JLabel("Low-stock threshold"));
            form.add(thresholdField);
            form.add(new JLabel("Storage location"));
            form.add(locationField);
            form.add(new JLabel("Opening stock quantity"));
            form.add(quantityField);
            form.add(new JLabel("Opening stock expiry (optional)"));
            form.add(expiryField);

            int result = JOptionPane.showConfirmDialog(this, form, "Add inventory item",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return;
            }
            if (barangayBox != null) {
                target = choices.get(barangayBox.getSelectedIndex());
            }
            if (nameField.getText().trim().isEmpty() || unitField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Item name and unit are required.");
                return;
            }

            int quantity = Integer.parseInt(quantityField.getText().trim());
            int threshold = Integer.parseInt(thresholdField.getText().trim());
            String expiry = expiryField.getText().trim();
            String itemId = "ITM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            Inventory item = controller.createInventoryItem(itemId, nameField.getText().trim(),
                    categoryField.getText().trim(), quantity, unitField.getText().trim(), threshold,
                    locationField.getText().trim(), expiry.isEmpty() ? null : expiry, target, user);
            DataAccess.INVENTORY.save(item);
            refresh();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Quantity and threshold must be whole numbers.");
        } catch (SecurityException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Could not save: " + e.getMessage());
        }
    }
}
