package LIB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Writes and reads the audit log. Audit rows can only be added; the database refuses
 * updates and deletes (see the triggers in schema.sql).
 */
public class AuditRepository {
    private final AuditLogController controller = new AuditLogController();

    /**
     * Builds an Audits record through the controller and saves it with an open connection, so it
     * becomes part of the same all-or-nothing transaction as the change it describes.
     */
    public Audits record(Connection connection, UserAccount user, String module, String barangayId,
            String action, String reference, String beforeValue, String afterValue, String reason,
            Donations donation, Distribution distribution) throws SQLException {
        String auditId = Database.nextId(connection, "audit_logs", "audit_id", "AUD");
        Audits audit = controller.createAuditRecord(auditId, action, Database.now(), user, donation, distribution);
        audit.setReference(reference);
        audit.setBeforeValue(beforeValue);
        audit.setAfterValue(afterValue);
        audit.setReason(reason);

        String sql = "INSERT INTO audit_logs (audit_id, action, logged_at, user_id, donation_id, distribution_id, "
                + "reference, before_value, after_value, reason, barangay_id, module) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, audit.getAuditId());
            statement.setString(2, audit.getAction());
            statement.setString(3, audit.getDateTime());
            statement.setString(4, user.getAccountId());
            statement.setString(5, donation == null ? null : donation.getDonationId());
            statement.setString(6, distribution == null ? null : distribution.getDistributionId());
            statement.setString(7, audit.getReference());
            statement.setString(8, audit.getBeforeValue());
            statement.setString(9, audit.getAfterValue());
            statement.setString(10, audit.getReason());
            statement.setString(11, barangayId);
            statement.setString(12, module);
            statement.executeUpdate();
        }
        return audit;
    }

    /** Saves an audit record on its own (used for blocked attempts, which change nothing else). */
    public void recordAlone(UserAccount user, String module, String barangayId, String action,
            String reference, String beforeValue, String afterValue, String reason) throws SQLException {
        synchronized (Database.WRITE_LOCK) {
            try (Connection connection = Database.getConnection()) {
                record(connection, user, module, barangayId, action, reference, beforeValue, afterValue,
                        reason, null, null);
            }
        }
    }

    /**
     * Audit history, newest first. barangayId null means every workspace (Admin);
     * onlyUserId limits the list to one person's own activity (Encoder).
     */
    public List<Map<String, Object>> list(String barangayId, String onlyUserId) throws SQLException {
        String sql = "SELECT a.audit_id AS id, a.logged_at AS loggedAt, a.action, a.reference, "
                + "a.before_value AS beforeValue, a.after_value AS afterValue, a.reason, a.module, "
                + "u.name AS actor, u.role AS actorRole, b.name AS barangay "
                + "FROM audit_logs a LEFT JOIN users u ON u.account_id = a.user_id "
                + "LEFT JOIN barangays b ON b.barangay_id = a.barangay_id "
                + "WHERE (? IS NULL OR a.barangay_id = ?) AND (? IS NULL OR a.user_id = ?) "
                + "ORDER BY a.logged_at DESC, a.audit_id DESC";
        return Database.query(sql, barangayId, barangayId, onlyUserId, onlyUserId);
    }
}
