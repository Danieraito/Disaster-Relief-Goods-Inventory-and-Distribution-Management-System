package LIB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Saves and loads barangay workspaces. */
public class BarangayRepository {

    public void save(Barangay barangay) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            save(connection, barangay);
        }
    }

    public Optional<Barangay> findById(String barangayId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            return Optional.ofNullable(findById(connection, barangayId));
        }
    }

    public List<Barangay> findAll() throws SQLException {
        List<Barangay> barangays = new ArrayList<>();
        try (Connection connection = Database.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT barangay_id, name, municipality, province FROM barangays ORDER BY name")) {
            while (rs.next()) {
                barangays.add(read(rs));
            }
        }
        return barangays;
    }

    // The versions below take an open connection so other repositories can reuse them
    // inside one database transaction.
    public static void save(Connection connection, Barangay barangay) throws SQLException {
        String sql = "INSERT INTO barangays (barangay_id, name, municipality, province) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(barangay_id) DO UPDATE SET name = excluded.name, "
                + "municipality = excluded.municipality, province = excluded.province";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, barangay.getBarangayId());
            statement.setString(2, barangay.getName());
            statement.setString(3, barangay.getMunicipality());
            statement.setString(4, barangay.getProvince());
            statement.executeUpdate();
        }
    }

    /** Returns null when no barangay has this id. */
    public static Barangay findById(Connection connection, String barangayId) throws SQLException {
        if (barangayId == null) {
            return null;
        }
        String sql = "SELECT barangay_id, name, municipality, province FROM barangays WHERE barangay_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, barangayId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        }
    }

    private static Barangay read(ResultSet rs) throws SQLException {
        return new Barangay(rs.getString("barangay_id"), rs.getString("name"),
                rs.getString("municipality"), rs.getString("province"));
    }
}