package LIB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Saves and loads disaster events and the evacuation centers of each barangay. */
public class DisasterRepository {

    // ----------------------------------------------------------------- disasters
    public static void save(Connection connection, Disaster disaster, String closedDate) throws SQLException {
        String sql = "INSERT INTO disasters (disaster_id, barangay_id, name, type, location, disaster_date, "
                + "status, severity, affected_areas, closed_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(disaster_id) DO UPDATE SET name = excluded.name, type = excluded.type, "
                + "location = excluded.location, disaster_date = excluded.disaster_date, "
                + "status = excluded.status, severity = excluded.severity, "
                + "affected_areas = excluded.affected_areas, closed_date = excluded.closed_date";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, disaster.getDisasterId());
            statement.setString(2, disaster.getBarangay().getBarangayId());
            statement.setString(3, disaster.getName());
            statement.setString(4, disaster.getType());
            statement.setString(5, disaster.getLocation());
            statement.setString(6, disaster.getDate());
            statement.setString(7, disaster.getStatus());
            statement.setString(8, disaster.getSeverity());
            statement.setString(9, disaster.getAffectedAreas());
            statement.setString(10, closedDate);
            statement.executeUpdate();
        }
    }

    /** Returns null when no disaster has this id. */
    public static Disaster findById(Connection connection, String disasterId) throws SQLException {
        if (disasterId == null) {
            return null;
        }
        try (PreparedStatement statement = connection.prepareStatement(SELECT + " WHERE disaster_id = ?")) {
            statement.setString(1, disasterId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? read(connection, rs) : null;
            }
        }
    }

    public Disaster findById(String disasterId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            return findById(connection, disasterId);
        }
    }

    /**
     * Disasters with household counts, newest first. barangayId null means all workspaces.
     * "assisted" counts households with at least one approved distribution for that disaster.
     */
    public List<Map<String, Object>> listSummaries(String barangayId) throws SQLException {
        String sql = "SELECT d.disaster_id AS id, d.name, d.type, d.location, d.disaster_date AS date, "
                + "d.status, d.severity, d.affected_areas AS affectedAreas, d.closed_date AS closedDate, "
                + "d.barangay_id AS barangayId, b.name AS barangay, "
                + "(SELECT COUNT(*) FROM households h WHERE h.disaster_id = d.disaster_id) AS families, "
                + "(SELECT COUNT(*) FROM family_members m JOIN households h ON h.beneficiary_id = m.household_id "
                + "   WHERE h.disaster_id = d.disaster_id) AS individuals, "
                + "(SELECT COUNT(DISTINCT x.household_id) FROM distributions x "
                + "   WHERE x.disaster_id = d.disaster_id AND x.status = 'APPROVED') AS assisted "
                + "FROM disasters d JOIN barangays b ON b.barangay_id = d.barangay_id "
                + "WHERE (? IS NULL OR d.barangay_id = ?) "
                + "ORDER BY d.disaster_date DESC, d.disaster_id DESC";
        return Database.query(sql, barangayId, barangayId);
    }

    private static final String SELECT = "SELECT disaster_id, barangay_id, name, type, location, disaster_date, "
            + "status, severity, affected_areas FROM disasters";

    private static Disaster read(Connection connection, ResultSet rs) throws SQLException {
        Barangay barangay = BarangayRepository.findById(connection, rs.getString("barangay_id"));
        Disaster disaster = new Disaster(rs.getString("disaster_id"), rs.getString("name"), rs.getString("type"),
                rs.getString("location"), rs.getString("disaster_date"), rs.getString("status"), barangay);
        disaster.setSeverity(rs.getString("severity"));
        disaster.setAffectedAreas(rs.getString("affected_areas"));
        return disaster;
    }

    // -------------------------------------------------------- evacuation centers
    public static void saveCenter(Connection connection, EvacuationCenter center) throws SQLException {
        String sql = "INSERT INTO evacuation_centers (center_id, barangay_id, name, address, capacity) "
                + "VALUES (?, ?, ?, ?, ?) ON CONFLICT(center_id) DO UPDATE SET name = excluded.name, "
                + "address = excluded.address, capacity = excluded.capacity";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, center.getCenterId());
            statement.setString(2, center.getBarangay().getBarangayId());
            statement.setString(3, center.getName());
            statement.setString(4, center.getAddress());
            statement.setInt(5, center.getCapacity());
            statement.executeUpdate();
        }
    }

    public static EvacuationCenter findCenterById(Connection connection, String centerId) throws SQLException {
        if (centerId == null || centerId.isEmpty()) {
            return null;
        }
        String sql = "SELECT center_id, barangay_id, name, address, capacity FROM evacuation_centers WHERE center_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, centerId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new EvacuationCenter(rs.getString("center_id"), rs.getString("name"),
                        rs.getString("address"), rs.getInt("capacity"),
                        BarangayRepository.findById(connection, rs.getString("barangay_id")));
            }
        }
    }

    public List<Map<String, Object>> listCenters(String barangayId) throws SQLException {
        String sql = "SELECT c.center_id AS id, c.name, c.address, c.capacity, c.barangay_id AS barangayId, "
                + "b.name AS barangay FROM evacuation_centers c JOIN barangays b ON b.barangay_id = c.barangay_id "
                + "WHERE (? IS NULL OR c.barangay_id = ?) ORDER BY c.name";
        return Database.query(sql, barangayId, barangayId);
    }

    /** Ids of every disaster in a workspace (used to check access quickly). */
    public List<String> idsFor(String barangayId) throws SQLException {
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> row : listSummaries(barangayId)) {
            ids.add((String) row.get("id"));
        }
        return ids;
    }
}
