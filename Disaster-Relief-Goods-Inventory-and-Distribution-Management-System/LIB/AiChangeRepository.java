package LIB;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Stores reviewable AI change proposals. Applying a proposal is always a separate human action. */
public final class AiChangeRepository {
    public static final String PENDING = "PENDING";
    public static final String APPLYING = "APPLYING";
    public static final String APPLIED = "APPLIED";
    public static final String REJECTED = "REJECTED";
    public static final String FAILED = "FAILED";

    public static final class Draft {
        public final String action;
        public final String summary;
        public final String request;
        public final String proposal;
        public final String before;
        public final String after;

        public Draft(String action, String summary, String request, String proposal, String before, String after) {
            this.action = action;
            this.summary = summary;
            this.request = request;
            this.proposal = proposal;
            this.before = before;
            this.after = after;
        }
    }

    public static final class Proposal {
        public final String id;
        public final String runId;
        public final String action;
        public final String summary;
        public final String request;
        public final String proposal;
        public final String before;
        public final String after;
        public final String status;
        public final String proposer;
        public final String createdAt;
        public final String reviewer;
        public final String reviewedAt;
        public final String outcome;
        public final String recordReference;

        Proposal(String id, String runId, String action, String summary, String request, String proposal,
                String before, String after, String status, String proposer, String createdAt, String reviewer,
                String reviewedAt, String outcome, String recordReference) {
            this.id = id;
            this.runId = runId;
            this.action = action;
            this.summary = summary;
            this.request = request;
            this.proposal = proposal;
            this.before = before;
            this.after = after;
            this.status = status;
            this.proposer = proposer;
            this.createdAt = createdAt;
            this.reviewer = reviewer;
            this.reviewedAt = reviewedAt;
            this.outcome = outcome;
            this.recordReference = recordReference;
        }
    }

    public List<Proposal> create(Barangay barangay, UserAccount user, String runId, List<Draft> drafts)
            throws SQLException {
        requireAccess(barangay, user);
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        return Db.transaction(connection -> {
            List<Proposal> created = new ArrayList<>();
            for (Draft draft : drafts) {
                String id = UUID.randomUUID().toString();
                Db.update(connection, "INSERT INTO ai_change_requests (proposal_id, run_id, barangay_id, action, "
                        + "summary, original_request, proposal_json, before_value, after_value, status, "
                        + "proposed_by_account_id, proposed_by_name, proposed_by_role, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))",
                        id, runId, barangay.getBarangayId(), draft.action, draft.summary, draft.request,
                        draft.proposal, draft.before, draft.after, PENDING, user.getAccountId(), user.getName(),
                        user.getRoleLabel());
                AuditRepository.record(connection, user, new AuditRepository.Entry("AI_CHANGE_PROPOSED", "AI Agent")
                        .reference(id).summary(draft.summary).before(draft.before).after(draft.after)
                        .reason(draft.request).result("Pending human review")
                        .barangay(barangay.getBarangayId()));
                created.add(load(connection, id));
            }
            return created;
        });
    }

    public List<Proposal> recent(Barangay barangay, UserAccount user) throws SQLException {
        requireAccess(barangay, user);
        String barangayId = barangay.getBarangayId();
        return Db.read(connection -> Db.list(connection,
                "SELECT proposal_id, run_id, action, summary, original_request, proposal_json, before_value, "
                        + "after_value, status, proposed_by_name, created_at, reviewed_by_name, reviewed_at, "
                        + "outcome, record_reference FROM ai_change_requests WHERE barangay_id=? "
                        + "ORDER BY created_at DESC LIMIT 200",
                AiChangeRepository::read, barangayId));
    }

    public Proposal find(Barangay barangay, UserAccount user, String id) throws SQLException {
        requireAccess(barangay, user);
        return Db.read(connection -> {
            Proposal proposal = Db.one(connection, "SELECT proposal_id, run_id, action, summary, original_request, "
                    + "proposal_json, before_value, after_value, status, proposed_by_name, created_at, "
                    + "reviewed_by_name, reviewed_at, outcome, record_reference FROM ai_change_requests "
                    + "WHERE proposal_id=? AND barangay_id=?",
                    AiChangeRepository::read, id, barangay.getBarangayId());
            return proposal;
        });
    }

    public void beginApply(Barangay barangay, UserAccount reviewer, String id) throws SQLException {
        requireManager(barangay, reviewer);
        Db.transaction(connection -> {
            int count = Db.update(connection, "UPDATE ai_change_requests SET status=?, reviewed_by_account_id=?, "
                    + "reviewed_by_name=?, reviewed_by_role=?, reviewed_at=CURRENT_TIMESTAMP(6) "
                    + "WHERE proposal_id=? AND barangay_id=? AND status=?",
                    APPLYING, reviewer.getAccountId(), reviewer.getName(), reviewer.getRoleLabel(), id,
                    barangay.getBarangayId(), PENDING);
            if (count != 1) {
                throw new IllegalStateException("This proposal is no longer waiting for review.");
            }
            AuditRepository.record(connection, reviewer, new AuditRepository.Entry("AI_CHANGE_REVIEW_STARTED", "AI Agent")
                    .reference(id).summary("AI change review started").result("Application in progress")
                    .barangay(barangay.getBarangayId()));
            return null;
        });
    }

    public void finish(Barangay barangay, UserAccount reviewer, String id, String status, String outcome,
            String recordReference) throws SQLException {
        requireManager(barangay, reviewer);
        if (!APPLIED.equals(status) && !FAILED.equals(status)) {
            throw new IllegalArgumentException("Invalid AI change result.");
        }
        Db.transaction(connection -> {
            int count = Db.update(connection, "UPDATE ai_change_requests SET status=?, outcome=?, record_reference=?, "
                    + "applied_at=CASE WHEN ?=? THEN CURRENT_TIMESTAMP(6) ELSE NULL END "
                    + "WHERE proposal_id=? AND barangay_id=? AND status=?",
                    status, outcome, blankToNull(recordReference), status, APPLIED, id, barangay.getBarangayId(),
                    APPLYING);
            if (count != 1) {
                throw new IllegalStateException("The proposal review state changed unexpectedly.");
            }
            AuditRepository.record(connection, reviewer, new AuditRepository.Entry(
                    APPLIED.equals(status) ? "AI_CHANGE_APPLIED" : "AI_CHANGE_FAILED", "AI Agent")
                    .reference(id).summary("AI proposal " + status.toLowerCase())
                    .result(outcome).barangay(barangay.getBarangayId()));
            return null;
        });
    }

    public void reject(Barangay barangay, UserAccount reviewer, String id, String note) throws SQLException {
        requireManager(barangay, reviewer);
        Db.transaction(connection -> {
            int count = Db.update(connection, "UPDATE ai_change_requests SET status=?, reviewed_by_account_id=?, "
                    + "reviewed_by_name=?, reviewed_by_role=?, reviewed_at=CURRENT_TIMESTAMP(6), outcome=? "
                    + "WHERE proposal_id=? AND barangay_id=? AND status=?",
                    REJECTED, reviewer.getAccountId(), reviewer.getName(), reviewer.getRoleLabel(),
                    note == null ? "" : note.trim(), id, barangay.getBarangayId(), PENDING);
            if (count != 1) {
                throw new IllegalStateException("This proposal is no longer waiting for review.");
            }
            AuditRepository.record(connection, reviewer, new AuditRepository.Entry("AI_CHANGE_REJECTED", "AI Agent")
                    .reference(id).summary("AI proposal rejected").reason(note)
                    .result("No application change made").barangay(barangay.getBarangayId()));
            return null;
        });
    }

    private Proposal load(java.sql.Connection connection, String id) throws SQLException {
        Proposal proposal = Db.one(connection, "SELECT proposal_id, run_id, action, summary, original_request, "
                + "proposal_json, before_value, after_value, status, proposed_by_name, created_at, "
                + "reviewed_by_name, reviewed_at, outcome, record_reference FROM ai_change_requests WHERE proposal_id=?",
                AiChangeRepository::read, id);
        if (proposal == null) {
            throw new SQLException("AI change proposal was not saved.");
        }
        return proposal;
    }

    private static Proposal read(java.sql.ResultSet row) throws SQLException {
        Timestamp created = row.getTimestamp("created_at");
        Timestamp reviewed = row.getTimestamp("reviewed_at");
        return new Proposal(row.getString("proposal_id"), row.getString("run_id"), row.getString("action"),
                row.getString("summary"), row.getString("original_request"), row.getString("proposal_json"),
                row.getString("before_value"), row.getString("after_value"), row.getString("status"),
                row.getString("proposed_by_name"), created == null ? "" : Time.stamp(created.toLocalDateTime()),
                row.getString("reviewed_by_name"), reviewed == null ? "" : Time.stamp(reviewed.toLocalDateTime()),
                row.getString("outcome"), row.getString("record_reference"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void requireAccess(Barangay barangay, UserAccount user) {
        if (barangay == null || user == null || !user.isActive() || !user.canAccessBarangay(barangay)) {
            throw new SecurityException("You cannot access AI change proposals in this workspace.");
        }
    }

    private static void requireManager(Barangay barangay, UserAccount user) {
        requireAccess(barangay, user);
        if (!user.canManageRecords()) {
            throw new SecurityException("Only a Barangay Official or Admin can review AI change proposals.");
        }
    }
}
