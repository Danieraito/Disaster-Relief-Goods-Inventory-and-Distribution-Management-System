package LIB;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/** Stores human review of AI advice; feedback never triggers inventory or distribution changes. */
public final class AiFeedbackRepository {
    /** How many recent reviews the workspace screen shows. */
    static final int RECENT_LIMIT = 10;

    public static final class Entry {
        public final String decision, comment, recommendation, reviewer, createdAt, runId;

        Entry(String decision, String comment, String recommendation, String reviewer, String createdAt, String runId) {
            this.decision = decision;
            this.comment = comment == null ? "" : comment;
            this.recommendation = recommendation;
            this.reviewer = reviewer;
            this.createdAt = createdAt;
            this.runId = runId == null ? "" : runId;
        }
    }

    public static final class Summary {
        public final int accepted;
        public final int rejected;
        public final int needsRevision;

        Summary(int accepted, int rejected, int needsRevision) {
            this.accepted = accepted;
            this.rejected = rejected;
            this.needsRevision = needsRevision;
        }

        public int total() {
            return accepted + rejected + needsRevision;
        }
    }

    public void save(Barangay barangay, UserAccount user, String runId, String decision,
            String recommendation, String comment)
            throws SQLException {
        if (barangay == null || user == null || !user.isActive() || !user.canManageRecords()
                || !user.canAccessBarangay(barangay)) {
            throw new SecurityException("Only an authorized Barangay Official or Admin can submit AI recommendation feedback.");
        }
        if (runId == null || runId.isBlank() || recommendation == null || recommendation.trim().isEmpty()) {
            throw new IllegalArgumentException("Generate an assessment before submitting feedback.");
        }
        if (!"Accepted".equals(decision) && !"Rejected".equals(decision) && !"Needs revision".equals(decision)) {
            throw new IllegalArgumentException("Choose a feedback decision.");
        }
        Db.transaction(connection -> {
            Db.update(connection,
                    "INSERT INTO ai_feedback (feedback_id, barangay_id, account_id, run_id, decision, recommendation, comment, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))",
                    UUID.randomUUID().toString(), barangay.getBarangayId(), user.getAccountId(), runId, decision,
                    recommendation, comment == null ? "" : comment.trim());
            return null;
        });
    }

    /** The most recent reviews for a workspace, newest first, with timestamps ready to display. */
    public List<Entry> recent(String barangayId) throws SQLException {
        return Db.read(connection -> Db.list(connection,
                "SELECT f.decision, f.comment, f.recommendation, f.run_id, u.name AS reviewer, f.created_at "
                        + "FROM ai_feedback f JOIN user_accounts u ON u.account_id=f.account_id WHERE f.barangay_id=? "
                        + "ORDER BY f.created_at DESC LIMIT " + RECENT_LIMIT,
                row -> {
                    Timestamp created = row.getTimestamp("created_at");
                    return new Entry(row.getString("decision"), row.getString("comment"),
                            row.getString("recommendation"), row.getString("reviewer"),
                    created == null ? "" : Time.stamp(created.toLocalDateTime()), row.getString("run_id"));
                }, barangayId));
    }

    public Summary summary(String barangayId) throws SQLException {
        return Db.read(connection -> Db.one(connection,
                "SELECT SUM(decision='Accepted') AS accepted, SUM(decision='Rejected') AS rejected, "
                        + "SUM(decision='Needs revision') AS revisions FROM ai_feedback WHERE barangay_id=?",
                row -> new Summary(row.getInt("accepted"), row.getInt("rejected"), row.getInt("revisions")),
                barangayId));
    }
}
