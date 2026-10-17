package LIB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BarangayRepository {
    public void save(Barangay barangay) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            save(connection, barangay);
        }
    }

    public Optional<Barangay> findById(String barangayId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return Optional.ofNullable(findById(connection, barangayId));
        }
    }

    public List<Barangay> findAll() throws SQLException {
        List<Barangay> barangays = new ArrayList<>();
        String sql = "SELECT barangay_id, name, municipality, province FROM barangays ORDER BY name";
        try (Connection connection = DatabaseConnection.getConnection();
                Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery(sql)) {
            while (results.next()) {
                barangays.add(read(results));
            }
        }
        return barangays;
    }

    static void save(Connection connection, Barangay barangay) throws SQLException {
        String sql = "INSERT INTO barangays (barangay_id, name, municipality, province) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE name = VALUES(name), municipality = VALUES(municipality), "
                + "province = VALUES(province)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, barangay.getBarangayId());
            statement.setString(2, barangay.getName());
            statement.setString(3, barangay.getMunicipality());
            statement.setString(4, barangay.getProvince());
            statement.executeUpdate();
        }
    }

    static Barangay findById(Connection connection, String barangayId) throws SQLException {
        if (barangayId == null) {
            return null;
        }
        String sql = "SELECT barangay_id, name, municipality, province FROM barangays WHERE barangay_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, barangayId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? read(results) : null;
            }
        }
    }

    private static Barangay read(ResultSet results) throws SQLException {
        return new Barangay(results.getString("barangay_id"), results.getString("name"),
                results.getString("municipality"), results.getString("province"));
    }
}