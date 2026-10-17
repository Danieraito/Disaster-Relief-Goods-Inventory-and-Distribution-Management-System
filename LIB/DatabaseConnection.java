package LIB;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnection {
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/disaster_relief_inventory";

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        String url = System.getenv("DB_URL");
        String username = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        if (url == null || url.trim().isEmpty()) {
            url = DEFAULT_URL;
        }
        if (username == null || username.trim().isEmpty()) {
            throw new SQLException("DB_USER environment variable is required.");
        }
        if (password == null || password.isEmpty()) {
            throw new SQLException("DB_PASSWORD environment variable is required.");
        }

        return DriverManager.getConnection(url, username, password);
    }
}
