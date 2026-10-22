package LIB;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Runs a .sql file that may contain DELIMITER lines (needed for the trigger section of schema.sql). */
public final class SqlScriptRunner {
    private SqlScriptRunner() {
    }

    public static void run(Connection connection, String script) throws SQLException {
        String delimiter = ";";
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.trim();
            if (current.length() == 0 && (trimmed.isEmpty() || trimmed.startsWith("--"))) {
                continue;
            }
            if (current.length() == 0 && trimmed.toUpperCase().startsWith("DELIMITER ")) {
                delimiter = trimmed.substring("DELIMITER ".length()).trim();
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(delimiter)) {
                String statement = current.toString();
                statement = statement.substring(0, statement.lastIndexOf(delimiter)).trim();
                current.setLength(0);
                if (!statement.isEmpty()) {
                    try (Statement sql = connection.createStatement()) {
                        sql.execute(statement);
                    }
                }
            }
        }
    }
}
