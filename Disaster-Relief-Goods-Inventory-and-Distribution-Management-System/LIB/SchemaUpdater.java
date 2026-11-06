package LIB;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps the database in step with the application. It can create the base tables from
 * database/schema.sql on an empty database, and it adds the extra columns and tables the
 * BarangayCare screens need. Every step is safe to repeat, so existing data is never touched.
 */
public final class SchemaUpdater {
    private static boolean checked;

    private SchemaUpdater() {
    }

    // table, column, column definition
    private static final String[][] COLUMNS = {
        {"user_accounts", "last_login_at", "DATETIME(6) NULL"},
        {"user_accounts", "created_at", "DATETIME(6) NULL"},
        {"disasters", "description", "TEXT NULL"},
        {"disasters", "evacuation_site", "VARCHAR(255) NULL"},
        {"disasters", "response_lead", "VARCHAR(150) NULL"},
        {"disasters", "assessment_contact", "VARCHAR(255) NULL"},
        {"disasters", "created_by", "VARCHAR(36) NULL"},
        {"disasters", "created_at", "DATETIME(6) NULL"},
        {"disasters", "closed_at", "DATETIME(6) NULL"},
        {"households", "contact_number", "VARCHAR(40) NULL"},
        {"households", "purok", "VARCHAR(60) NULL"},
        {"households", "is_homeless", "BOOLEAN NOT NULL DEFAULT FALSE"},
        {"households", "evacuation_status", "VARCHAR(30) NULL"},
        {"households", "evacuation_location", "VARCHAR(255) NULL"},
        {"households", "assessment_note", "TEXT NULL"},
        {"households", "registered_by", "VARCHAR(36) NULL"},
        {"households", "registered_at", "DATETIME(6) NULL"},
        {"family_members", "sex", "VARCHAR(10) NULL"},
        {"family_members", "is_pregnant", "BOOLEAN NOT NULL DEFAULT FALSE"},
        {"family_members", "age_months", "INT NULL"},
        {"inventory_items", "is_archived", "BOOLEAN NOT NULL DEFAULT FALSE"},
        {"inventory_items", "archived_reason", "TEXT NULL"},
        {"inventory_items", "created_at", "DATETIME(6) NULL"},
        {"inventory_items", "created_by", "VARCHAR(36) NULL"},
        {"inventory_items", "updated_at", "DATETIME(6) NULL"},
        {"inventory_items", "updated_by", "VARCHAR(36) NULL"},
        {"donations", "created_at", "DATETIME(6) NULL"},
        {"donations", "created_by", "VARCHAR(36) NULL"},
        {"donations", "approved_at", "DATETIME(6) NULL"},
        {"donations", "donor_representative", "VARCHAR(200) NULL"},
        {"donations", "notes", "TEXT NULL"},
        {"donations", "rejection_reason", "TEXT NULL"},
        {"distributions", "created_at", "DATETIME(6) NULL"},
        {"distributions", "created_by", "VARCHAR(36) NULL"},
        {"distributions", "approved_at", "DATETIME(6) NULL"},
        {"distributions", "acknowledgment_method", "VARCHAR(120) NULL"},
        {"distributions", "notes", "TEXT NULL"},
        {"distributions", "rejection_reason", "TEXT NULL"},
        {"audit_logs", "module", "VARCHAR(40) NULL"},
        {"audit_logs", "barangay_id", "VARCHAR(36) NULL"},
        {"audit_logs", "inventory_item_id", "VARCHAR(36) NULL"},
        {"audit_logs", "household_id", "VARCHAR(36) NULL"},
        {"audit_logs", "summary", "VARCHAR(255) NULL"},
        {"audit_logs", "movement", "VARCHAR(255) NULL"},
        {"audit_logs", "result", "VARCHAR(255) NULL"},
        {"audit_logs", "actor_name", "VARCHAR(150) NULL"},
        {"audit_logs", "actor_role", "VARCHAR(30) NULL"},
    };

    private static final String[][] AI_COLUMNS = {
        {"ai_feedback", "run_id", "VARCHAR(36) NULL"},
        {"ai_runs", "status", "VARCHAR(20) NOT NULL DEFAULT 'SUCCEEDED'"},
        {"ai_runs", "provider", "VARCHAR(40) NULL"},
        {"ai_runs", "model", "VARCHAR(150) NULL"},
        {"ai_runs", "elapsed_ms", "BIGINT NOT NULL DEFAULT 0"},
        {"ai_runs", "provider_elapsed_ms", "BIGINT NOT NULL DEFAULT 0"},
        {"ai_runs", "prompt_tokens", "BIGINT NOT NULL DEFAULT 0"},
        {"ai_runs", "completion_tokens", "BIGINT NOT NULL DEFAULT 0"},
    };

    private static final String[] TABLES = {
        "CREATE TABLE IF NOT EXISTS id_counters ("
            + "counter_key VARCHAR(60) NOT NULL PRIMARY KEY, "
            + "`last_value` BIGINT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS household_needs ("
            + "household_id VARCHAR(36) NOT NULL, "
            + "category VARCHAR(40) NOT NULL, "
            + "requested_quantity INT NOT NULL, "
            + "PRIMARY KEY (household_id, category), "
            + "CONSTRAINT fk_need_household FOREIGN KEY (household_id) "
            + "REFERENCES households (household_id) ON DELETE RESTRICT)",
        "CREATE TABLE IF NOT EXISTS ai_feedback ("
            + "feedback_id VARCHAR(36) NOT NULL PRIMARY KEY, barangay_id VARCHAR(36) NOT NULL, "
            + "account_id VARCHAR(36) NOT NULL, run_id VARCHAR(36) NULL, decision VARCHAR(30) NOT NULL, "
            + "recommendation TEXT NOT NULL, "
            + "comment TEXT NULL, created_at DATETIME(6) NOT NULL, "
            + "CONSTRAINT fk_ai_feedback_barangay FOREIGN KEY (barangay_id) REFERENCES barangays (barangay_id) ON DELETE RESTRICT, "
            + "CONSTRAINT fk_ai_feedback_account FOREIGN KEY (account_id) REFERENCES user_accounts (account_id) ON DELETE RESTRICT)",
        "CREATE TABLE IF NOT EXISTS ai_runs ("
            + "run_id VARCHAR(36) NOT NULL PRIMARY KEY, barangay_id VARCHAR(36) NOT NULL, "
            + "account_id VARCHAR(36) NULL, mode VARCHAR(40) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'SUCCEEDED', "
            + "provider VARCHAR(40) NULL, model VARCHAR(150) NULL, elapsed_ms BIGINT NOT NULL DEFAULT 0, "
            + "provider_elapsed_ms BIGINT NOT NULL DEFAULT 0, prompt_tokens BIGINT NOT NULL DEFAULT 0, "
            + "completion_tokens BIGINT NOT NULL DEFAULT 0, question TEXT NULL, "
            + "answer TEXT NOT NULL, tools_used TEXT NULL, created_at DATETIME(6) NOT NULL, "
            + "CONSTRAINT fk_ai_runs_barangay FOREIGN KEY (barangay_id) REFERENCES barangays (barangay_id) ON DELETE RESTRICT)",
        "CREATE TABLE IF NOT EXISTS ai_follow_ups ("
            + "follow_up_id VARCHAR(36) NOT NULL PRIMARY KEY, barangay_id VARCHAR(36) NOT NULL, "
            + "run_id VARCHAR(36) NULL, title VARCHAR(255) NOT NULL, detail TEXT NOT NULL, "
            + "status VARCHAR(30) NOT NULL, last_assessed_at DATETIME(6) NULL, "
            + "created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, "
            + "CONSTRAINT fk_ai_follow_barangay FOREIGN KEY (barangay_id) REFERENCES barangays (barangay_id) ON DELETE RESTRICT)",
        "CREATE TABLE IF NOT EXISTS ai_change_requests ("
            + "proposal_id VARCHAR(36) NOT NULL PRIMARY KEY, run_id VARCHAR(36) NULL, barangay_id VARCHAR(36) NOT NULL, "
            + "action VARCHAR(80) NOT NULL, summary VARCHAR(255) NOT NULL, original_request TEXT NOT NULL, "
            + "proposal_json LONGTEXT NOT NULL, before_value LONGTEXT NOT NULL, after_value LONGTEXT NOT NULL, "
            + "status VARCHAR(20) NOT NULL, proposed_by_account_id VARCHAR(36) NOT NULL, "
            + "proposed_by_name VARCHAR(150) NOT NULL, proposed_by_role VARCHAR(30) NOT NULL, "
            + "reviewed_by_account_id VARCHAR(36) NULL, reviewed_by_name VARCHAR(150) NULL, "
            + "reviewed_by_role VARCHAR(30) NULL, created_at DATETIME(6) NOT NULL, reviewed_at DATETIME(6) NULL, "
            + "applied_at DATETIME(6) NULL, outcome TEXT NULL, record_reference VARCHAR(100) NULL, "
            + "CONSTRAINT fk_ai_change_barangay FOREIGN KEY (barangay_id) REFERENCES barangays (barangay_id) ON DELETE RESTRICT, "
            + "CONSTRAINT fk_ai_change_run FOREIGN KEY (run_id) REFERENCES ai_runs (run_id) ON DELETE RESTRICT, "
            + "CONSTRAINT fk_ai_change_proposer FOREIGN KEY (proposed_by_account_id) REFERENCES user_accounts (account_id) ON DELETE RESTRICT, "
            + "CONSTRAINT fk_ai_change_reviewer FOREIGN KEY (reviewed_by_account_id) REFERENCES user_accounts (account_id) ON DELETE RESTRICT)",
                "CREATE TABLE IF NOT EXISTS stock_adjustments ("
            + "adjustment_id VARCHAR(36) NOT NULL PRIMARY KEY, "
            + "barangay_id VARCHAR(36) NOT NULL, "
            + "inventory_item_id VARCHAR(36) NOT NULL, "
            + "batch_id VARCHAR(100) NOT NULL, "
            + "adjustment_type VARCHAR(10) NOT NULL, "
            + "reason_category VARCHAR(20) NOT NULL, "
            + "quantity INT NOT NULL, "
            + "before_quantity INT NOT NULL, "
            + "after_quantity INT NOT NULL, "
            + "reason TEXT NOT NULL, "
            + "adjusted_on DATE NOT NULL, "
            + "created_at DATETIME(6) NOT NULL, "
            + "created_by VARCHAR(36) NULL, "
            + "CONSTRAINT chk_adjustment_quantity CHECK (quantity > 0), "
            + "CONSTRAINT fk_adjustment_item FOREIGN KEY (inventory_item_id, barangay_id) "
            + "REFERENCES inventory_items (inventory_item_id, barangay_id) ON DELETE RESTRICT, "
            + "CONSTRAINT fk_adjustment_batch FOREIGN KEY (batch_id, inventory_item_id) "
            + "REFERENCES inventory_batches (batch_id, inventory_item_id) ON DELETE RESTRICT)"
    };

    /** True when the base tables from schema.sql already exist. */
    public static boolean isInitialized(Connection connection) throws SQLException {
        return Db.intValue(connection, "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = 'barangays'") > 0;
    }

    /** Creates the base tables, triggers and views from database/schema.sql. */
    public static void createBaseSchema(Connection connection) throws SQLException {
        String script;
        try {
            script = readSchemaScript();
        } catch (IOException exception) {
            throw new SQLException("Could not read database/schema.sql: " + exception.getMessage(), exception);
        }
        SqlScriptRunner.run(connection, script);
    }

    private static String readSchemaScript() throws IOException {
        Path file = Paths.get("database", "schema.sql");
        if (Files.isReadable(file)) {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        }
        try (InputStream in = SchemaUpdater.class.getResourceAsStream("/database/schema.sql")) {
            if (in == null) {
                throw new IOException("database/schema.sql was not found next to the application.");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Adds anything the application needs that the database does not have yet. */
    public static synchronized void ensureUpToDate() throws SQLException {
        if (checked) {
            return;
        }
        try (Connection connection = DatabaseConnection.getConnection()) {
            ensureUpToDate(connection);
        }
        checked = true;
    }

    private static final String[] BASE_TABLES = { "barangays", "user_accounts", "inventory_items", "inventory_batches",
        "donors", "donations", "donation_items", "disasters", "evacuation_centers", "households", "family_members",
        "distributions", "distribution_items", "audit_logs" };

    public static void ensureUpToDate(Connection connection) throws SQLException {
        List<String> missing = new ArrayList<>();
        Set<String> tables = new HashSet<>(Db.list(connection, "SELECT LOWER(table_name) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE()", row -> row.getString(1)));
        for (String table : BASE_TABLES) {
            if (!tables.contains(table)) {
                missing.add(table);
            }
        }
        if (!missing.isEmpty()) {
            throw new SQLException("The database is missing these BarangayCare tables: " + String.join(", ", missing)
                    + ". Apply database/schema.sql to a clean database (or add the missing tables from it).");
        }
        Set<String> existing = new HashSet<>();
        for (String entry : Db.list(connection, "SELECT CONCAT(table_name, '.', column_name) "
                + "FROM information_schema.columns WHERE table_schema = DATABASE()",
                row -> row.getString(1).toLowerCase())) {
            existing.add(entry);
        }
        for (String[] column : COLUMNS) {
            if (!existing.contains((column[0] + "." + column[1]).toLowerCase())) {
                execute(connection, "ALTER TABLE " + column[0] + " ADD COLUMN " + column[1] + " " + column[2]);
            }
        }
        for (String table : TABLES) {
            execute(connection, table);
        }
        Set<String> aiColumns = new HashSet<>(Db.list(connection,
                "SELECT CONCAT(table_name, '.', column_name) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE()", row -> row.getString(1).toLowerCase()));
        for (String[] column : AI_COLUMNS) {
            if (!aiColumns.contains((column[0] + "." + column[1]).toLowerCase())) {
                execute(connection, "ALTER TABLE " + column[0] + " ADD COLUMN " + column[1] + " " + column[2]);
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            // 1060 = duplicate column name: another copy of the app added it first.
            if (exception.getErrorCode() != 1060) {
                throw new SQLException("Schema update failed for: " + sql + " (" + exception.getMessage() + ")",
                        exception);
            }
        }
    }
}
