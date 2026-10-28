package LIB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Saves and loads donors. Donors are shared by every barangay workspace. */
public class DonorRepository {

    public static void save(Connection connection, Donor donor) throws SQLException {
        String sql = "INSERT INTO donors (donor_id, name, type, contact_person, contact_details) "
                + "VALUES (?, ?, ?, ?, ?) ON CONFLICT(donor_id) DO UPDATE SET name = excluded.name, "
                + "type = excluded.type, contact_person = excluded.contact_person, "
                + "contact_details = excluded.contact_details";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, donor.getDonorId());
            statement.setString(2, donor.getName());
            statement.setString(3, donor.getType().name());
            statement.setString(4, donor.getContactPerson());
            statement.setString(5, donor.getContactDetails());
            statement.executeUpdate();
        }
    }

    public static Donor findById(Connection connection, String donorId) throws SQLException {
        if (donorId == null) {
            return null;
        }
        String sql = "SELECT donor_id, name, type, contact_person, contact_details FROM donors WHERE donor_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, donorId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Donor(rs.getString("donor_id"), rs.getString("name"),
                        Donor.Type.valueOf(rs.getString("type")), rs.getString("contact_person"),
                        rs.getString("contact_details"));
            }
        }
    }

    /** Every donor with how many approved donations they have made. */
    public List<Map<String, Object>> listSummaries() throws SQLException {
        String sql = "SELECT dn.donor_id AS id, dn.name, dn.type, dn.contact_person AS contactPerson, "
                + "dn.contact_details AS contactDetails, "
                + "(SELECT COUNT(*) FROM donations d WHERE d.donor_id = dn.donor_id AND d.status = 'APPROVED') AS donations "
                + "FROM donors dn ORDER BY dn.name";
        return Database.query(sql);
    }
}
