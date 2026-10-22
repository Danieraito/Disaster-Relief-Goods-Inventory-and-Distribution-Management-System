package LIB;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DonorRepository {
    /** A donor with the latest confirmed donation received by one barangay. */
    public static final class Row {
        public Donor donor;
        public String latestDonationId;
        public LocalDate latestDonationDate;
        public String latestGoods = "";
    }

    public List<Row> findAll(String barangayId) throws SQLException {
        return Db.read(connection -> {
            List<Row> rows = Db.list(connection, "SELECT dn.donor_id, dn.name, dn.donor_type, dn.contact_person, "
                    + "dn.contact_details, ld.donation_id, ld.donation_date FROM donors dn LEFT JOIN donations ld "
                    + "ON ld.donation_id = (SELECT d2.donation_id FROM donations d2 WHERE d2.donor_id = dn.donor_id "
                    + "AND d2.status = 'APPROVED' AND d2.barangay_id = ? ORDER BY d2.donation_date DESC, "
                    + "d2.donation_id DESC LIMIT 1) ORDER BY dn.name", row -> {
                        Row item = new Row();
                        item.donor = read(row);
                        item.latestDonationId = row.getString("donation_id");
                        item.latestDonationDate = Db.date(row, "donation_date");
                        return item;
                    }, barangayId);
            Map<String, Row> byDonation = new HashMap<>();
            for (Row row : rows) {
                if (row.latestDonationId != null) {
                    byDonation.put(row.latestDonationId, row);
                }
            }
            if (!byDonation.isEmpty()) {
                StringBuilder marks = new StringBuilder();
                for (int index = 0; index < byDonation.size(); index++) {
                    marks.append(index == 0 ? "?" : ", ?");
                }
                Map<String, List<String>> goods = new HashMap<>();
                Db.list(connection, "SELECT di.donation_id, di.quantity, i.unit FROM donation_items di "
                        + "JOIN inventory_items i ON i.inventory_item_id = di.inventory_item_id "
                        + "WHERE di.donation_id IN (" + marks + ") ORDER BY di.line_number", line -> {
                            goods.computeIfAbsent(line.getString("donation_id"), key -> new ArrayList<>())
                                    .add(Units.quantity(line.getInt("quantity"), line.getString("unit")));
                            return null;
                        }, byDonation.keySet().toArray());
                for (Map.Entry<String, List<String>> entry : goods.entrySet()) {
                    byDonation.get(entry.getKey()).latestGoods = String.join(" · ", entry.getValue());
                }
            }
            return rows;
        });
    }

    public Donor findById(String donorId) throws SQLException {
        return Db.read(connection -> Db.one(connection, "SELECT donor_id, name, donor_type, contact_person, "
                + "contact_details FROM donors WHERE donor_id = ?", DonorRepository::read, donorId));
    }

    /** Adds a donor (blank id) or updates one, and records it in the audit ledger. */
    public Donor save(Donor donor, Barangay workspace, UserAccount actor) throws SQLException {
        return Db.transaction(connection -> {
            boolean isNew = donor.getDonorId() == null || donor.getDonorId().trim().isEmpty();
            if (isNew) {
                if (Db.intValue(connection, "SELECT COUNT(*) FROM donors WHERE LOWER(name) = LOWER(?)",
                        donor.getName()) > 0) {
                    throw new IllegalArgumentException("A donor named " + donor.getName() + " already exists.");
                }
                donor.setDonorId(IdGenerator.next(connection, "DNR", false, 3));
                Db.update(connection, "INSERT INTO donors (donor_id, name, donor_type, contact_person, "
                        + "contact_details) VALUES (?, ?, ?, ?, ?)", donor.getDonorId(), donor.getName(),
                        donor.getType(), donor.getContactPerson(), donor.getContactDetails());
            } else {
                Db.update(connection, "UPDATE donors SET name = ?, donor_type = ?, contact_person = ?, "
                        + "contact_details = ? WHERE donor_id = ?", donor.getName(), donor.getType(),
                        donor.getContactPerson(), donor.getContactDetails(), donor.getDonorId());
            }
            AuditRepository.record(connection, actor, new AuditRepository.Entry("DONOR_SAVED",
                    AuditRepository.MODULE_DONATIONS).reference(donor.getDonorId()).summary(donor.getName()
                    + (isNew ? " added as donor" : " contact details updated")).movement("No stock movement")
                    .result("Previous receipts unchanged").barangay(workspace == null ? null : workspace.getBarangayId()));
            return donor;
        });
    }

    private static Donor read(java.sql.ResultSet row) throws SQLException {
        return new Donor(row.getString("donor_id"), row.getString("name"),
                Donor.Type.valueOf(row.getString("donor_type")), row.getString("contact_person"),
                row.getString("contact_details"));
    }
}
