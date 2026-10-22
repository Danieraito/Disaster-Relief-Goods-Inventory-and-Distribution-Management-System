package LIB;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryRepository {
    /** What a saved item edit reports back to the screen. */
    public static final class EditResult {
        public final String reference;
        public final String summary;
        public final String message;

        EditResult(String reference, String summary, String message) {
            this.reference = reference;
            this.summary = summary;
            this.message = message;
        }
    }

    private static final String ITEM_SELECT = "SELECT i.inventory_item_id, i.barangay_id, i.item_name, i.category, "
            + "i.unit, i.low_stock_threshold, i.storage_location, i.is_archived, i.archived_reason, i.created_at, "
            + "i.updated_at, cb.name AS created_by_name, ub.name AS updated_by_name FROM inventory_items i "
            + "LEFT JOIN user_accounts cb ON cb.account_id = i.created_by "
            + "LEFT JOIN user_accounts ub ON ub.account_id = i.updated_by ";

    // ------------------------------------------------------------------ reads

    public List<Inventory> findByBarangay(String barangayId) throws SQLException {
        return findByBarangay(barangayId, false);
    }

    public List<Inventory> findByBarangay(String barangayId, boolean includeArchived) throws SQLException {
        return Db.read(connection -> loadItems(connection,
                "WHERE i.barangay_id = ?" + (includeArchived ? "" : " AND i.is_archived = FALSE"), barangayId));
    }

    public Inventory findById(String itemId) throws SQLException {
        return Db.read(connection -> findById(connection, itemId));
    }

    static Inventory findById(Connection connection, String itemId) throws SQLException {
        List<Inventory> items = loadItems(connection, "WHERE i.inventory_item_id = ?", itemId);
        return items.isEmpty() ? null : items.get(0);
    }

    /** Loads items with all their batches using two queries, however many items there are. */
    static List<Inventory> loadItems(Connection connection, String where, Object... params) throws SQLException {
        Map<String, Barangay> barangays = new HashMap<>();
        List<Inventory> items = Db.list(connection, ITEM_SELECT + where + " ORDER BY i.item_name", row -> {
            String barangayId = row.getString("barangay_id");
            if (!barangays.containsKey(barangayId)) {
                barangays.put(barangayId, BarangayRepository.findById(connection, barangayId));
            }
            Inventory item = new Inventory(row.getString("inventory_item_id"), row.getString("item_name"),
                    row.getString("category"), 0, row.getString("unit"), row.getInt("low_stock_threshold"),
                    row.getString("storage_location"), null, barangays.get(barangayId));
            item.setArchived(row.getBoolean("is_archived"), row.getString("archived_reason"));
            item.setCreated(Db.dateTime(row, "created_at"), row.getString("created_by_name"));
            item.setUpdated(Db.dateTime(row, "updated_at"), row.getString("updated_by_name"));
            return item;
        }, params);
        if (items.isEmpty()) {
            return items;
        }
        Map<String, Inventory> byId = new HashMap<>();
        for (Inventory item : items) {
            byId.put(item.getItemId(), item);
        }
        Db.list(connection, "SELECT b.batch_id, b.inventory_item_id, b.received_date, b.expiry_date, "
                + "b.received_quantity, b.remaining_quantity FROM inventory_batches b "
                + "JOIN inventory_items i ON i.inventory_item_id = b.inventory_item_id " + where
                + " ORDER BY b.received_date, b.batch_id", row -> {
                    Inventory item = byId.get(row.getString("inventory_item_id"));
                    int received = row.getInt("received_quantity");
                    int remaining = row.getInt("remaining_quantity");
                    LocalDate receivedDate = Db.date(row, "received_date");
                    LocalDate expiry = Db.date(row, "expiry_date");
                    String batchId = row.getString("batch_id");
                    item.addBatch(batchId, received, receivedDate == null ? "" : receivedDate.toString(),
                            expiry == null ? null : expiry.toString());
                    if (remaining < received) {
                        item.removeBatchStock(batchId, received - remaining);
                    }
                    return null;
                }, params);
        return items;
    }

    /** An existing, non-archived item of this barangay with the same name and unit, or null. */
    public Inventory findDuplicate(String barangayId, String name, String unit) throws SQLException {
        return Db.read(connection -> {
            List<Inventory> matches = loadItems(connection, "WHERE i.barangay_id = ? AND LOWER(i.item_name) = LOWER(?) "
                    + "AND LOWER(i.unit) = LOWER(?) AND i.is_archived = FALSE", barangayId, name.trim(), unit.trim());
            return matches.isEmpty() ? null : matches.get(0);
        });
    }

    /** True when the item has donation, release or adjustment records (its unit can no longer change). */
    public boolean hasMovements(String itemId) throws SQLException {
        return Db.read(connection -> hasMovements(connection, itemId));
    }

    private static boolean hasMovements(Connection connection, String itemId) throws SQLException {
        return Db.intValue(connection, "SELECT (SELECT COUNT(*) FROM donation_items WHERE inventory_item_id = ?) "
                + "+ (SELECT COUNT(*) FROM distribution_items WHERE inventory_item_id = ?) "
                + "+ (SELECT COUNT(*) FROM stock_adjustments WHERE inventory_item_id = ?)", itemId, itemId, itemId) > 0;
    }

    // ------------------------------------------------------------------ writes

    /**
     * Old entry point kept for compatibility: saves a new item and its opening batch.
     * New code should use {@link #createItem}.
     */
    public void save(Inventory item) throws SQLException {
        if (item == null || item.getBarangay() == null) {
            throw new IllegalArgumentException("Inventory must belong to a barangay before it can be saved.");
        }
        String openingBatchId = "OPENING-" + item.getItemId();
        for (InventoryBatch batch : item.getBatches()) {
            if (!batch.getBatchId().equals(openingBatchId)) {
                throw new IllegalArgumentException(
                        "Only opening stock can be saved here; donation batches must be saved with their donation.");
            }
        }
        Db.transaction(connection -> {
            BarangayRepository.save(connection, item.getBarangay());
            saveItem(connection, item, null);
            for (InventoryBatch batch : item.getBatches()) {
                saveOpeningBatch(connection, item, batch);
            }
            return null;
        });
    }

    /** Creates an item with its opening stock batch, a readable ITM-nnn reference and an audit row. */
    public Inventory createItem(String name, String category, int openingQuantity, String unit, int threshold,
            String storageLocation, String expiryDate, String note, Barangay barangay, UserAccount actor)
            throws SQLException {
        InventoryController rules = new InventoryController();
        return Db.transaction(connection -> {
            String itemId = IdGenerator.next(connection, "ITM", false, 3);
            Inventory item = rules.createInventoryItem(itemId, name.trim(), category, openingQuantity, unit.trim(),
                    threshold, storageLocation.trim(), expiryDate, barangay, actor);
            BarangayRepository.save(connection, barangay);
            saveItem(connection, item, actor);
            for (InventoryBatch batch : item.getBatches()) {
                saveOpeningBatch(connection, item, batch);
            }
            AuditRepository.record(connection, actor, new AuditRepository.Entry("ITEM_CREATED",
                    AuditRepository.MODULE_INVENTORY).reference(itemId)
                    .summary(item.getItemName() + " added to inventory")
                    .movement(openingQuantity > 0 ? "+" + openingQuantity + " " + item.getUnit() + " opening stock"
                            : "No stock movement")
                    .after(openingQuantity + " " + item.getUnit()).reason(note).result("Item record created")
                    .item(itemId).barangay(barangay.getBarangayId()));
            return item;
        });
    }

    /** Changes the item details (never the stock) and logs the previous and new values with the reason. */
    public EditResult updateDetails(Inventory item, String name, String category, String unit, int threshold,
            String storage, String reason, UserAccount actor) throws SQLException {
        return Db.transaction(connection -> {
            Inventory current = findById(connection, item.getItemId());
            if (current == null) {
                throw new IllegalStateException("This item no longer exists.");
            }
            List<String> changed = new ArrayList<>();
            List<String> before = new ArrayList<>();
            List<String> after = new ArrayList<>();
            if (!current.getItemName().equals(name.trim())) {
                changed.add("Name");
                before.add(current.getItemName());
                after.add(name.trim());
            }
            if (!current.getCategory().equals(category)) {
                changed.add("Category");
                before.add(current.getCategory());
                after.add(category);
            }
            if (!current.getUnit().equals(unit.trim())) {
                if (hasMovements(connection, current.getItemId())) {
                    throw new IllegalStateException("Unit cannot change while there are stock movements.");
                }
                changed.add("Unit");
                before.add(current.getUnit());
                after.add(unit.trim());
            }
            if (current.getLowStockThreshold() != threshold) {
                changed.add("Threshold");
                before.add(current.getLowStockThreshold() + " " + current.getUnit());
                after.add(threshold + " " + unit.trim());
            }
            String oldStorage = current.getStorageLocation() == null ? "" : current.getStorageLocation();
            if (!oldStorage.equals(storage.trim())) {
                changed.add("Storage");
                before.add(oldStorage.isEmpty() ? "(none)" : oldStorage);
                after.add(storage.trim());
            }
            if (changed.isEmpty()) {
                throw new IllegalArgumentException("Nothing was changed. Update a field before saving.");
            }
            Db.update(connection, "UPDATE inventory_items SET item_name = ?, category = ?, unit = ?, "
                    + "low_stock_threshold = ?, storage_location = ?, updated_at = ?, updated_by = ? "
                    + "WHERE inventory_item_id = ?", name.trim(), category, unit.trim(), threshold, storage.trim(),
                    Time.now(), actor.getAccountId(), current.getItemId());
            String reference = IdGenerator.next(connection, "EDIT", true, 4);
            String summary = String.join(", ", changed) + " updated";
            AuditRepository.record(connection, actor, new AuditRepository.Entry("ITEM_EDITED",
                    AuditRepository.MODULE_INVENTORY).reference(reference).summary(current.getItemName() + " · "
                    + summary).movement("No stock movement").before(String.join("\n", before))
                    .after(String.join("\n", after)).reason(reason).result("Stock stays " + current.getQuantity()
                    + " " + current.getUnit()).item(current.getItemId())
                    .barangay(current.getBarangay().getBarangayId()));
            String message = changed.size() == 1 && changed.get(0).equals("Threshold")
                    ? "Low-stock threshold updated from " + before.get(0).replace(" " + current.getUnit(), "")
                            + " to " + threshold + " " + unit.trim() + " by " + actor.getName() + " at "
                            + Time.time(Time.now()) + "."
                    : summary + " by " + actor.getName() + " at " + Time.time(Time.now()) + ".";
            return new EditResult(reference, summary, message);
        });
    }

    /** Posts a damage, spoilage or correction entry against one batch, atomically with its audit row. */
    public StockAdjustment adjustStock(String itemId, String batchId, StockAdjustment.Type type,
            StockAdjustment.Reason reason, int quantity, String evidence, LocalDate adjustedOn, UserAccount actor)
            throws SQLException {
        return Db.transaction(connection -> {
            Db.list(connection, "SELECT batch_id FROM inventory_batches WHERE inventory_item_id = ? FOR UPDATE",
                    row -> row.getString(1), itemId);
            Inventory item = findById(connection, itemId);
            if (item == null) {
                throw new IllegalStateException("This item no longer exists.");
            }
            if (item.isArchived()) {
                throw new IllegalStateException("Archived items cannot be adjusted. Restore the item first.");
            }
            StockAdjustment adjustment = new StockAdjustment(null, item, batchId, type, reason, quantity,
                    evidence, adjustedOn);
            int before = item.getQuantity();
            item.applyAdjustment(adjustment);
            int after = item.getQuantity();
            InventoryBatch batch = item.findBatch(batchId);
            Db.update(connection, "UPDATE inventory_batches SET remaining_quantity = ?, received_quantity = ? "
                    + "WHERE batch_id = ? AND inventory_item_id = ?", batch.getRemainingQuantity(),
                    batch.getReceivedQuantity(), batch.getBatchId(), item.getItemId());
            adjustment.setAdjustmentId(IdGenerator.next(connection, "ADJ", true, 4));
            LocalDateTime now = Time.now();
            Db.update(connection, "INSERT INTO stock_adjustments (adjustment_id, barangay_id, inventory_item_id, "
                    + "batch_id, adjustment_type, reason_category, quantity, before_quantity, after_quantity, "
                    + "reason, adjusted_on, created_at, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    adjustment.getAdjustmentId(), item.getBarangay().getBarangayId(), item.getItemId(),
                    batch.getBatchId(), type, reason, quantity, before, after, adjustment.getEvidence(),
                    adjustment.getAdjustedOn(), now, actor.getAccountId());
            adjustment.recordResult(before, after, now, actor.getName());
            String sign = type == StockAdjustment.Type.DECREASE ? "-" : "+";
            AuditRepository.record(connection, actor, new AuditRepository.Entry("STOCK_ADJUSTED",
                    AuditRepository.MODULE_INVENTORY).reference(adjustment.getAdjustmentId())
                    .summary(reason.label() + " · " + item.getItemName())
                    .movement(sign + quantity + " " + item.getUnit() + " · " + before + " -> " + after)
                    .before(before + " " + item.getUnit()).after(after + " " + item.getUnit())
                    .reason(adjustment.getEvidence()).result(reason.label() + " posted · batch " + batch.getBatchId())
                    .item(item.getItemId()).barangay(item.getBarangay().getBarangayId()));
            return adjustment;
        });
    }

    public void setArchived(String itemId, boolean archived, String reason, UserAccount actor) throws SQLException {
        Db.transaction(connection -> {
            Inventory item = findById(connection, itemId);
            if (item == null) {
                throw new IllegalStateException("This item no longer exists.");
            }
            Db.update(connection, "UPDATE inventory_items SET is_archived = ?, archived_reason = ?, updated_at = ?, "
                    + "updated_by = ? WHERE inventory_item_id = ?", archived, archived ? reason : null, Time.now(),
                    actor.getAccountId(), itemId);
            AuditRepository.record(connection, actor, new AuditRepository.Entry(
                    archived ? "ITEM_ARCHIVED" : "ITEM_RESTORED", AuditRepository.MODULE_INVENTORY)
                    .reference(itemId).summary(item.getItemName() + (archived ? " archived" : " restored"))
                    .movement("No stock movement").reason(reason)
                    .result(archived ? "Hidden from new transactions; history kept" : "Available for transactions")
                    .item(itemId).barangay(item.getBarangay().getBarangayId()));
            return null;
        });
    }

    /** Deletes an unused, zero-stock item. Anything else must be archived instead. */
    public void deleteItem(String itemId, String reason, UserAccount actor) throws SQLException {
        Db.transaction(connection -> {
            Inventory item = findById(connection, itemId);
            if (item == null) {
                throw new IllegalStateException("This item no longer exists.");
            }
            if (item.getQuantity() > 0 || hasMovements(connection, itemId)) {
                throw new IllegalStateException("A stocked or used item cannot be deleted. Archive it instead.");
            }
            Db.update(connection, "DELETE FROM inventory_batches WHERE inventory_item_id = ?", itemId);
            Db.update(connection, "DELETE FROM inventory_items WHERE inventory_item_id = ?", itemId);
            AuditRepository.record(connection, actor, new AuditRepository.Entry("ITEM_DELETED",
                    AuditRepository.MODULE_INVENTORY).reference(itemId)
                    .summary(item.getItemName() + " · " + itemId + " deleted").movement("No stock movement")
                    .reason(reason).result("Unused zero-stock record removed")
                    .barangay(item.getBarangay().getBarangayId()));
            return null;
        });
    }

    // ------------------------------------------------------------------ history

    /** Opening stock, donations, releases, adjustments and record edits of one item, newest first. */
    public List<StockMovement> findMovements(String itemId) throws SQLException {
        return Db.read(connection -> {
            Inventory item = findById(connection, itemId);
            if (item == null) {
                return new ArrayList<StockMovement>();
            }
            String unit = item.getUnit();
            List<StockMovement> events = new ArrayList<>();

            for (StockMovement opening : Db.list(connection, "SELECT b.batch_id, b.received_quantity, b.received_date "
                    + "FROM inventory_batches b WHERE b.inventory_item_id = ? AND b.batch_type = 'OPENING'", row -> {
                        LocalDateTime when = item.getCreatedAt() != null ? item.getCreatedAt()
                                : Db.date(row, "received_date").atStartOfDay();
                        return new StockMovement(StockMovement.Kind.OPENING, itemId, when, row.getInt("received_quantity"),
                                "Opening stock · +" + row.getInt("received_quantity") + " " + unit,
                                "Opening count", item.getCreatedByName(), unit, null, null, null);
                    }, itemId)) {
                events.add(opening);
            }

            events.addAll(Db.list(connection, "SELECT d.donation_id, d.donation_date, d.approved_at, di.quantity, "
                    + "dn.name AS donor_name, ap.name AS approver_name FROM donation_items di "
                    + "JOIN donations d ON d.donation_id = di.donation_id "
                    + "JOIN donors dn ON dn.donor_id = d.donor_id "
                    + "LEFT JOIN user_accounts ap ON ap.account_id = d.received_by_account_id "
                    + "WHERE di.inventory_item_id = ? AND d.status = 'APPROVED'", row -> {
                        LocalDateTime when = Db.dateTime(row, "approved_at");
                        if (when == null) {
                            when = Db.date(row, "donation_date").atStartOfDay();
                        }
                        return new StockMovement(StockMovement.Kind.DONATION, row.getString("donation_id"), when,
                                row.getInt("quantity"), "Donation · +" + row.getInt("quantity") + " " + unit,
                                row.getString("donor_name"), row.getString("approver_name"), unit, null, null, null);
                    }, itemId));

            events.addAll(Db.list(connection, "SELECT ds.distribution_id, ds.distribution_date, ds.approved_at, "
                    + "dti.quantity, hh.household_name, ec.name AS center_name, dis.name AS disaster_name, "
                    + "rl.name AS releaser_name FROM distribution_items dti "
                    + "JOIN distributions ds ON ds.distribution_id = dti.distribution_id "
                    + "JOIN disasters dis ON dis.disaster_id = ds.disaster_id "
                    + "LEFT JOIN households hh ON hh.household_id = ds.beneficiary_id "
                    + "LEFT JOIN evacuation_centers ec ON ec.center_id = ds.evacuation_center_id "
                    + "LEFT JOIN user_accounts rl ON rl.account_id = ds.released_by_account_id "
                    + "WHERE dti.inventory_item_id = ? AND ds.status = 'APPROVED'", row -> {
                        LocalDateTime when = Db.dateTime(row, "approved_at");
                        if (when == null) {
                            when = Db.date(row, "distribution_date").atStartOfDay();
                        }
                        String recipient = row.getString("household_name") != null ? row.getString("household_name")
                                : row.getString("center_name");
                        return new StockMovement(StockMovement.Kind.RELEASE, row.getString("distribution_id"), when,
                                -row.getInt("quantity"), "Release · −" + row.getInt("quantity") + " " + unit,
                                recipient + " · " + row.getString("disaster_name"), row.getString("releaser_name"),
                                unit, null, null, null);
                    }, itemId));

            events.addAll(Db.list(connection, "SELECT a.adjustment_id, a.adjustment_type, a.reason_category, "
                    + "a.quantity, a.reason, a.created_at, u.name AS actor_name FROM stock_adjustments a "
                    + "LEFT JOIN user_accounts u ON u.account_id = a.created_by WHERE a.inventory_item_id = ?", row -> {
                        boolean decrease = "DECREASE".equals(row.getString("adjustment_type"));
                        int quantity = row.getInt("quantity");
                        StockAdjustment.Reason reason = StockAdjustment.Reason.valueOf(row.getString("reason_category"));
                        return new StockMovement(StockMovement.Kind.ADJUSTMENT, row.getString("adjustment_id"),
                                Db.dateTime(row, "created_at"), decrease ? -quantity : quantity,
                                reason.label() + " · " + (decrease ? "−" : "+") + quantity + " " + unit,
                                row.getString("reason"), row.getString("actor_name"), unit, null, null, reason.name());
                    }, itemId));

            for (Audits audit : new AuditRepository().findFor("inventory_item_id", itemId, "ITEM_EDITED")) {
                events.add(new StockMovement(StockMovement.Kind.EDIT, audit.getReference(), audit.getWhen(), 0,
                        "Record edit", audit.getReason(), audit.getActorName(), unit, audit.getBeforeValue(),
                        audit.getAfterValue(), null));
            }

            events.sort(Comparator.comparing((StockMovement event) -> event.when)
                    .thenComparingInt(event -> event.kind.ordinal()));
            int balance = 0;
            for (StockMovement event : events) {
                event.before = balance;
                balance += event.delta;
                event.after = balance;
            }
            events.sort(Comparator.comparing((StockMovement event) -> event.when).reversed()
                    .thenComparing(Comparator.comparingInt((StockMovement event) -> event.kind.ordinal()).reversed()));
            return events;
        });
    }

    // ------------------------------------------------------------------ internals

    private static void saveItem(Connection connection, Inventory item, UserAccount actor) throws SQLException {
        String sql = "INSERT INTO inventory_items (inventory_item_id, barangay_id, item_name, category, "
                + "unit, low_stock_threshold, storage_location, created_at, created_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), category = VALUES(category), "
                + "unit = VALUES(unit), low_stock_threshold = VALUES(low_stock_threshold), "
                + "storage_location = VALUES(storage_location)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Db.bind(statement, item.getItemId(), item.getBarangay().getBarangayId(), item.getItemName(),
                    item.getCategory(), item.getUnit(), item.getLowStockThreshold(), item.getStorageLocation(),
                    Time.now(), actor == null ? null : actor.getAccountId());
            statement.executeUpdate();
        }
    }

    private static void saveOpeningBatch(Connection connection, Inventory item, InventoryBatch batch)
            throws SQLException {
        String sql = "INSERT INTO inventory_batches (batch_id, inventory_item_id, batch_type, donation_id, "
                + "donation_line_number, received_date, expiry_date, received_quantity, remaining_quantity) "
                + "VALUES (?, ?, 'OPENING', NULL, NULL, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE batch_id = batch_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, batch.getBatchId());
            statement.setString(2, item.getItemId());
            statement.setDate(3, parseDateOrToday(batch.getReceivedDate()));
            setNullableDate(statement, 4, batch.getExpiryDate());
            statement.setInt(5, batch.getReceivedQuantity());
            statement.setInt(6, batch.getRemainingQuantity());
            statement.executeUpdate();
        }
    }

    private static Date parseDateOrToday(String date) {
        return date == null || date.trim().isEmpty()
                ? Date.valueOf(Time.today())
                : Date.valueOf(LocalDate.parse(date));
    }

    private static void setNullableDate(PreparedStatement statement, int parameter, String date)
            throws SQLException {
        if (date == null || date.trim().isEmpty()) {
            statement.setNull(parameter, Types.DATE);
        } else {
            statement.setDate(parameter, Date.valueOf(LocalDate.parse(date)));
        }
    }
}
