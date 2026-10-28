package LIB;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Opens the SQLite database file and creates the tables the first time.
 * Run the program from the project root so the "database" folder can be found.
 */
public final class Database {
    private static final String DB_URL = "jdbc:sqlite:barangaycare.db";
    private static final Path SCHEMA_FILE = Paths.get("database", "schema.sql");
    private static final Path SEED_FILE = Paths.get("database", "seed_demo.sql");

    private Database() {
    }

    /**
     * Every change that touches stock or approvals runs while holding this lock, so two people
     * approving at the same moment cannot both spend the same batch.
     */
    public static final Object WRITE_LOCK = new Object();

    /** Opens a new connection with foreign keys turned on. Close it when done. */
    public static Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(DB_URL);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    /**
     * Makes the next readable ID such as DON-2026-0001. It looks at the highest ID already saved
     * with the same prefix and year.
     */
    public static String nextId(Connection connection, String table, String idColumn, String prefix)
            throws SQLException {
        String start = prefix + "-" + java.time.Year.now().getValue() + "-";
        String sql = "SELECT " + idColumn + " FROM " + table + " WHERE " + idColumn + " LIKE ?";
        int highest = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, start + "%");
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    try {
                        highest = Math.max(highest, Integer.parseInt(rs.getString(1).substring(start.length())));
                    } catch (NumberFormatException e) {
                        // not one of our numbered IDs; ignore it
                    }
                }
            }
        }
        return start + String.format("%04d", highest + 1);
    }

    /**
     * Runs a read-only SELECT and returns each row as a map of column label to value.
     * Used for list screens and reports; changes always go through the model classes.
     */
    public static List<Map<String, Object>> query(Connection connection, String sql, Object... params)
            throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int c = 1; c <= meta.getColumnCount(); c++) {
                        row.put(meta.getColumnLabel(c), rs.getObject(c));
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /** Same as query, but opens and closes its own connection. */
    public static List<Map<String, Object>> query(String sql, Object... params) throws SQLException {
        try (Connection connection = getConnection()) {
            return query(connection, sql, params);
        }
    }

    /** Current date and time in Philippine Standard Time, as stored in the database. */
    public static String now() {
        return java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Manila"))
                .withNano(0).toString().replace('T', ' ');
    }

    /** Today's date in Philippine Standard Time. */
    public static java.time.LocalDate today() {
        return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Manila"));
    }

    /** Creates the tables if they do not exist yet. Optionally loads the fictional demo data. */
    public static void initialize(boolean loadDemoData) throws SQLException, IOException {
        try (Connection connection = getConnection()) {
            if (!tableExists(connection, "barangays")) {
                runScript(connection, SCHEMA_FILE);
                if (loadDemoData) {
                    runScript(connection, SEED_FILE);
                }
            }
            upgrade(connection);
        }
    }

    /**
     * Adds columns that were introduced after the first version of the schema, so an existing
     * barangaycare.db keeps its data and still works.
     */
    private static void upgrade(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "users", "last_active", "TEXT");
        addColumnIfMissing(connection, "households", "evacuation_status", "TEXT");
        addColumnIfMissing(connection, "audit_logs", "barangay_id", "TEXT");
        addColumnIfMissing(connection, "audit_logs", "module", "TEXT");
        addColumnIfMissing(connection, "disasters", "closed_date", "TEXT");
        addColumnIfMissing(connection, "donations", "recorded_by", "TEXT");
        addColumnIfMissing(connection, "donations", "recorded_at", "TEXT");
        addColumnIfMissing(connection, "distributions", "recorded_by", "TEXT");
        addColumnIfMissing(connection, "distributions", "recorded_at", "TEXT");
        addColumnIfMissing(connection, "family_members", "is_pregnant", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, "households", "faced_number", "TEXT");
        addColumnIfMissing(connection, "distributions", "round_no", "INTEGER NOT NULL DEFAULT 1");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS relief_packs (pack_id TEXT PRIMARY KEY, "
                    + "barangay_id TEXT NOT NULL REFERENCES barangays(barangay_id), name TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS relief_pack_items (pack_id TEXT NOT NULL REFERENCES "
                    + "relief_packs(pack_id), item_id TEXT NOT NULL REFERENCES inventory_items(item_id), "
                    + "quantity INTEGER NOT NULL CHECK (quantity > 0), PRIMARY KEY (pack_id, item_id))");
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS one_faced_number_per_disaster "
                    + "ON households(disaster_id, faced_number) WHERE faced_number IS NOT NULL");
        }
    }

    private static void addColumnIfMissing(Connection connection, String table, String column, String type)
            throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (rs.getString("name").equalsIgnoreCase(column)) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        String sql = "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    /** Runs a .sql file one statement at a time, all-or-nothing. */
    private static void runScript(Connection connection, Path file) throws SQLException, IOException {
        List<String> lines = Files.readAllLines(file);
        StringBuilder current = new StringBuilder();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                current.append(line).append("\n");
                if (isEndOfStatement(current.toString(), trimmed)) {
                    statement.execute(current.toString());
                    current.setLength(0);
                }
            }
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    // A trigger contains ';' inside BEGIN ... END, so it only ends at the line ending with END;
    private static boolean isEndOfStatement(String statementSoFar, String lastLine) {
        if (statementSoFar.trim().toUpperCase().startsWith("CREATE TRIGGER")) {
            return lastLine.toUpperCase().endsWith("END;");
        }
        return lastLine.endsWith(";");
    }
}
