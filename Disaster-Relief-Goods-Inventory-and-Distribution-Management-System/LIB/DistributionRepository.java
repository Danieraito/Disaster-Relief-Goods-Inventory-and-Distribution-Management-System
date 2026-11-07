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

public class DistributionRepository {
    public enum Mode { DRAFT, SUBMIT, POST }

    public static final class Line {
        public final String itemId;
        public final String batchId;
        public final int quantity;

        public Line(String itemId, String batchId, int quantity) {
            this.itemId = itemId;
            this.batchId = batchId;
            this.quantity = quantity;
        }
    }

    /** Everything typed on the New distribution form. */
    public static final class Request {
        public Disaster disaster;
        public String householdId;
        public String centerId;
        public LocalDate date = Time.today();
        public String acknowledgment = "";
        public String notes = "";
        public final List<Line> lines = new ArrayList<>();
    }

    /** Why a release cannot go ahead, with a small table of the lines involved. */
    public static final class Problem {
        public enum Kind { STOCK, DUPLICATE, EXPIRED, OTHER }

        public final Kind kind;
        public final String title;
        public final String message;
        public final String existingId;
        public final String existingDetail;
        /** Table rows: item, requested/already released, available, shortfall, result. */
        public final List<String[]> rows = new ArrayList<>();

        Problem(Kind kind, String title, String message, String existingId, String existingDetail) {
            this.kind = kind;
            this.title = title;
            this.message = message;
            this.existingId = existingId;
            this.existingDetail = existingDetail;
        }
    }

    public static class BlockedException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        public final transient Problem problem;

        BlockedException(Problem problem) {
            super(problem.title + ": " + problem.message);
            this.problem = problem;
        }
    }

    public static final class Row {
        public String distributionId;
        public LocalDate date;
        public LocalDateTime recordedAt;
        public TransactionStatus status;
        public String disasterId;
        public String disasterName;
        public String centerName;
        public String barangayName;
        public String representative = "";
        public String representativeId;
        public String officer = "";
        public String createdBy = "";
        public String receiptReference = "";
        public List<String> goods = new ArrayList<>();

        public String recipient() {
            return centerName != null ? centerName : representative + " household";
        }
    }

    public static final class Filter {
        public String disasterId;
        public String householdId;
        public String text;
        public LocalDate from;
        public LocalDate to;
        public TransactionStatus status;
        public String itemId;
        public int limit = 200;
    }

    public static final class Movement {
        public final String itemName;
        public final String unit;
        public final String batchId;
        public final LocalDate expiry;
        public final int quantity;
        public final int before;
        public final int after;

        Movement(String itemName, String unit, String batchId, LocalDate expiry, int quantity, int before, int after) {
            this.itemName = itemName;
            this.unit = unit;
            this.batchId = batchId;
            this.expiry = expiry;
            this.quantity = quantity;
            this.before = before;
            this.after = after;
        }
    }

    /** CLM-0188 for DST-2026-0188: the number printed on the paper claim receipt. */
    public static String claimReference(String distributionId) {
        int dash = distributionId.lastIndexOf('-');
        return "CLM-" + (dash >= 0 ? distributionId.substring(dash + 1) : distributionId);
    }

    // ------------------------------------------------------------------ list / read

    public List<Row> list(String barangayId, Filter filter) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT ds.distribution_id, ds.distribution_date, ds.created_at, "
                + "ds.approved_at, ds.status, ds.receipt_reference, dis.disaster_id, dis.name AS disaster_name, "
                + "ec.name AS center_name, bg.name AS barangay_name, hh.household_name AS rep_name, "
                + "hh.household_id AS rep_id, rl.name AS officer, cr.name AS creator, "
                + "(SELECT GROUP_CONCAT(CONCAT(di.quantity, '|', i.unit, '|', i.item_name) ORDER BY di.line_number "
                + "SEPARATOR ';;') FROM distribution_items di JOIN inventory_items i ON i.inventory_item_id = "
                + "di.inventory_item_id WHERE di.distribution_id = ds.distribution_id) AS item_lines "
                + "FROM distributions ds JOIN disasters dis ON dis.disaster_id = ds.disaster_id "
                + "JOIN barangays bg ON bg.barangay_id = ds.barangay_id "
                + "LEFT JOIN evacuation_centers ec ON ec.center_id = ds.evacuation_center_id "
                + "LEFT JOIN households hh ON hh.household_id = ds.beneficiary_id "
                + "LEFT JOIN user_accounts rl ON rl.account_id = ds.released_by_account_id "
                + "LEFT JOIN user_accounts cr ON cr.account_id = ds.created_by WHERE ds.barangay_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(barangayId);
        if (filter.disasterId != null) {
            sql.append(" AND ds.disaster_id = ?");
            params.add(filter.disasterId);
        }
        if (filter.householdId != null) {
            sql.append(" AND ds.beneficiary_id = ?");
            params.add(filter.householdId);
        }
        if (filter.status != null) {
            sql.append(" AND ds.status = ?");
            params.add(filter.status);
        }
        if (filter.from != null) {
            sql.append(" AND ds.distribution_date >= ?");
            params.add(filter.from);
        }
        if (filter.to != null) {
            sql.append(" AND ds.distribution_date <= ?");
            params.add(filter.to);
        }
        if (filter.itemId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM distribution_items x WHERE x.distribution_id = ds.distribution_id "
                    + "AND x.inventory_item_id = ?)");
            params.add(filter.itemId);
        }
        if (filter.text != null && !filter.text.trim().isEmpty()) {
            sql.append(" AND (ds.distribution_id LIKE ? OR ec.name LIKE ? OR hh.household_name LIKE ? "
                    + "OR bg.name LIKE ? OR ds.receipt_reference LIKE ?)");
            String like = "%" + filter.text.trim() + "%";
            for (int index = 0; index < 5; index++) {
                params.add(like);
            }
        }
        sql.append(" ORDER BY COALESCE(ds.approved_at, ds.created_at) DESC, ds.distribution_id DESC LIMIT ")
                .append(Math.max(1, filter.limit));
        return Db.read(connection -> Db.list(connection, sql.toString(), row -> {
            Row item = new Row();
            item.distributionId = row.getString("distribution_id");
            item.date = Db.date(row, "distribution_date");
            item.recordedAt = Db.dateTime(row, "approved_at") != null ? Db.dateTime(row, "approved_at")
                    : Db.dateTime(row, "created_at");
            item.status = TransactionStatus.valueOf(row.getString("status"));
            item.disasterId = row.getString("disaster_id");
            item.disasterName = row.getString("disaster_name");
            item.centerName = row.getString("center_name");
            item.barangayName = row.getString("barangay_name");
            item.representative = Db.text(row, "rep_name");
            item.representativeId = row.getString("rep_id");
            item.officer = Db.text(row, "officer");
            item.createdBy = Db.text(row, "creator");
            item.receiptReference = Db.text(row, "receipt_reference");
            String lines = row.getString("item_lines");
            if (lines != null) {
                for (String line : lines.split(";;")) {
                    String[] part = line.split("\\|", 3);
                    item.goods.add(Units.quantity(Integer.parseInt(part[0]), part[1]) + " " + part[2]);
                }
            }
            return item;
        }, params.toArray()));
    }

    public Distribution find(String distributionId) throws SQLException {
        return Db.read(connection -> find(connection, distributionId));
    }

    private static Distribution find(Connection connection, String distributionId) throws SQLException {
        Object[] header = Db.one(connection, "SELECT ds.disaster_id, ds.beneficiary_id, ds.evacuation_center_id, "
                + "ds.distribution_date, ds.release_location, ds.receipt_reference, ds.status, ds.created_at, "
                + "ds.approved_at, ds.acknowledgment_method, ds.notes, ds.rejection_reason, ds.released_by_account_id, "
                + "cr.name AS creator FROM distributions ds LEFT JOIN user_accounts cr ON cr.account_id = ds.created_by "
                + "WHERE ds.distribution_id = ?", row -> new Object[] { row.getString("disaster_id"),
                    row.getString("beneficiary_id"), row.getString("evacuation_center_id"),
                    Db.date(row, "distribution_date").toString(), row.getString("release_location"),
                    row.getString("receipt_reference"), row.getString("status"), Db.dateTime(row, "created_at"),
                    Db.dateTime(row, "approved_at"), row.getString("acknowledgment_method"), row.getString("notes"),
                    row.getString("rejection_reason"), row.getString("released_by_account_id"),
                    row.getString("creator") }, distributionId);
        if (header == null) {
            return null;
        }
        Disaster disaster = DisasterRepository.findById(connection, (String) header[0]);
        Beneficiaries household = header[1] == null ? null
                : BeneficiaryRepository.findById(connection, (String) header[1], disaster);
        EvacuationCenter center = EvacuationCenterRepository.findById(connection, (String) header[2],
                disaster.getBarangay());
        Distribution distribution = center == null
                ? new Distribution(distributionId, (String) header[3], disaster, household, (String) header[4],
                        (String) header[5])
                : new Distribution(distributionId, (String) header[3], disaster, household, center,
                        (String) header[4], (String) header[5]);
        distribution.setAcknowledgmentMethod((String) header[9]);
        distribution.setNotes((String) header[10]);
        List<Object[]> lines = Db.list(connection, "SELECT inventory_item_id, batch_id, quantity FROM "
                + "distribution_items WHERE distribution_id = ? ORDER BY line_number", row -> new Object[] {
                    row.getString("inventory_item_id"), row.getString("batch_id"), row.getInt("quantity") },
                distributionId);
        Map<String, Inventory> inventory = loadInventory(connection, ids(lines));
        for (Object[] line : lines) {
            distribution.addItem(new TransactionItem(inventory.get(line[0]), (Integer) line[2], (String) line[1], null));
        }
        UserAccount releaser = header[12] == null ? null
                : new UserRepository().findByIdWith(connection, (String) header[12]);
        distribution.restoreState(TransactionStatus.valueOf((String) header[6]), releaser, (LocalDateTime) header[7],
                (String) header[13], (LocalDateTime) header[8], (String) header[11]);
        return distribution;
    }

    private static List<String> ids(List<Object[]> lines) {
        List<String> ids = new ArrayList<>();
        for (Object[] line : lines) {
            if (!ids.contains((String) line[0])) {
                ids.add((String) line[0]);
            }
        }
        return ids;
    }

    private static Map<String, Inventory> loadInventory(Connection connection, List<String> ids) throws SQLException {
        Map<String, Inventory> inventory = new HashMap<>();
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

    /** Batch, quantity and stock before/after for each line of a confirmed release (for the receipt). */
    public List<Movement> movements(Distribution distribution) throws SQLException {
        InventoryRepository repository = new InventoryRepository();
        Map<String, List<StockMovement>> byItem = new HashMap<>();
        List<Movement> result = new ArrayList<>();
        Map<String, Integer> used = new HashMap<>();
        for (TransactionItem line : distribution.getItems()) {
            Inventory item = line.getInventory();
            List<StockMovement> history = byItem.get(item.getItemId());
            if (history == null) {
                history = new ArrayList<>();
                for (StockMovement movement : repository.findMovements(item.getItemId())) {
                    if (distribution.getDistributionId().equals(movement.reference)) {
                        history.add(0, movement);
                    }
                }
                byItem.put(item.getItemId(), history);
            }
            int position = used.merge(item.getItemId(), 1, Integer::sum) - 1;
            StockMovement match = position < history.size() ? history.get(position) : null;
            InventoryBatch batch = item.findBatch(line.getBatchReference());
            result.add(new Movement(item.getItemName(), item.getUnit(), line.getBatchReference(),
                    batch == null ? null : batch.getExpiryLocalDate(), line.getQuantity(),
                    match == null ? item.getQuantity() + line.getQuantity() : match.before,
                    match == null ? item.getQuantity() : match.after));
        }
        return result;
    }

    // ------------------------------------------------------------------ validation

    /** Returns the reason a release is blocked, or null when it may go ahead. */
    public Problem check(Request request) throws SQLException {
        return Db.read(connection -> check(connection, request));
    }

    private static Problem check(Connection connection, Request request) throws SQLException {
        Map<String, Inventory> inventory = loadInventory(connection, requestIds(request));
        Map<String, Integer> requested = new LinkedHashMap<>();
        Map<String, Integer> perBatch = new LinkedHashMap<>();
        for (Line line : request.lines) {
            Inventory item = inventory.get(line.itemId);
            if (item == null) {
                return new Problem(Problem.Kind.OTHER, "Release blocked", "A selected item no longer exists.", null, null);
            }
            requested.merge(line.itemId, line.quantity, Integer::sum);
            perBatch.merge(line.itemId + "|" + line.batchId, line.quantity, Integer::sum);
        }
        LocalDate asOf = request.date == null ? Time.today() : request.date;
        for (Map.Entry<String, Integer> entry : perBatch.entrySet()) {
            String[] key = entry.getKey().split("\\|", 2);
            Inventory item = inventory.get(key[0]);
            InventoryBatch batch = item.findBatch(key[1]);
            if (batch == null) {
                return new Problem(Problem.Kind.OTHER, "Release blocked",
                        "Choose a stock batch for " + item.getItemName() + ".", null, null);
            }
            if (batch.isExpired(asOf)) {
                Problem problem = new Problem(Problem.Kind.EXPIRED, "Release blocked: expired goods",
                        "Batch " + batch.getBatchId() + " of " + item.getItemName() + " expired on "
                        + Time.date(batch.getExpiryLocalDate()) + ". Expired goods are quarantined and can never be released.",
                        null, null);
                problem.rows.add(new String[] { item.getItemName(), fmt(entry.getValue()), "0", fmt(entry.getValue()), "Blocked" });
                return problem;
            }
        }
        boolean blocked = false;
        Problem stock = null;
        List<String[]> table = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : requested.entrySet()) {
            Inventory item = inventory.get(entry.getKey());
            int available = item.getAvailableQuantity(asOf);
            int shortfall = Math.max(0, entry.getValue() - available);
            table.add(new String[] { item.getItemName(), Units.quantity(entry.getValue(), item.getUnit()),
                Units.quantity(available, item.getUnit()), shortfall == 0 ? "0" : Units.quantity(shortfall, item.getUnit()),
                shortfall == 0 ? "Available" : "Blocked" });
            if (shortfall > 0 && stock == null) {
                stock = new Problem(Problem.Kind.STOCK, "Release blocked: insufficient stock",
                        "Cannot release " + Units.quantity(entry.getValue(), item.getUnit()) + " of "
                        + item.getItemName().toLowerCase() + ". Only " + Units.quantity(available, item.getUnit())
                        + " are available. The request exceeds stock by " + Units.quantity(shortfall, item.getUnit())
                        + ". No goods have been deducted.", null, null);
            }
        }
        if (stock != null) {
            stock.rows.addAll(table);
            return stock;
        }
        for (Map.Entry<String, Integer> entry : perBatch.entrySet()) {
            String[] key = entry.getKey().split("\\|", 2);
            Inventory item = inventory.get(key[0]);
            InventoryBatch batch = item.findBatch(key[1]);
            if (entry.getValue() > batch.getRemainingQuantity()) {
                Problem problem = new Problem(Problem.Kind.STOCK, "Release blocked: batch too small",
                        "Batch " + batch.getBatchId() + " of " + item.getItemName() + " only has "
                        + Units.quantity(batch.getRemainingQuantity(), item.getUnit()) + ". Choose another batch or add "
                        + "another line for the rest.", null, null);
                problem.rows.add(new String[] { item.getItemName(), Units.quantity(entry.getValue(), item.getUnit()),
                    Units.quantity(batch.getRemainingQuantity(), item.getUnit()),
                    Units.quantity(entry.getValue() - batch.getRemainingQuantity(), item.getUnit()), "Blocked" });
                return problem;
            }
        }
        return duplicate(connection, request, inventory, requested);
    }

    private static Problem duplicate(Connection connection, Request request, Map<String, Inventory> inventory,
            Map<String, Integer> requested) throws SQLException {
        StringBuilder marks = new StringBuilder();
        List<Object> params = new ArrayList<>();
        params.add(request.disaster.getDisasterId());
        params.add(request.centerId);
        params.add(request.householdId);
        int count = 0;
        for (String itemId : requested.keySet()) {
            marks.append(count++ == 0 ? "?" : ", ?");
            params.add(itemId);
        }
        List<Object[]> found = Db.list(connection, "SELECT ds.distribution_id, ds.distribution_date, ds.approved_at, "
                + "ds.created_at, hh.household_name AS rep, rl.name AS officer, di.inventory_item_id, di.quantity "
                + "FROM distributions ds JOIN distribution_items di ON di.distribution_id = ds.distribution_id "
                + "LEFT JOIN households hh ON hh.household_id = ds.beneficiary_id "
                + "LEFT JOIN user_accounts rl ON rl.account_id = ds.released_by_account_id "
                + "WHERE ds.disaster_id = ? AND ds.status IN ('PENDING', 'APPROVED') "
                + "AND ds.evacuation_center_id <=> ? AND ds.beneficiary_id <=> ? AND di.inventory_item_id IN ("
                + marks + ") ORDER BY ds.distribution_date DESC, ds.distribution_id DESC", row -> new Object[] {
                    row.getString("distribution_id"), Db.dateTime(row, "approved_at") != null
                            ? Db.dateTime(row, "approved_at") : Db.dateTime(row, "created_at"),
                    row.getString("rep"), row.getString("officer"), row.getString("inventory_item_id"),
                    row.getInt("quantity") }, params.toArray());
        if (found.isEmpty()) {
            return null;
        }
        Object[] first = found.get(0);
        boolean center = request.centerId != null;
        Problem problem = new Problem(Problem.Kind.DUPLICATE,
                center ? "Release blocked: duplicate center request" : "Release blocked: household already assisted",
                (center ? "This center request already has a release. " : "This household already received these items. ")
                + "It was fulfilled under " + first[0] + ". Do not confirm the same request again. "
                + "No stock was deducted.",
                (String) first[0], "Date / time · " + Time.longStamp((LocalDateTime) first[1])
                + "   Household representative · " + (first[2] == null ? "—" : first[2])
                + "   Releasing officer · " + (first[3] == null ? "—" : first[3]));
        Map<String, Integer> already = new HashMap<>();
        for (Object[] row : found) {
            already.merge((String) row[4], (Integer) row[5], Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : requested.entrySet()) {
            Inventory item = inventory.get(entry.getKey());
            Integer released = already.get(entry.getKey());
            problem.rows.add(new String[] { item.getItemName(),
                released == null ? "—" : Units.quantity(released, item.getUnit()),
                Units.quantity(entry.getValue(), item.getUnit()), "", released == null ? "Allowed" : "Duplicate" });
        }
        return problem;
    }

    private static List<String> requestIds(Request request) {
        List<String> ids = new ArrayList<>();
        for (Line line : request.lines) {
            if (!ids.contains(line.itemId)) {
                ids.add(line.itemId);
            }
        }
        return ids;
    }

    private static String fmt(int number) {
        return String.format("%,d", number);
    }

    // ------------------------------------------------------------------ writes

    public Distribution record(Request request, Mode mode, UserAccount actor) throws SQLException {
        DistributionController rules = new DistributionController();
        return Db.transaction(connection -> {
            Disaster disaster = DisasterRepository.findById(connection, request.disaster.getDisasterId());
            if (disaster == null || !disaster.isActive()) {
                throw new IllegalStateException("This disaster is closed. Releases can only be made for active events.");
            }
            request.disaster = disaster;
            List<String> ids = requestIds(request);
            if (!ids.isEmpty()) {
                StringBuilder marks = new StringBuilder();
                for (int index = 0; index < ids.size(); index++) {
                    marks.append(index == 0 ? "?" : ", ?");
                }
                Db.list(connection, "SELECT batch_id FROM inventory_batches WHERE inventory_item_id IN (" + marks
                        + ") FOR UPDATE", row -> row.getString(1), ids.toArray());
            }
            Problem problem = check(connection, request);
            if (problem != null) {
                throw new BlockedException(problem);
            }
            Map<String, Inventory> inventory = loadInventory(connection, ids);
            String distributionId = IdGenerator.next(connection, "DST", true, 4);
            Beneficiaries household = request.householdId == null ? null
                    : BeneficiaryRepository.findById(connection, request.householdId, disaster);
            if (request.householdId != null && household == null) {
                throw new IllegalArgumentException("The selected household no longer exists.");
            }
            EvacuationCenter center = EvacuationCenterRepository.findById(connection, request.centerId,
                    disaster.getBarangay());
            if (request.centerId != null && center == null) {
                throw new IllegalArgumentException("The selected evacuation center no longer exists.");
            }
            String location = center != null ? center.getName() : household != null ? household.getAddress() : "";
            Distribution distribution = center == null
                    ? rules.createDistribution(distributionId, request.date.toString(), disaster, household, location,
                            claimReference(distributionId), actor)
                    : rules.createDistribution(distributionId, request.date.toString(), disaster, household, center,
                            location, claimReference(distributionId), actor);
            distribution.setAcknowledgmentMethod(request.acknowledgment);
            distribution.setNotes(request.notes);
            for (Line line : request.lines) {
                Inventory item = inventory.get(line.itemId);
                if (item.isArchived()) {
                    throw new IllegalArgumentException(item.getItemName() + " is archived and cannot be released.");
                }
                rules.addDistributionItem(distribution, new TransactionItem(item, line.quantity, line.batchId, null), actor);
            }
            String barangayId = disaster.getBarangay().getBarangayId();
            Db.update(connection, "INSERT INTO distributions (distribution_id, barangay_id, disaster_id, beneficiary_id, "
                    + "evacuation_center_id, distribution_date, release_location, receipt_reference, status, created_at, "
                    + "created_by, acknowledgment_method, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?)",
                    distributionId, barangayId, disaster.getDisasterId(), request.householdId, request.centerId,
                    request.date, location, claimReference(distributionId), Time.now(), actor.getAccountId(),
                    request.acknowledgment, request.notes);
            int number = 1;
            for (TransactionItem line : distribution.getItems()) {
                Db.update(connection, "INSERT INTO distribution_items (distribution_id, barangay_id, line_number, "
                        + "inventory_item_id, batch_id, quantity) VALUES (?, ?, ?, ?, ?, ?)", distributionId, barangayId,
                        number++, line.getInventory().getItemId(), line.getBatchReference(), line.getQuantity());
            }
            if (mode != Mode.DRAFT) {
                distribution.submit(actor);
                Db.update(connection, "UPDATE distributions SET status = 'PENDING' WHERE distribution_id = ?",
                        distributionId);
                if (mode == Mode.POST) {
                    post(connection, distribution, actor);
                } else {
                    AuditRepository.record(connection, actor, new AuditRepository.Entry("DISTRIBUTION_SUBMITTED",
                            AuditRepository.MODULE_DISTRIBUTION).reference(distributionId)
                            .summary("Release · " + recipient(distribution)).movement("No stock movement")
                            .result("Waiting for approval").distribution(distributionId).barangay(barangayId));
                }
            }
            return distribution;
        });
    }

    /** Sends a stored draft release for approval. */
    public void submitDraft(String distributionId, UserAccount actor) throws SQLException {
        Db.transaction(connection -> {
            Distribution distribution = find(connection, distributionId);
            if (distribution == null) {
                throw new IllegalStateException("This distribution no longer exists.");
            }
            distribution.submit(actor);
            Db.update(connection, "UPDATE distributions SET status = 'PENDING' WHERE distribution_id = ?", distributionId);
            AuditRepository.record(connection, actor, new AuditRepository.Entry("DISTRIBUTION_SUBMITTED",
                    AuditRepository.MODULE_DISTRIBUTION).reference(distributionId)
                    .summary("Release · " + recipient(distribution)).movement("No stock movement")
                    .result("Waiting for approval").distribution(distributionId)
                    .barangay(distribution.getDisaster().getBarangay().getBarangayId()));
            return null;
        });
    }

    public Distribution approve(String distributionId, UserAccount approver) throws SQLException {
        return Db.transaction(connection -> {
            Distribution loaded = find(connection, distributionId);
            if (loaded == null) {
                throw new IllegalStateException("This distribution no longer exists.");
            }
            for (String itemId : ids(itemRows(loaded))) {
                Db.list(connection, "SELECT batch_id FROM inventory_batches WHERE inventory_item_id = ? FOR UPDATE",
                        row -> row.getString(1), itemId);
            }
            // re-read after locking so the stock figures are the committed ones
            Distribution distribution = find(connection, distributionId);
            post(connection, distribution, approver);
            return distribution;
        });
    }

    private static List<Object[]> itemRows(Distribution distribution) {
        List<Object[]> rows = new ArrayList<>();
        for (TransactionItem item : distribution.getItems()) {
            rows.add(new Object[] { item.getInventory().getItemId() });
        }
        return rows;
    }

    public void reject(String distributionId, String reason, UserAccount approver) throws SQLException {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter the reason for rejecting this release.");
        }
        Db.transaction(connection -> {
            Distribution distribution = find(connection, distributionId);
            if (distribution == null) {
                throw new IllegalStateException("This distribution no longer exists.");
            }
            distribution.reject(approver);
            Db.update(connection, "UPDATE distributions SET status = 'REJECTED', rejection_reason = ? "
                    + "WHERE distribution_id = ?", reason.trim(), distributionId);
            AuditRepository.record(connection, approver, new AuditRepository.Entry("DISTRIBUTION_REJECTED",
                    AuditRepository.MODULE_DISTRIBUTION).reference(distributionId)
                    .summary("Release · " + recipient(distribution)).movement("No stock movement")
                    .reason(reason.trim()).result("Rejected - stock unchanged").distribution(distributionId)
                    .barangay(distribution.getDisaster().getBarangay().getBarangayId()));
            return null;
        });
    }

    private void post(Connection connection, Distribution distribution, UserAccount approver) throws SQLException {
        Map<String, Integer> before = new LinkedHashMap<>();
        for (TransactionItem line : distribution.getItems()) {
            before.putIfAbsent(line.getInventory().getItemId(), line.getInventory().getQuantity());
        }
        distribution.approve(approver);
        Db.update(connection, "UPDATE distributions SET status = 'APPROVED', released_by_account_id = ?, approved_at = ? "
                + "WHERE distribution_id = ?", approver.getAccountId(), Time.now(), distribution.getDistributionId());
        List<String> movement = new ArrayList<>();
        List<String> beforeLines = new ArrayList<>();
        List<String> afterLines = new ArrayList<>();
        Map<String, Boolean> done = new HashMap<>();
        for (TransactionItem line : distribution.getItems()) {
            Inventory item = line.getInventory();
            int changed = Db.update(connection, "UPDATE inventory_batches SET remaining_quantity = remaining_quantity - ? "
                    + "WHERE batch_id = ? AND inventory_item_id = ? AND remaining_quantity >= ?", line.getQuantity(),
                    line.getBatchReference(), item.getItemId(), line.getQuantity());
            if (changed != 1) {
                throw new IllegalStateException("Stock changed while this release was open. Review it again.");
            }
            movement.add("-" + Units.quantity(line.getQuantity(), item.getUnit()));
            if (done.put(item.getItemId(), true) == null) {
                beforeLines.add(Units.shortCategory(item.getCategory()) + " " + String.format("%,d", before.get(item.getItemId())));
                afterLines.add(Units.shortCategory(item.getCategory()) + " " + String.format("%,d", item.getQuantity()));
            }
        }
        AuditRepository.record(connection, approver, new AuditRepository.Entry("DISTRIBUTION_CONFIRMED",
                AuditRepository.MODULE_DISTRIBUTION).reference(distribution.getDistributionId())
                .summary("Release · " + recipient(distribution)).movement(String.join(", ", movement))
                .before(String.join("\n", beforeLines)).after(String.join("\n", afterLines))
                .reason(distribution.getEvacuationCenter() != null
                        ? "Center receipt " + distribution.getReceiptReference()
                        : "Household claim " + distribution.getReceiptReference())
                .result("Confirmed - receipt issued").distribution(distribution.getDistributionId())
                .barangay(distribution.getDisaster().getBarangay().getBarangayId()));
    }

    static String recipient(Distribution distribution) {
        if (distribution.getEvacuationCenter() != null) {
            return distribution.getEvacuationCenter().getName();
        }
        return distribution.getBeneficiary() == null ? distribution.getReleaseLocation()
                : distribution.getBeneficiary().getName() + " household";
    }
}
