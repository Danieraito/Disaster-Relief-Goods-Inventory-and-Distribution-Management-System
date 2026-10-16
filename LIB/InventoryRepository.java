package LIB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Saves and loads inventory items together with their stock batches. */
public class InventoryRepository {

    /** Saves the item and all of its batches in one all-or-nothing database transaction. */
    public void save(Inventory item) throws SQLException {
        if (item.getBarangay() == null) {
            throw new IllegalArgumentException("Inventory must belong to a barangay before it can be saved.");
        }
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BarangayRepository.save(connection, item.getBarangay());
                saveItem(connection, item);
                for (InventoryBatch batch : item.getBatches()) {
                    saveBatch(connection, item, batch);
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public Optional<Inventory> findById(String itemId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT + " WHERE item_id = ?")) {
            statement.setString(1, itemId);
            List<Inventory> items = readItems(connection, statement);
            return items.isEmpty() ? Optional.empty() : Optional.of(items.get(0));
        }
    }

    /** All items of one barangay workspace. */
    public List<Inventory> findByBarangay(String barangayId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        SELECT + " WHERE barangay_id = ? ORDER BY item_name")) {
            statement.setString(1, barangayId);
            return readItems(connection, statement);
        }
    }

    private static final String SELECT = "SELECT item_id, barangay_id, item_name, category, unit, "
            + "low_stock_threshold, storage_location FROM inventory_items";

    private static void saveItem(Connection connection, Inventory item) throws SQLException {
        String sql = "INSERT INTO inventory_items (item_id, barangay_id, item_name, category, unit, "
                + "low_stock_threshold, storage_location) VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(item_id) DO UPDATE SET item_name = excluded.item_name, "
                + "category = excluded.category, unit = excluded.unit, "
                + "low_stock_threshold = excluded.low_stock_threshold, "
                + "storage_location = excluded.storage_location";
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

    private static void saveBatch(Connection connection, Inventory item, InventoryBatch batch)
            throws SQLException {
        String sql = "INSERT INTO inventory_batches (batch_id, item_id, received_date, expiry_date, "
                + "received_quantity, remaining_quantity) VALUES (?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(batch_id) DO UPDATE SET expiry_date = excluded.expiry_date, "
                + "remaining_quantity = excluded.remaining_quantity";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, batch.getBatchId());
            statement.setString(2, item.getItemId());
            statement.setString(3, batch.getReceivedDate() == null ? "" : batch.getReceivedDate());
            statement.setString(4, batch.getExpiryDate());
            statement.setInt(5, batch.getReceivedQuantity());
            statement.setInt(6, batch.getRemainingQuantity());
            statement.executeUpdate();
        }
    }

    private static List<Inventory> readItems(Connection connection, PreparedStatement statement)
            throws SQLException {
        // Read the item rows first, then load each item's batches.
        List<Inventory> items = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                Barangay barangay = BarangayRepository.findById(connection, rs.getString("barangay_id"));
                items.add(new Inventory(
                        rs.getString("item_id"),
                        rs.getString("item_name"),
                        rs.getString("category"),
                        0,
                        rs.getString("unit"),
                        rs.getInt("low_stock_threshold"),
                        rs.getString("storage_location"),
                        null,
                        barangay));
            }
        }
        String sql = "SELECT batch_id, received_date, expiry_date, received_quantity, remaining_quantity "
                + "FROM inventory_batches WHERE item_id = ? ORDER BY received_date, batch_id";
        for (Inventory item : items) {
            try (PreparedStatement batchStatement = connection.prepareStatement(sql)) {
                batchStatement.setString(1, item.getItemId());
                try (ResultSet rs = batchStatement.executeQuery()) {
                    while (rs.next()) {
                        item.restoreBatch(
                                rs.getString("batch_id"),
                                rs.getString("received_date"),
                                rs.getString("expiry_date"),
                                rs.getInt("received_quantity"),
                                rs.getInt("remaining_quantity"));
                    }
                }
            }
        }
        return items;
    }
}