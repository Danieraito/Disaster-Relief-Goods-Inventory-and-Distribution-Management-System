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
import java.util.List;

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

    /** Opens a new connection with foreign keys turned on. Close it when done. */
    public static Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(DB_URL);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
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
