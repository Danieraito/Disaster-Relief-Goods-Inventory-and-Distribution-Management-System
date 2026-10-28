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
 * Saves and loads distributions (releases of relief goods) with their item lines. Approval itself
 * is done by the Distribution model; this class only stores the result.
 */
public class DistributionRepository {

    /** Saves a new distribution (normally PENDING after submit) and its lines. Each line names its batch. */
    public static void insert(Connection connection, Distribution distribution, List<String> batchIds,
            UserAccount recordedBy) throws SQLException {
        String sql = "INSERT INTO distributions (distribution_id, barangay_id, disaster_id, household_id, center_id, "
                + "distribution_date, release_location, receipt_reference, status, released_by, recorded_by, recorded_at, round_no) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, distribution.getDistributionId());
            statement.setString(2, distribution.getBarangay().getBarangayId());
            statement.setString(3, distribution.getDisaster().getDisasterId());
            statement.setString(4, distribution.getBeneficiary().getBeneficiaryId());
            statement.setString(5, distribution.getEvacuationCenter() == null
                    ? null : distribution.getEvacuationCenter().getCenterId());
            statement.setString(6, distribution.getDistributionDate());
            statement.setString(7, distribution.getReleaseLocation());
            statement.setString(8, distribution.getReceiptReference());
            statement.setString(9, distribution.getStatus().name());
            statement.setString(10, distribution.getReleasedBy() == null
                    ? null : distribution.getReleasedBy().getAccountId());
            statement.setString(11, recordedBy.getAccountId());
            statement.setString(12, Database.now());
            statement.setInt(13, distribution.getRoundNumber());
            statement.executeUpdate();
        }
        String lineSql = "INSERT INTO distribution_items (distribution_id, item_id, batch_id, quantity) VALUES (?, ?, ?, ?)";
        List<TransactionItem> items = distribution.getItems();
        for (int index = 0; index < items.size(); index++) {
            try (PreparedStatement statement = connection.prepareStatement(lineSql)) {
                statement.setString(1, distribution.getDistributionId());
                statement.setString(2, items.get(index).getInventory().getItemId());
                statement.setString(3, batchIds.get(index));
                statement.setInt(4, items.get(index).getQuantity());
                statement.executeUpdate();
            }
        }
    }

    /**
     * Rebuilds a saved distribution. The household is loaded with the same Disaster object, and
     * lines for the same item share one Inventory object, because the model compares them directly.
     */
    public static Distribution load(Connection connection, String distributionId) throws SQLException {
        String sql = "SELECT disaster_id, household_id, center_id, distribution_date, release_location, "
                + "receipt_reference, status, released_by, round_no FROM distributions WHERE distribution_id = ?";
        Distribution distribution;
        String status;
        String releasedById;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, distributionId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Disaster disaster = DisasterRepository.findById(connection, rs.getString("disaster_id"));
                Beneficiaries household = HouseholdRepository.load(connection, rs.getString("household_id"), disaster);
                EvacuationCenter center = DisasterRepository.findCenterById(connection, rs.getString("center_id"));
                distribution = new Distribution(distributionId, rs.getString("distribution_date"), disaster,
                        household, center, rs.getString("release_location"), rs.getString("receipt_reference"));
                distribution.setRoundNumber(rs.getInt("round_no"));
                status = rs.getString("status");
                releasedById = rs.getString("released_by");
            }
        }
        Map<String, Inventory> inventory = new HashMap<>();
        String lineSql = "SELECT item_id, batch_id, quantity FROM distribution_items WHERE distribution_id = ? ORDER BY line_id";
        try (PreparedStatement statement = connection.prepareStatement(lineSql)) {
            statement.setString(1, distributionId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String itemId = rs.getString("item_id");
                    if (!inventory.containsKey(itemId)) {
                        inventory.put(itemId, InventoryRepository.findById(connection, itemId));
                    }
                    distribution.addItem(new TransactionItem(inventory.get(itemId), rs.getInt("quantity"),
                            rs.getString("batch_id"), null));
                }
            }
        }
        distribution.restoreStatus(TransactionStatus.valueOf(status),
                UserRepository.findById(connection, releasedById));
        return distribution;
    }

    /**
     * Stores an approval or rejection. Only a PENDING row can change, so the same release can
     * never deduct stock twice. On approval the reduced batch quantities are saved too.
     */
    public static void saveDecision(Connection connection, Distribution distribution) throws SQLException {
        String sql = "UPDATE distributions SET status = ?, released_by = ? WHERE distribution_id = ? AND status = 'PENDING'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, distribution.getStatus().name());
            statement.setString(2, distribution.getReleasedBy() == null
                    ? null : distribution.getReleasedBy().getAccountId());
            statement.setString(3, distribution.getDistributionId());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("This distribution was already decided by someone else.");
            }
        }
        if (distribution.getStatus() == TransactionStatus.APPROVED) {
            for (Inventory item : DonationRepository.distinctInventory(distribution.getItems())) {
                InventoryRepository.saveBatches(connection, item);
            }
        }
    }

    /**
     * Another pending or approved distribution for the same household, disaster and round, or null.
     * Used to block duplicate releases within a round.
     */
    public static String findExistingRelease(Connection connection, String householdId, String disasterId, int round)
            throws SQLException {
        List<Map<String, Object>> rows = Database.query(connection,
                "SELECT distribution_id FROM distributions WHERE household_id = ? AND disaster_id = ? "
                        + "AND round_no = ? AND status IN ('PENDING','APPROVED') ORDER BY distribution_id LIMIT 1",
                householdId, disasterId, round);
        return rows.isEmpty() ? null : (String) rows.get(0).get("distribution_id");
    }

    /** Distribution history (newest first) with each release's item lines. */
    public List<Map<String, Object>> listSummaries(String barangayId) throws SQLException {
        String sql = "SELECT x.distribution_id AS id, x.distribution_date AS date, x.recorded_at AS recordedAt, "
                + "x.status, x.round_no AS round, x.release_location AS releaseLocation, x.receipt_reference AS receiptReference, "
                + "x.disaster_id AS disasterId, d.name AS disaster, x.household_id AS householdId, h.name AS household, "
                + "h.address AS householdAddress, x.center_id AS centerId, c.name AS center, "
                + "x.barangay_id AS barangayId, b.name AS barangay, r.name AS releasedBy, rb.name AS recordedBy "
                + "FROM distributions x JOIN disasters d ON d.disaster_id = x.disaster_id "
                + "JOIN households h ON h.beneficiary_id = x.household_id "
                + "JOIN barangays b ON b.barangay_id = x.barangay_id "
                + "LEFT JOIN evacuation_centers c ON c.center_id = x.center_id "
                + "LEFT JOIN users r ON r.account_id = x.released_by "
                + "LEFT JOIN users rb ON rb.account_id = x.recorded_by "
                + "WHERE (? IS NULL OR x.barangay_id = ?) "
                + "ORDER BY COALESCE(x.recorded_at, x.distribution_date) DESC, x.distribution_id DESC";
        List<Map<String, Object>> releases = Database.query(sql, barangayId, barangayId);

        String lineSql = "SELECT xi.distribution_id AS distributionId, xi.item_id AS itemId, i.item_name AS name, "
                + "i.unit, xi.batch_id AS batchId, xi.quantity FROM distribution_items xi "
                + "JOIN inventory_items i ON i.item_id = xi.item_id "
                + "JOIN distributions x ON x.distribution_id = xi.distribution_id "
                + "WHERE (? IS NULL OR x.barangay_id = ?) ORDER BY xi.line_id";
        Map<String, List<Map<String, Object>>> lines = new LinkedHashMap<>();
        for (Map<String, Object> line : Database.query(lineSql, barangayId, barangayId)) {
            String id = (String) line.remove("distributionId");
            lines.computeIfAbsent(id, key -> new ArrayList<>()).add(line);
        }
        for (Map<String, Object> release : releases) {
            release.put("items", lines.getOrDefault((String) release.get("id"), new ArrayList<>()));
        }
        return releases;
    }
}
