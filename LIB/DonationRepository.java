package LIB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saves and loads donations with their item lines. Approval itself is done by the Donations
 * model; this class only stores the result (status, receiving officer and new stock batches).
 */
public class DonationRepository {

    /** Saves a new donation (normally PENDING after submit) and its lines. */
    public static void insert(Connection connection, Donations donation, String barangayId, UserAccount recordedBy)
            throws SQLException {
        String sql = "INSERT INTO donations (donation_id, barangay_id, donor_id, donation_date, receiving_location, "
                + "delivery_reference, status, received_by, recorded_by, recorded_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, donation.getDonationId());
            statement.setString(2, barangayId);
            statement.setString(3, donation.getDonor().getDonorId());
            statement.setString(4, donation.getDonationDate());
            statement.setString(5, donation.getReceivingLocation());
            statement.setString(6, donation.getDeliveryReference());
            statement.setString(7, donation.getStatus().name());
            statement.setString(8, donation.getReceivedBy() == null ? null : donation.getReceivedBy().getAccountId());
            statement.setString(9, recordedBy.getAccountId());
            statement.setString(10, Database.now());
            statement.executeUpdate();
        }
        String lineSql = "INSERT INTO donation_items (donation_id, item_id, quantity, batch_reference, expiry_date) "
                + "VALUES (?, ?, ?, ?, ?)";
        for (TransactionItem item : donation.getItems()) {
            try (PreparedStatement statement = connection.prepareStatement(lineSql)) {
                statement.setString(1, donation.getDonationId());
                statement.setString(2, item.getInventory().getItemId());
                statement.setInt(3, item.getQuantity());
                statement.setString(4, item.getBatchReference());
                statement.setString(5, item.getExpiryDate());
                statement.executeUpdate();
            }
        }
    }

    /**
     * Rebuilds a saved donation. Lines for the same item share one Inventory object, because the
     * Donations model compares inventory objects directly.
     */
    public static Donations load(Connection connection, String donationId) throws SQLException {
        String sql = "SELECT donor_id, donation_date, receiving_location, delivery_reference, status, received_by "
                + "FROM donations WHERE donation_id = ?";
        Donations donation;
        String status;
        String receivedById;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, donationId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Donor donor = DonorRepository.findById(connection, rs.getString("donor_id"));
                donation = new Donations(donationId, donor, rs.getString("donation_date"),
                        rs.getString("receiving_location"), rs.getString("delivery_reference"));
                status = rs.getString("status");
                receivedById = rs.getString("received_by");
            }
        }
        Map<String, Inventory> inventory = new HashMap<>();
        String lineSql = "SELECT item_id, quantity, batch_reference, expiry_date FROM donation_items "
                + "WHERE donation_id = ? ORDER BY line_id";
        try (PreparedStatement statement = connection.prepareStatement(lineSql)) {
            statement.setString(1, donationId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String itemId = rs.getString("item_id");
                    if (!inventory.containsKey(itemId)) {
                        inventory.put(itemId, InventoryRepository.findById(connection, itemId));
                    }
                    donation.addItem(new TransactionItem(inventory.get(itemId), rs.getInt("quantity"),
                            rs.getString("batch_reference"), rs.getString("expiry_date")));
                }
            }
        }
        donation.restoreStatus(TransactionStatus.valueOf(status), UserRepository.findById(connection, receivedById));
        return donation;
    }

    /**
     * Stores an approval or rejection. Only a PENDING row can change, so the same donation can
     * never be posted twice. On approval the new stock batches are saved too.
     */
    public static void saveDecision(Connection connection, Donations donation) throws SQLException {
        String sql = "UPDATE donations SET status = ?, received_by = ? WHERE donation_id = ? AND status = 'PENDING'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, donation.getStatus().name());
            statement.setString(2, donation.getReceivedBy() == null ? null : donation.getReceivedBy().getAccountId());
            statement.setString(3, donation.getDonationId());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("This donation was already decided by someone else.");
            }
        }
        if (donation.getStatus() != TransactionStatus.APPROVED) {
            return;
        }
        // The model names each new batch "<donation id>-<line number>" unless a reference was given.
        List<TransactionItem> items = donation.getItems();
        String lineSql = "UPDATE donation_items SET batch_reference = ? WHERE line_id = ("
                + "SELECT line_id FROM donation_items WHERE donation_id = ? ORDER BY line_id LIMIT 1 OFFSET ?) "
                + "AND batch_reference IS NULL";
        for (int index = 0; index < items.size(); index++) {
            try (PreparedStatement statement = connection.prepareStatement(lineSql)) {
                statement.setString(1, donation.getDonationId() + "-" + (index + 1));
                statement.setString(2, donation.getDonationId());
                statement.setInt(3, index);
                statement.executeUpdate();
            }
        }
        for (Inventory item : distinctInventory(items)) {
            InventoryRepository.saveBatches(connection, item);
        }
    }

    static List<Inventory> distinctInventory(List<TransactionItem> items) {
        List<Inventory> distinct = new ArrayList<>();
        for (TransactionItem item : items) {
            if (!distinct.contains(item.getInventory())) {
                distinct.add(item.getInventory());
            }
        }
        return distinct;
    }

    /** Donation history (newest first) with each donation's item lines. */
    public List<Map<String, Object>> listSummaries(String barangayId) throws SQLException {
        String sql = "SELECT d.donation_id AS id, d.donation_date AS date, d.recorded_at AS recordedAt, d.status, "
                + "d.receiving_location AS receivingLocation, d.delivery_reference AS deliveryReference, "
                + "d.barangay_id AS barangayId, b.name AS barangay, dn.donor_id AS donorId, dn.name AS donor, "
                + "dn.type AS donorType, r.name AS receivedBy, rb.name AS recordedBy "
                + "FROM donations d JOIN donors dn ON dn.donor_id = d.donor_id "
                + "JOIN barangays b ON b.barangay_id = d.barangay_id "
                + "LEFT JOIN users r ON r.account_id = d.received_by "
                + "LEFT JOIN users rb ON rb.account_id = d.recorded_by "
                + "WHERE (? IS NULL OR d.barangay_id = ?) "
                + "ORDER BY COALESCE(d.recorded_at, d.donation_date) DESC, d.donation_id DESC";
        List<Map<String, Object>> donations = Database.query(sql, barangayId, barangayId);

        String lineSql = "SELECT di.donation_id AS donationId, di.item_id AS itemId, i.item_name AS name, i.unit, "
                + "di.quantity, di.expiry_date AS expiry FROM donation_items di "
                + "JOIN inventory_items i ON i.item_id = di.item_id "
                + "JOIN donations d ON d.donation_id = di.donation_id "
                + "WHERE (? IS NULL OR d.barangay_id = ?) ORDER BY di.line_id";
        Map<String, List<Map<String, Object>>> lines = new LinkedHashMap<>();
        for (Map<String, Object> line : Database.query(lineSql, barangayId, barangayId)) {
            String id = (String) line.remove("donationId");
            lines.computeIfAbsent(id, key -> new ArrayList<>()).add(line);
        }
        for (Map<String, Object> donation : donations) {
            donation.put("items", lines.getOrDefault((String) donation.get("id"), new ArrayList<>()));
        }
        return donations;
    }

    /** The barangay a saved donation belongs to, or null. */
    public static String barangayOf(Connection connection, String donationId) throws SQLException {
        List<Map<String, Object>> rows = Database.query(connection,
                "SELECT barangay_id FROM donations WHERE donation_id = ?", donationId);
        return rows.isEmpty() ? null : (String) rows.get(0).get("barangay_id");
    }
}
