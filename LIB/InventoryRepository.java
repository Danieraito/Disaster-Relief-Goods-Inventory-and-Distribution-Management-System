package LIB;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class InventoryRepository {
    public void save(Inventory item) throws SQLException {
        if (item == null || item.getBarangay() == null) {
            throw new IllegalArgumentException("Inventory must belong to a barangay before it can be saved.");
        }
        String openingBatchId = "OPENING-" + item.getItemId();
        for (InventoryBatch batch : item.getBatches()) {
            if (!batch.getBatchId().equals(openingBatchId)) {
                throw new IllegalArgumentException(
                        "Only opening stock can be saved here; donation batches must be saved with their donation.");
            }
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BarangayRepository.save(connection, item.getBarangay());
                saveItem(connection, item);
                for (InventoryBatch batch : item.getBatches()) {
                    saveOpeningBatch(connection, item, batch);
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public List<Inventory> findByBarangay(String barangayId) throws SQLException {
        List<Inventory> items = new ArrayList<>();
        String sql = "SELECT inventory_item_id, barangay_id, item_name, category, unit, "
                + "low_stock_threshold, storage_location FROM inventory_items "
                + "WHERE barangay_id = ? ORDER BY item_name";
        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, barangayId);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    Barangay barangay = BarangayRepository.findById(connection, results.getString("barangay_id"));
                    items.add(new Inventory(results.getString("inventory_item_id"),
                            results.getString("item_name"), results.getString("category"), 0,
                            results.getString("unit"), results.getInt("low_stock_threshold"),
                            results.getString("storage_location"), null, barangay));
                }
            }
            for (Inventory item : items) {
                loadBatches(connection, item);
            }
        }
        return items;
    }

    private static void saveItem(Connection connection, Inventory item) throws SQLException {
        String sql = "INSERT INTO inventory_items (inventory_item_id, barangay_id, item_name, category, "
                + "unit, low_stock_threshold, storage_location) VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), category = VALUES(category), "
                + "unit = VALUES(unit), low_stock_threshold = VALUES(low_stock_threshold), "
                + "storage_location = VALUES(storage_location)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, item.getItemId());
            statement.setString(2, item.getBarangay().getBarangayId());
            statement.setString(3, item.getItemName());
            statement.setString(4, item.getCategory());
            statement.setString(5, item.getUnit());
            statement.setInt(6, item.getLowStockThreshold());
            statement.setString(7, item.getStorageLocation());
            statement.executeUpdate();
        }
    }

    private static void saveOpeningBatch(Connection connection, Inventory item, InventoryBatch batch)
            throws SQLException {
        String sql = "INSERT INTO inventory_batches (batch_id, inventory_item_id, batch_type, donation_id, "
                + "donation_line_number, received_date, expiry_date, received_quantity, remaining_quantity) "
                + "VALUES (?, ?, 'OPENING', NULL, NULL, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE batch_id = batch_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, batch.getBatchId());
            statement.setString(2, item.getItemId());
            statement.setDate(3, parseDateOrToday(batch.getReceivedDate()));
            setNullableDate(statement, 4, batch.getExpiryDate());
            statement.setInt(5, batch.getReceivedQuantity());
            statement.setInt(6, batch.getRemainingQuantity());
            statement.executeUpdate();
        }
    }

    private static void loadBatches(Connection connection, Inventory item) throws SQLException {
        String sql = "SELECT batch_id, received_date, expiry_date, received_quantity, remaining_quantity "
                + "FROM inventory_batches WHERE inventory_item_id = ? ORDER BY received_date, batch_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, item.getItemId());
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    Date expiry = results.getDate("expiry_date");
                    int received = results.getInt("received_quantity");
                    int remaining = results.getInt("remaining_quantity");
                    String batchId = results.getString("batch_id");
                    item.addBatch(batchId, received, results.getDate("received_date").toString(),
                            expiry == null ? null : expiry.toString());
                    if (remaining < received) {
                        item.removeBatchStock(batchId, received - remaining);
                    }
                }
            }
        }
    }

    private static Date parseDateOrToday(String date) {
        return date == null || date.trim().isEmpty()
                ? Date.valueOf(LocalDate.now())
                : Date.valueOf(LocalDate.parse(date));
    }

    private static void setNullableDate(PreparedStatement statement, int parameter, String date)
            throws SQLException {
        if (date == null || date.trim().isEmpty()) {
            statement.setNull(parameter, Types.DATE);
        } else {
            statement.setDate(parameter, Date.valueOf(LocalDate.parse(date)));
        }
    }
}