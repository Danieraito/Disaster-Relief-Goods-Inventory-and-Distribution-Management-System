package LIB;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class EvacuationCenterRepository {
    public List<EvacuationCenter> findByBarangay(Barangay barangay) throws SQLException {
        return Db.read(connection -> Db.list(connection, "SELECT center_id, name, address, capacity "
                + "FROM evacuation_centers WHERE barangay_id = ? ORDER BY name",
                row -> new EvacuationCenter(row.getString("center_id"), row.getString("name"),
                        row.getString("address"), row.getInt("capacity"), barangay), barangay.getBarangayId()));
    }

    static EvacuationCenter findById(Connection connection, String centerId, Barangay barangay) throws SQLException {
        if (centerId == null) {
            return null;
        }
        return Db.one(connection, "SELECT center_id, name, address, capacity FROM evacuation_centers "
                + "WHERE center_id = ?", row -> new EvacuationCenter(row.getString("center_id"), row.getString("name"),
                row.getString("address"), row.getInt("capacity"), barangay), centerId);
    }

    public EvacuationCenter create(String name, String address, int capacity, Barangay barangay, UserAccount actor)
            throws SQLException {
        EvacuationCenterController rules = new EvacuationCenterController();
        return Db.transaction(connection -> {
            if (Db.intValue(connection, "SELECT COUNT(*) FROM evacuation_centers WHERE barangay_id = ? "
                    + "AND LOWER(name) = LOWER(?)", barangay.getBarangayId(), name.trim()) > 0) {
                throw new IllegalArgumentException("An evacuation center with that name already exists.");
            }
            String id = IdGenerator.next(connection, "EVC", false, 3);
            EvacuationCenter center = rules.createEvacuationCenter(id, name, address, capacity, barangay, actor);
            Db.update(connection, "INSERT INTO evacuation_centers (center_id, barangay_id, name, address, capacity) "
                    + "VALUES (?, ?, ?, ?, ?)", id, barangay.getBarangayId(), center.getName(), center.getAddress(),
                    center.getCapacity());
            return center;
        });
    }
}
