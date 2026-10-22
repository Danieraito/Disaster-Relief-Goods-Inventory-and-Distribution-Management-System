package LIB;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DonationRepository {
    public enum Mode { DRAFT, SUBMIT, POST }

    /** One donated item line as typed on the form. */
    public static final class Line {
        public final String itemId;
        public final int quantity;
        public final LocalDate expiry;

        public Line(String itemId, int quantity, LocalDate expiry) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.expiry = expiry;
        }
    }

    /** One row of the donation history table. */
    public static final class Row {
        public String donationId;
        public LocalDate date;
        public LocalDateTime recordedAt;
        public String donorName;
        public Donor.Type donorType;
        public TransactionStatus status;
        public String officer = "";
        public String createdBy = "";
        public int totalUnits;
        public List<String> goods = new ArrayList<>();
    }

    public static final class Filter {
        public String text;
        public LocalDate from;
        public LocalDate to;
        public Donor.Type donorType;
        public TransactionStatus status;
        public String itemId;
        public int limit = 200;
    }

    /** What the receipt screen shows for each posted line. */
    public static final class Movement {
        public final String itemId;
        public final String itemName;
        public final String unit;
        public final int before;
        public final int added;
        public final int after;

        Movement(String itemId, String itemName, String unit, int before, int added, int after) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.unit = unit;
            this.before = before;
            this.added = added;
            this.after = after;
        }
    }

    // ------------------------------------------------------------------ list / read

    public List<Row> list(String barangayId, Filter filter) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT d.donation_id, d.donation_date, d.created_at, d.approved_at, "
                + "d.status, dn.name AS donor_name, dn.donor_type, ap.name AS officer, cr.name AS creator, "
                + "(SELECT GROUP_CONCAT(CONCAT(di.quantity, '|', i.unit, '|', i.item_name) ORDER BY di.line_number "
                + "SEPARATOR ';;') FROM donation_items di JOIN inventory_items i ON i.inventory_item_id = "
                + "di.inventory_item_id WHERE di.donation_id = d.donation_id) AS item_lines FROM donations d "
                + "JOIN donors dn ON dn.donor_id = d.donor_id "
                + "LEFT JOIN user_accounts ap ON ap.account_id = d.received_by_account_id "
                + "LEFT JOIN user_accounts cr ON cr.account_id = d.created_by WHERE d.barangay_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(barangayId);
        if (filter.status != null) {
            sql.append(" AND d.status = ?");
            params.add(filter.status);
        }
        if (filter.donorType != null) {
            sql.append(" AND dn.donor_type = ?");
            params.add(filter.donorType);
        }
        if (filter.from != null) {
            sql.append(" AND d.donation_date >= ?");
            params.add(filter.from);
        }
        if (filter.to != null) {
            sql.append(" AND d.donation_date <= ?");
            params.add(filter.to);
        }
        if (filter.itemId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM donation_items x WHERE x.donation_id = d.donation_id "
                    + "AND x.inventory_item_id = ?)");
            params.add(filter.itemId);
        }
        if (filter.text != null && !filter.text.trim().isEmpty()) {
            sql.append(" AND (d.donation_id LIKE ? OR dn.name LIKE ? OR d.delivery_reference LIKE ?)");
            String like = "%" + filter.text.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY d.donation_date DESC, COALESCE(d.approved_at, d.created_at) DESC, d.donation_id DESC LIMIT ")
                .append(Math.max(1, filter.limit));
        return Db.read(connection -> Db.list(connection, sql.toString(), row -> {
            Row item = new Row();
            item.donationId = row.getString("donation_id");
            item.date = Db.date(row, "donation_date");
            item.recordedAt = Db.dateTime(row, "approved_at") != null ? Db.dateTime(row, "approved_at")
                    : Db.dateTime(row, "created_at");
            item.donorName = row.getString("donor_name");
            item.donorType = Donor.Type.valueOf(row.getString("donor_type"));
            item.status = TransactionStatus.valueOf(row.getString("status"));
            item.officer = Db.text(row, "officer");
            item.createdBy = Db.text(row, "creator");
            String lines = row.getString("item_lines");
            if (lines != null) {
                for (String line : lines.split(";;")) {
                    String[] part = line.split("\\|", 3);
                    int quantity = Integer.parseInt(part[0]);
                    item.totalUnits += quantity;
                    item.goods.add(Units.quantity(quantity, part[1]) + " " + part[2]);
                }
            }
            return item;
        }, params.toArray()));
    }

    /** Loads a donation with its donor, lines and (shared) inventory items. */
    public Donations find(String donationId) throws SQLException {
        return Db.read(connection -> find(connection, donationId));
    }

    static Donations find(Connection connection, String donationId) throws SQLException {
        Object[] header = Db.one(connection, "SELECT d.barangay_id, d.donor_id, d.donation_date, d.receiving_location, "
                + "d.delivery_reference, d.status, d.created_at, d.approved_at, d.donor_representative, d.notes, "
                + "d.rejection_reason, d.received_by_account_id, cr.name AS creator FROM donations d "
                + "LEFT JOIN user_accounts cr ON cr.account_id = d.created_by WHERE d.donation_id = ?", row -> new Object[] {
                    row.getString("barangay_id"), row.getString("donor_id"), Db.date(row, "donation_date").toString(),
                    row.getString("receiving_location"), row.getString("delivery_reference"), row.getString("status"),
                    Db.dateTime(row, "created_at"), Db.dateTime(row, "approved_at"), row.getString("donor_representative"),
                    row.getString("notes"), row.getString("rejection_reason"), row.getString("received_by_account_id"),
                    row.getString("creator") }, donationId);
        if (header == null) {
            return null;
        }
        Barangay barangay = BarangayRepository.findById(connection, (String) header[0]);
        Donor donor = Db.one(connection, "SELECT donor_id, name, donor_type, contact_person, contact_details "
                + "FROM donors WHERE donor_id = ?", row -> new Donor(row.getString("donor_id"), row.getString("name"),
                Donor.Type.valueOf(row.getString("donor_type")), row.getString("contact_person"),
                row.getString("contact_details")), header[1]);
        Donations donation = new Donations(donationId, donor, (String) header[2], (String) header[3], (String) header[4]);
        donation.setWorkspace(barangay);
        donation.setDonorRepresentative((String) header[8]);
        donation.setNotes((String) header[9]);
        List<Object[]> lines = Db.list(connection, "SELECT inventory_item_id, quantity, batch_reference, expiry_date "
                + "FROM donation_items WHERE donation_id = ? ORDER BY line_number", row -> new Object[] {
                    row.getString("inventory_item_id"), row.getInt("quantity"), row.getString("batch_reference"),
                    Db.date(row, "expiry_date") }, donationId);
        Map<String, Inventory> inventory = loadInventory(connection, lines);
        for (Object[] line : lines) {
            LocalDate expiry = (LocalDate) line[3];
            donation.addItem(new TransactionItem(inventory.get(line[0]), (Integer) line[1], (String) line[2],
                    expiry == null ? null : expiry.toString()));
        }
        UserAccount approver = header[11] == null ? null : new UserRepository().findByIdWith(connection, (String) header[11]);
        donation.restoreState(TransactionStatus.valueOf((String) header[5]), approver, (LocalDateTime) header[6],
                (String) header[12], (LocalDateTime) header[7], (String) header[10]);
        return donation;
    }

    private static Map<String, Inventory> loadInventory(Connection connection, List<Object[]> lines) throws SQLException {
        Map<String, Inventory> inventory = new HashMap<>();
        List<String> ids = new ArrayList<>();
        for (Object[] line : lines) {
            if (!ids.contains(line[0])) {
                ids.add((String) line[0]);
            }
        }
        if (ids.isEmpty()) {
            return inventory;
        }
        StringBuilder marks = new StringBuilder();
        for (int index = 0; index < ids.size(); index++) {
            marks.append(index == 0 ? "?" : ", ?");
        }
        for (Inventory item : InventoryRepository.loadItems(connection, "WHERE i.inventory_item_id IN (" + marks + ")",
                ids.toArray())) {
            inventory.put(item.getItemId(), item);
        }
        return inventory;
    }

    /** Before / added / after totals per item of a posted donation, for the receipt. */
    public List<Movement> movements(Donations donation) throws SQLException {
        InventoryRepository inventory = new InventoryRepository();
        Map<String, Movement> result = new LinkedHashMap<>();
        for (TransactionItem line : donation.getItems()) {
            Inventory item = line.getInventory();
            if (result.containsKey(item.getItemId())) {
                continue;
            }
            int added = 0;
            for (TransactionItem other : donation.getItems()) {
                if (other.getInventory().getItemId().equals(item.getItemId())) {
                    added += other.getQuantity();
                }
            }
            int before = item.getQuantity();
            int after = item.getQuantity();
            for (StockMovement movement : inventory.findMovements(item.getItemId())) {
                if (donation.getDonationId().equals(movement.reference)) {
                    before = movement.before;
                    after = movement.after;
                    // a donation with several lines of one item is shown as one movement
                    after = before + added;
                    break;
                }
            }
            result.put(item.getItemId(), new Movement(item.getItemId(), item.getItemName(), item.getUnit(),
                    before, added, after));
        }
        return new ArrayList<>(result.values());
    }

    // ------------------------------------------------------------------ writes

    /**
     * Saves a new donation, or replaces the lines of an existing draft. DRAFT stores it, SUBMIT sends it for
     * approval, POST (officers and admins only) approves it immediately so stock increases.
     */
    public Donations save(String existingId, Donor donor, Barangay workspace, LocalDate date, String location,
            String deliveryReference, String donorRepresentative, String notes, List<Line> lines, Mode mode,
            UserAccount actor) throws SQLException {
        DonationController rules = new DonationController();
        return Db.transaction(connection -> {
            if (existingId != null) {
                Donations stored = find(connection, existingId);
                if (stored == null || stored.getStatus() != TransactionStatus.DRAFT) {
                    throw new IllegalStateException("Only draft donations can be changed.");
                }
                Db.update(connection, "DELETE FROM donation_items WHERE donation_id = ?", existingId);
                Db.update(connection, "UPDATE donations SET donor_id = ?, donation_date = ?, receiving_location = ?, "
                        + "delivery_reference = ?, donor_representative = ?, notes = ? WHERE donation_id = ?",
                        donor.getDonorId(), date, location, deliveryReference, donorRepresentative, notes, existingId);
            }
            String donationId = existingId != null ? existingId : IdGenerator.next(connection, "DON", true, 4);
            Map<String, Inventory> inventory = new HashMap<>();
            List<Object[]> ids = new ArrayList<>();
            for (Line line : lines) {
                ids.add(new Object[] { line.itemId });
            }
            inventory.putAll(loadInventory(connection, ids));
            Donations donation = rules.createDonation(donationId, donor, date.toString(), location,
                    deliveryReference, actor);
            donation.setWorkspace(workspace);
            donation.setDonorRepresentative(donorRepresentative);
            donation.setNotes(notes);
            for (Line line : lines) {
                Inventory item = inventory.get(line.itemId);
                if (item == null) {
                    throw new IllegalArgumentException("A selected item no longer exists.");
                }
                if (item.isArchived()) {
                    throw new IllegalArgumentException(item.getItemName() + " is archived. Restore it before receiving goods.");
                }
                if (!item.getBarangay().getBarangayId().equals(workspace.getBarangayId())) {
                    throw new IllegalArgumentException("A donation cannot include items of another barangay.");
                }
                rules.addDonationItem(donation, new TransactionItem(item, line.quantity, null,
                        line.expiry == null ? null : line.expiry.toString()), actor);
            }
            if (existingId == null) {
                Db.update(connection, "INSERT INTO donations (donation_id, barangay_id, donor_id, donation_date, "
                        + "receiving_location, delivery_reference, status, created_at, created_by, "
                        + "donor_representative, notes) VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?)", donationId,
                        workspace.getBarangayId(), donor.getDonorId(), date, location, deliveryReference, Time.now(),
                        actor.getAccountId(), donorRepresentative, notes);
            }
            int number = 1;
            for (TransactionItem line : donation.getItems()) {
                Db.update(connection, "INSERT INTO donation_items (donation_id, barangay_id, line_number, "
                        + "inventory_item_id, quantity, batch_reference, expiry_date) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        donationId, workspace.getBarangayId(), number++, line.getInventory().getItemId(),
                        line.getQuantity(), line.getBatchReference(),
                        line.getExpiryDate() == null || line.getExpiryDate().isEmpty() ? null
                                : LocalDate.parse(line.getExpiryDate()));
            }
            if (mode != Mode.DRAFT) {
                submitAndMaybePost(connection, donation, mode == Mode.POST, actor);
            }
            return donation;
        });
    }

    /** Sends a stored draft for approval. */
    public void submitDraft(String donationId, UserAccount actor) throws SQLException {
        Db.transaction(connection -> {
            Donations donation = find(connection, donationId);
            if (donation == null) {
                throw new IllegalStateException("This donation no longer exists.");
            }
            submitAndMaybePost(connection, donation, false, actor);
            return null;
        });
    }

    /** Approves a pending donation: stock increases and the receipt becomes official. */
    public Donations approve(String donationId, UserAccount approver) throws SQLException {
        return Db.transaction(connection -> {
            Donations donation = find(connection, donationId);
            if (donation == null) {
                throw new IllegalStateException("This donation no longer exists.");
            }
            post(connection, donation, approver);
            return donation;
        });
    }

    public void reject(String donationId, String reason, UserAccount approver) throws SQLException {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter the reason for rejecting this donation.");
        }
        Db.transaction(connection -> {
            Donations donation = find(connection, donationId);
            if (donation == null) {
                throw new IllegalStateException("This donation no longer exists.");
            }
            donation.reject(approver);
            Db.update(connection, "UPDATE donations SET status = 'REJECTED', rejection_reason = ? WHERE donation_id = ?",
                    reason.trim(), donationId);
            AuditRepository.record(connection, approver, new AuditRepository.Entry("DONATION_REJECTED",
                    AuditRepository.MODULE_DONATIONS).reference(donationId)
                    .summary(donation.getDonor().getName() + " donation rejected").movement("No stock movement")
                    .reason(reason.trim()).result("Rejected - stock unchanged").donation(donationId)
                    .barangay(donation.getBarangay().getBarangayId()));
            return null;
        });
    }

    private void submitAndMaybePost(Connection connection, Donations donation, boolean post, UserAccount actor)
            throws SQLException {
        donation.submit(actor);
        Db.update(connection, "UPDATE donations SET status = 'PENDING' WHERE donation_id = ?", donation.getDonationId());
        if (post) {
            post(connection, donation, actor);
        } else {
            AuditRepository.record(connection, actor, new AuditRepository.Entry("DONATION_SUBMITTED",
                    AuditRepository.MODULE_DONATIONS).reference(donation.getDonationId())
                    .summary(donation.getDonor().getName() + " donation submitted").movement("No stock movement")
                    .result("Waiting for approval").donation(donation.getDonationId())
                    .barangay(donation.getBarangay().getBarangayId()));
        }
    }

    private void post(Connection connection, Donations donation, UserAccount approver) throws SQLException {
        Map<String, Integer> before = new LinkedHashMap<>();
        for (TransactionItem line : donation.getItems()) {
            before.putIfAbsent(line.getInventory().getItemId(), line.getInventory().getQuantity());
        }
        donation.approve(approver);
        Db.update(connection, "UPDATE donations SET status = 'APPROVED', received_by_account_id = ?, approved_at = ? "
                + "WHERE donation_id = ?", approver.getAccountId(), Time.now(), donation.getDonationId());
        int number = 1;
        for (TransactionItem line : donation.getItems()) {
            String batchId = donation.getDonationId() + "-" + number;
            Db.update(connection, "INSERT INTO inventory_batches (batch_id, inventory_item_id, batch_type, donation_id, "
                    + "donation_line_number, received_date, expiry_date, received_quantity, remaining_quantity) "
                    + "VALUES (?, ?, 'DONATION', ?, ?, ?, ?, ?, ?)", batchId, line.getInventory().getItemId(),
                    donation.getDonationId(), number, LocalDate.parse(donation.getDonationDate()),
                    line.getExpiryDate() == null || line.getExpiryDate().isEmpty() ? null
                            : LocalDate.parse(line.getExpiryDate()),
                    line.getQuantity(), line.getQuantity());
            number++;
        }
        List<String> movement = new ArrayList<>();
        List<String> beforeLines = new ArrayList<>();
        List<String> afterLines = new ArrayList<>();
        Map<String, Boolean> done = new HashMap<>();
        for (TransactionItem line : donation.getItems()) {
            Inventory item = line.getInventory();
            movement.add("+" + Units.quantity(line.getQuantity(), item.getUnit()) + " " + item.getItemName().toLowerCase());
            if (done.put(item.getItemId(), true) == null) {
                beforeLines.add(Units.shortCategory(item.getCategory()) + " " + String.format("%,d", before.get(item.getItemId())));
                afterLines.add(Units.shortCategory(item.getCategory()) + " " + String.format("%,d", item.getQuantity()));
            }
        }
        AuditRepository.record(connection, approver, new AuditRepository.Entry("DONATION_POSTED",
                AuditRepository.MODULE_DONATIONS).reference(donation.getDonationId())
                .summary(donation.getDonor().getName() + " donation").movement(String.join(", ", movement))
                .before(String.join("\n", beforeLines)).after(String.join("\n", afterLines))
                .reason(donation.getDeliveryReference()).result("Posted - stock increased")
                .donation(donation.getDonationId()).barangay(donation.getBarangay().getBarangayId()));
    }
}
