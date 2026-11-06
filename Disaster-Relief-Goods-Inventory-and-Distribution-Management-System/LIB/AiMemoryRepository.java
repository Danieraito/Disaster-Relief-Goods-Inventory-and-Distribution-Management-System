package LIB;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stores agent runs and open recommendations. Writes never change inventory or distributions. */
public final class AiMemoryRepository {
    static final int RUN_LIMIT = 20;
    private static final Pattern FOLLOW_HEADING = Pattern.compile("(?i)^#{1,3}\\s*follow[- ]?ups?\\s*:?$");
    private static final Pattern LIST_ITEM = Pattern.compile("^([-*•]|\\d{1,2}[.)]|\\[OPEN\\])\\s+(.*)$");
    private static final java.util.Map<String, String> TOOL_FOLLOW_UPS = java.util.Map.ofEntries(
            java.util.Map.entry("get_stock_alerts",
                    "Identify each named low-stock, expired, or soon-to-expire item and record its specific next action."),
            java.util.Map.entry("get_open_follow_ups",
                    "Recheck each unresolved task against current stock and pending workflows; keep only tasks with a verified action still needed."),
            java.util.Map.entry("get_pending_workflow",
                    "Review each pending donation or distribution by record; have an authorized manager decide whether it can proceed."),
            java.util.Map.entry("get_workspace_snapshot",
                    "Identify the specific missing or outdated workspace record before assigning a correction."),
            java.util.Map.entry("get_disasters",
                    "Confirm the active disaster status and affected-household count with the response lead; correct any verified mismatch."),
            java.util.Map.entry("get_inventory",
                    "Check the named item's usable quantity and batch expiry; identify any quantity or batch that needs correction."),
            java.util.Map.entry("get_donors",
                    "Confirm the donor, item quantities, and delivery date against source documents before recording the donation."),
            java.util.Map.entry("get_evacuation_centers",
                    "Obtain current occupancy and capacity for each relevant evacuation center; occupancy is not in the database."),
            java.util.Map.entry("get_memory",
                    "Compare the repeated recommendation with the latest assessment and feedback; retain it only if current evidence supports it."),
            java.util.Map.entry("get_historical_trends",
                    "Compare the named item's recent distribution trend with verified current needs before requesting replenishment."),
            java.util.Map.entry("simulate_scenario",
                    "Verify the scenario's hazard and severity assumptions against completed historical events before using its baseline."));

    public static final class Run {
        public final String runId, mode, question, answer, toolsUsed, createdAt, status, provider, model;
        public final long elapsedMillis, providerElapsedMillis, promptTokens, completionTokens;

        Run(String runId, String mode, String question, String answer, String toolsUsed, String createdAt,
                String status, String provider, String model, long elapsedMillis, long providerElapsedMillis,
                long promptTokens, long completionTokens) {
            this.runId = runId;
            this.mode = mode;
            this.question = question == null ? "" : question;
            this.answer = answer;
            this.toolsUsed = toolsUsed == null ? "" : toolsUsed;
            this.createdAt = createdAt;
            this.status = status == null ? "SUCCEEDED" : status;
            this.provider = provider == null ? "" : provider;
            this.model = model == null ? "" : model;
            this.elapsedMillis = elapsedMillis;
            this.providerElapsedMillis = providerElapsedMillis;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
        }
    }

    public static final class FollowUp {
        public final String followUpId, title, detail, status, createdAt, lastAssessedAt;

        FollowUp(String followUpId, String title, String detail, String status, String createdAt,
                String lastAssessedAt) {
            this.followUpId = followUpId;
            this.title = describeFollowUp(title);
            String cleanDetail = detail == null ? "" : detail;
            this.detail = cleanDetail.equals(title) ? this.title : describeFollowUp(cleanDetail);
            this.status = status;
            this.createdAt = createdAt;
            this.lastAssessedAt = lastAssessedAt == null ? "" : lastAssessedAt;
        }
    }

    public String saveRun(Barangay barangay, UserAccount user, String mode, String question, String answer,
            List<String> tools, String status, String provider, String model, long elapsedMillis,
            long providerElapsedMillis, long promptTokens, long completionTokens) throws SQLException {
        if (barangay == null || user == null || !user.isActive() || !user.canAccessBarangay(barangay)) {
            throw new SecurityException("You cannot store AI agent memory for this workspace.");
        }
        if (answer == null || answer.isBlank() || (!"SUCCEEDED".equals(status) && !"FAILED".equals(status))) {
            throw new IllegalArgumentException("A completed agent outcome is required.");
        }
        String runId = UUID.randomUUID().toString();
        String toolLine = tools == null || tools.isEmpty() ? "" : String.join(", ", new LinkedHashSet<>(tools));
        List<String> followUps = extractFollowUps(answer);
        Db.transaction(connection -> {
            Db.update(connection,
                    "INSERT INTO ai_runs (run_id, barangay_id, account_id, mode, status, provider, model, elapsed_ms, "
                            + "provider_elapsed_ms, prompt_tokens, completion_tokens, question, answer, tools_used, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))",
                    runId, barangay.getBarangayId(), user.getAccountId(), mode, status, provider, model,
                    Math.max(0, elapsedMillis), Math.max(0, providerElapsedMillis), Math.max(0, promptTokens),
                    Math.max(0, completionTokens),
                    question == null ? "" : question.trim(), answer, toolLine);
            if ("SUCCEEDED".equals(status)) {
                for (String item : followUps) {
                    insertFollowUp(connection, barangay.getBarangayId(), runId, item);
                }
            }
            return null;
        });
        return runId;
    }

    public List<Run> recentRuns(String barangayId) throws SQLException {
        return Db.read(connection -> Db.list(connection,
                "SELECT run_id, mode, question, answer, tools_used, created_at, status, provider, model, "
                        + "elapsed_ms, provider_elapsed_ms, prompt_tokens, completion_tokens "
                        + "FROM ai_runs WHERE barangay_id=? "
                        + "ORDER BY created_at DESC LIMIT " + RUN_LIMIT,
                row -> new Run(row.getString("run_id"), row.getString("mode"), row.getString("question"),
                        row.getString("answer"), row.getString("tools_used"), stamp(row.getTimestamp("created_at")),
                        row.getString("status"), row.getString("provider"), row.getString("model"),
                        row.getLong("elapsed_ms"), row.getLong("provider_elapsed_ms"),
                        row.getLong("prompt_tokens"), row.getLong("completion_tokens")),
                barangayId));
    }

    public List<FollowUp> followUps(String barangayId, boolean openOnly) throws SQLException {
        String sql = "SELECT follow_up_id, title, detail, status, created_at, last_assessed_at FROM ai_follow_ups "
                + "WHERE barangay_id=?" + (openOnly ? " AND status='OPEN'" : "")
                + " ORDER BY (status='OPEN') DESC, created_at DESC LIMIT 40";
        return Db.read(connection -> Db.list(connection, sql, row -> new FollowUp(row.getString("follow_up_id"),
                row.getString("title"), row.getString("detail"), row.getString("status"),
                stamp(row.getTimestamp("created_at")), stamp(row.getTimestamp("last_assessed_at"))), barangayId));
    }

    public void updateStatus(Barangay barangay, UserAccount user, String followUpId, String status)
            throws SQLException {
        requireManage(barangay, user);
        if (!"OPEN".equals(status) && !"RESOLVED".equals(status) && !"DISMISSED".equals(status)) {
            throw new IllegalArgumentException("Choose Open, Resolved, or Dismissed.");
        }
        Db.transaction(connection -> {
            int updated = Db.update(connection,
                    "UPDATE ai_follow_ups SET status=?, updated_at=CURRENT_TIMESTAMP(6) "
                            + "WHERE follow_up_id=? AND barangay_id=?",
                    status, followUpId, barangay.getBarangayId());
            if (updated == 0) {
                throw new IllegalArgumentException("That follow-up was not found in this workspace.");
            }
            return null;
        });
    }

    public void markAssessed(Barangay barangay, UserAccount user, List<String> followUpIds) throws SQLException {
        if (barangay == null || user == null || !user.canAccessBarangay(barangay)) {
            throw new SecurityException("You cannot update AI follow-ups for this workspace.");
        }
        if (followUpIds == null || followUpIds.isEmpty()) {
            return;
        }
        Db.transaction(connection -> {
            for (String id : followUpIds) {
                Db.update(connection, "UPDATE ai_follow_ups SET last_assessed_at=CURRENT_TIMESTAMP(6), "
                        + "updated_at=CURRENT_TIMESTAMP(6) WHERE follow_up_id=? AND barangay_id=?",
                        id, barangay.getBarangayId());
            }
            return null;
        });
    }

    public String memoryBrief(String barangayId) throws SQLException {
        List<Run> runs = recentRuns(barangayId);
        List<FollowUp> open = followUps(barangayId, true);
        StringBuilder out = new StringBuilder();
        out.append("OPEN FOLLOW-UPS: ").append(open.size()).append('\n');
        for (FollowUp item : open) {
            out.append("- ").append(item.title);
            if (!item.detail.isEmpty() && !item.detail.equals(item.title)) {
                out.append(" — ").append(item.detail);
            }
            out.append(" (last assessed: ").append(item.lastAssessedAt.isEmpty() ? "never" : item.lastAssessedAt)
                    .append(")\n");
        }
        out.append("\nPREVIOUS AGENT RUNS (newest first):\n");
        if (runs.isEmpty()) {
            out.append("None stored yet.\n");
        }
        int shown = 0;
        for (Run run : runs) {
            if (shown++ == 8) {
                break;
            }
            out.append("- ").append(run.createdAt).append(" · ").append(run.mode);
            if (!run.question.isBlank()) {
                out.append(" · asked: ").append(trim(run.question, 120));
            }
            out.append('\n').append("  outcome: ").append(trim(run.answer.replace('\n', ' '), 220)).append('\n');
        }
        return out.toString();
    }

    public static List<String> extractFollowUps(String answer) {
        List<String> items = new ArrayList<>();
        if (answer == null || answer.isBlank()) {
            return items;
        }
        boolean inSection = false;
        for (String raw : answer.split("\n", -1)) {
            String trimmed = raw.trim();
            if (FOLLOW_HEADING.matcher(trimmed.replace("*", "")).matches()) {
                inSection = true;
                continue;
            }
            if (inSection && trimmed.startsWith("#")) {
                inSection = false;
                continue;
            }
            Matcher item = LIST_ITEM.matcher(trimmed);
            if (item.matches() && (inSection || trimmed.toUpperCase().startsWith("[OPEN]"))) {
                String body = item.group(2).trim();
                if (!body.isEmpty() && !isToolOnlyFollowUp(body)) {
                    items.add(describeFollowUp(body));
                }
            }
        }
        Set<String> unique = new LinkedHashSet<>(items);
        return new ArrayList<>(unique);
    }

    private static String describeFollowUp(String text) {
        String value = text == null ? "" : text.trim();
        value = value.replaceFirst("(?i)^\\[OPEN\\]\\s*", "");
        String toolName = value.replaceAll("^[`*_\\s]+|[`*_\\s]+$", "");
        return TOOL_FOLLOW_UPS.getOrDefault(toolName, value);
    }

    private static boolean isToolOnlyFollowUp(String text) {
        String value = text.replaceFirst("(?i)^\\[OPEN\\]\\s*", "")
                .replaceAll("^[`*_\\s]+|[`*_\\s]+$", "");
        return TOOL_FOLLOW_UPS.containsKey(value);
    }

    private static void insertFollowUp(java.sql.Connection connection, String barangayId, String runId, String item)
            throws SQLException {
        String title = trim(item, 180);
        int existing = Db.intValue(connection,
                "SELECT COUNT(*) FROM ai_follow_ups WHERE barangay_id=? AND status='OPEN' AND title=?",
                barangayId, title);
        if (existing > 0) {
            return;
        }
        Db.update(connection,
                "INSERT INTO ai_follow_ups (follow_up_id, barangay_id, run_id, title, detail, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'OPEN', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
                UUID.randomUUID().toString(), barangayId, runId, title, item);
    }

    private static void requireManage(Barangay barangay, UserAccount user) {
        if (barangay == null || user == null || !user.isActive() || !user.canManageRecords()
                || !user.canAccessBarangay(barangay)) {
            throw new SecurityException("Only a Barangay Official or Admin can close AI follow-ups.");
        }
    }

    private static String stamp(Timestamp value) {
        if (value == null) {
            return "";
        }
        LocalDateTime when = value.toLocalDateTime();
        return Time.stamp(when);
    }

    private static String trim(String text, int max) {
        String value = text == null ? "" : text.trim();
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
