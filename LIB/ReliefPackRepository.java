package LIB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Saves and loads relief pack templates and their items. */
public class ReliefPackRepository {

    public static void insert(Connection connection, ReliefPack pack) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO relief_packs (pack_id, barangay_id, name) VALUES (?, ?, ?)")) {
            statement.setString(1, pack.getPackId());
            statement.setString(2, pack.getBarangay().getBarangayId());
            statement.setString(3, pack.getName());
            statement.executeUpdate();
        }
        for (ReliefPack.Line line : pack.getLines()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO relief_pack_items (pack_id, item_id, quantity) VALUES (?, ?, ?)")) {
                statement.setString(1, pack.getPackId());
                statement.setString(2, line.getInventory().getItemId());
                statement.setInt(3, line.getQuantityPerFamily());
                statement.executeUpdate();
            }
        }
    }

    public static void delete(Connection connection, String packId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM relief_pack_items WHERE pack_id = ?")) {
            statement.setString(1, packId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM relief_packs WHERE pack_id = ?")) {
            statement.setString(1, packId);
            statement.executeUpdate();
        }
    }

    /**
     * Loads one pack. Items are taken from the shared map when present, so several distributions
     * made from the same pack work on the very same Inventory objects (and stock adds up correctly).
     */
    public static ReliefPack load(Connection connection, String packId, Map<String, Inventory> shared)
            throws SQLException {
        String barangayId;
        String name;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT barangay_id, name FROM relief_packs WHERE pack_id = ?")) {
            statement.setString(1, packId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                barangayId = rs.getString("barangay_id");
                name = rs.getString("name");
            }
        }
        ReliefPack pack = new ReliefPack(packId, name, BarangayRepository.findById(connection, barangayId));
        List<String[]> lines = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT item_id, quantity FROM relief_pack_items WHERE pack_id = ? ORDER BY rowid")) {
            statement.setString(1, packId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    lines.add(new String[] {rs.getString("item_id"), String.valueOf(rs.getInt("quantity"))});
                }
            }
        }
        Map<String, Inventory> items = shared == null ? new HashMap<>() : shared;
        for (String[] line : lines) {
            if (!items.containsKey(line[0])) {
                items.put(line[0], InventoryRepository.findById(connection, line[0]));
            }
            pack.addLine(items.get(line[0]), Integer.parseInt(line[1]));
        }
        return pack;
    }

    /** Every pack of a workspace (null = all workspaces), loaded as models. */
    public List<ReliefPack> findByBarangay(String barangayId) throws SQLException {
        List<ReliefPack> packs = new ArrayList<>();
        try (Connection connection = Database.getConnection()) {
            for (Map<String, Object> row : Database.query(connection,
                    "SELECT pack_id FROM relief_packs WHERE (? IS NULL OR barangay_id = ?) ORDER BY name",
                    barangayId, barangayId)) {
                packs.add(load(connection, (String) row.get("pack_id"), null));
            }
        }
        return packs;
    }
}
