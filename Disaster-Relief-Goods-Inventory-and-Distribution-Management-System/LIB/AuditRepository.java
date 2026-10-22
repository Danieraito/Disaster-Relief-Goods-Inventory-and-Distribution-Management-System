package LIB;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes and reads the append-only audit ledger. The database refuses UPDATE and DELETE on
 * audit_logs, and the other repositories call {@link #record} inside their own transaction so a
 * posted change and its audit row are saved (or rolled back) together.
 */
public class AuditRepository {
    public static final String MODULE_INVENTORY = "Inventory";
    public static final String MODULE_DISASTERS = "Disasters";
    public static final String MODULE_BENEFICIARIES = "Beneficiaries";
    public static final String MODULE_DONATIONS = "Donations";
    public static final String MODULE_DISTRIBUTION = "Distribution";
    public static final String MODULE_USERS = "Users";

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("ITEM_CREATED", "Item created");
        LABELS.put("ITEM_EDITED", "Item edited");
        LABELS.put("ITEM_ARCHIVED", "Item archived");
        LABELS.put("ITEM_RESTORED", "Item restored");
        LABELS.put("ITEM_DELETED", "Item deleted");
        LABELS.put("STOCK_ADJUSTED", "Stock adjustment");
        LABELS.put("DONATION_SUBMITTED", "Donation submitted");
        LABELS.put("DONATION_POSTED", "Donation posted");
        LABELS.put("DONATION_REJECTED", "Donation rejected");
        LABELS.put("DISTRIBUTION_SUBMITTED", "Distribution submitted");
        LABELS.put("DISTRIBUTION_CONFIRMED", "Distribution confirmed");
        LABELS.put("DISTRIBUTION_REJECTED", "Distribution rejected");
        LABELS.put("VALIDATION_BLOCKED", "Release blocked");
        LABELS.put("HOUSEHOLD_REGISTERED", "Household registered");
        LABELS.put("HOUSEHOLD_UPDATED", "Household updated");
        LABELS.put("DISASTER_CREATED", "Disaster created");
        LABELS.put("DISASTER_CLOSED", "Disaster closed");
        LABELS.put("DONOR_SAVED", "Donor saved");
        LABELS.put("USER_INVITED", "User invited");
        LABELS.put("USER_ROLE_CHANGED", "Role changed");
        LABELS.put("USER_DEACTIVATED", "Account deactivated");
        LABELS.put("USER_REACTIVATED", "Account reactivated");
        LABELS.put("PASSWORD_RESET", "Password reset");
        LABELS.put("BARANGAY_CREATED", "Workspace created");
    }

    public static String label(String action) {
        String label = LABELS.get(action);
        return label == null ? action : label;
    }

    /** Everything one audit row can carry; build it with the chained setters. */
    public static final class Entry {
        private final String action;
        private final String module;
        private String reference = "";
        private String summary = "";
        private String movement = "";
        private String before = "";
        private String after = "";
        private String reason = "";
        private String result = "";
        private String barangayId;
        private String itemId;
        private String householdId;
        private String donationId;
        private String distributionId;

        public Entry(String action, String module) {
            this.action = action;
            this.module = module;
        }

        public Entry reference(String value) { this.reference = value; return this; }
        public Entry summary(String value) { this.summary = value; return this; }
        public Entry movement(String value) { this.movement = value; return this; }
        public Entry before(String value) { this.before = value; return this; }
        public Entry after(String value) { this.after = value; return this; }
        public Entry reason(String value) { this.reason = value; return this; }
        public Entry result(String value) { this.result = value; return this; }
        public Entry barangay(String value) { this.barangayId = value; return this; }
        public Entry item(String value) { this.itemId = value; return this; }
        public Entry household(String value) { this.householdId = value; return this; }
        public Entry donation(String value) { this.donationId = value; return this; }
        public Entry distribution(String value) { this.distributionId = value; return this; }
    }

    /** Adds one row to the ledger inside the caller's transaction. */
    public static void record(Connection connection, UserAccount actor, Entry entry) throws SQLException {
        Db.update(connection, "INSERT INTO audit_logs (audit_id, action, action_at, reference, before_value, "
                + "after_value, reason, account_id, donation_id, distribution_id, module, barangay_id, "
                + "inventory_item_id, household_id, summary, movement, result, actor_name, actor_role) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), entry.action, Time.now(), blankToNull(entry.reference),
                blankToNull(entry.before), blankToNull(entry.after), blankToNull(entry.reason),
                actor == null ? null : actor.getAccountId(), entry.donationId, entry.distributionId,
                entry.module, entry.barangayId, entry.itemId, entry.householdId, blankToNull(entry.summary),
                blankToNull(entry.movement), blankToNull(entry.result),
                actor == null ? null : actor.getName(), actor == null ? null : actor.getRoleLabel());
    }

    /** Records an entry on its own (used for blocked attempts that change no stock). */
    public void record(UserAccount actor, Entry entry) throws SQLException {
        Db.transaction(connection -> {
            record(connection, actor, entry);
            return null;
        });
    }

    /** Next VAL-yyyy-nnnn reference for a blocked attempt. */
    public String recordBlockedAttempt(UserAccount actor, String barangayId, String reason, String summary,
            String distributionContext) throws SQLException {
        return Db.transaction(connection -> {
            String reference = IdGenerator.next(connection, "VAL", true, 4);
            record(connection, actor, new Entry("VALIDATION_BLOCKED", MODULE_DISTRIBUTION)
                    .reference(reference).summary(summary).reason(reason).result("Blocked - no stock movement")
                    .after(distributionContext).barangay(barangayId));
            return reference;
        });
    }

    public static final class Filter {
        public String barangayId;
        public boolean includeGlobal;
        public String module;
        public String actorAccountId;
        public String text;
        public LocalDate from;
        public LocalDate to;
        public List<String> modules;
        public int limit = 200;
    }

    public List<Audits> find(Filter filter) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT audit_id, action, action_at, reference, before_value, "
                + "after_value, reason, account_id, module, barangay_id, inventory_item_id, household_id, "
                + "summary, movement, result, actor_name, actor_role FROM audit_logs WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (filter.barangayId != null) {
            sql.append(filter.includeGlobal ? " AND (barangay_id = ? OR barangay_id IS NULL)" : " AND barangay_id = ?");
            params.add(filter.barangayId);
        }
        if (filter.module != null && !filter.module.isEmpty()) {
            sql.append(" AND module = ?");
            params.add(filter.module);
        }
        if (filter.modules != null && !filter.modules.isEmpty()) {
            sql.append(" AND module IN (");
            for (int index = 0; index < filter.modules.size(); index++) {
                sql.append(index == 0 ? "?" : ", ?");
                params.add(filter.modules.get(index));
            }
            sql.append(")");
        }
        if (filter.actorAccountId != null) {
            sql.append(" AND account_id = ?");
            params.add(filter.actorAccountId);
        }
        if (filter.from != null) {
            sql.append(" AND action_at >= ?");
            params.add(filter.from.atStartOfDay());
        }
        if (filter.to != null) {
            sql.append(" AND action_at < ?");
            params.add(filter.to.plusDays(1).atStartOfDay());
        }
        if (filter.text != null && !filter.text.trim().isEmpty()) {
            sql.append(" AND (reference LIKE ? OR actor_name LIKE ? OR reason LIKE ? OR summary LIKE ? OR action LIKE ?)");
            String like = "%" + filter.text.trim() + "%";
            for (int index = 0; index < 5; index++) {
                params.add(like);
            }
        }
        sql.append(" ORDER BY action_at DESC, audit_id LIMIT ").append(Math.max(1, filter.limit));
        return Db.read(connection -> Db.list(connection, sql.toString(), AuditRepository::read, params.toArray()));
    }

    /** History of one inventory item or household (used on the detail screens). */
    public List<Audits> findFor(String column, String id, String action) throws SQLException {
        String sql = "SELECT audit_id, action, action_at, reference, before_value, after_value, reason, account_id, "
                + "module, barangay_id, inventory_item_id, household_id, summary, movement, result, actor_name, "
                + "actor_role FROM audit_logs WHERE " + column + " = ?"
                + (action == null ? "" : " AND action = ?") + " ORDER BY action_at DESC";
        return Db.read(connection -> action == null
                ? Db.list(connection, sql, AuditRepository::read, id)
                : Db.list(connection, sql, AuditRepository::read, id, action));
    }

    private static Audits read(java.sql.ResultSet row) throws SQLException {
        LocalDateTime when = Db.dateTime(row, "action_at");
        Audits audit = new Audits(row.getString("audit_id"), row.getString("action"),
                when == null ? "" : when.toString(), null, null, null, row.getString("reference"),
                row.getString("before_value"), row.getString("after_value"), row.getString("reason"));
        audit.setStoredDetails(when, row.getString("module"), row.getString("summary"), row.getString("movement"),
                row.getString("result"), row.getString("actor_name"), row.getString("actor_role"),
                row.getString("barangay_id"), row.getString("inventory_item_id"), row.getString("household_id"));
        return audit;
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
