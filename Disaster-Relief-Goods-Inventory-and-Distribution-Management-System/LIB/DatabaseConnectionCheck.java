package LIB;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseConnectionCheck {
    private DatabaseConnectionCheck() {
    }

    public static void main(String[] args) {
        try (Connection connection = DatabaseConnection.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT 1")) {
            if (!result.next() || result.getInt(1) != 1) {
                throw new SQLException("Database returned an unexpected test result.");
            }
            System.out.println("JDBC connection successful: "
                    + connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            System.err.println("JDBC connection test failed: " + exception.getMessage());
            System.exit(1);
        }
    }
}
