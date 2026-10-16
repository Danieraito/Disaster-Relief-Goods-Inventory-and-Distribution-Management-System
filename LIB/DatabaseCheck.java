package LIB;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/** Quick check that the database is created and readable. Not part of the final system. */
public class DatabaseCheck {
    public static void main(String[] args) throws Exception {
        Database.initialize(true);

        try (Connection connection = Database.getConnection();
                Statement statement = connection.createStatement()) {

            System.out.println("--- Inventory stock ---");
            try (ResultSet rs = statement.executeQuery(
                    "SELECT item_id, item_name, quantity, is_low_stock FROM v_inventory_stock ORDER BY item_id")) {
                while (rs.next()) {
                    System.out.println(rs.getString("item_id") + " | " + rs.getString("item_name")
                            + " | quantity: " + rs.getInt("quantity")
                            + " | low stock: " + (rs.getInt("is_low_stock") == 1));
                }
            }

            System.out.println("--- Households ---");
            try (ResultSet rs = statement.executeQuery(
                    "SELECT name, household_size, senior_count, infant_count, pwd_count FROM v_household_summary")) {
                while (rs.next()) {
                    System.out.println(rs.getString("name") + " | members: " + rs.getInt("household_size")
                            + " | seniors: " + rs.getInt("senior_count")
                            + " | infants: " + rs.getInt("infant_count")
                            + " | PWD: " + rs.getInt("pwd_count"));
                }
            }
        }
        System.out.println("Database OK");
    }
}
