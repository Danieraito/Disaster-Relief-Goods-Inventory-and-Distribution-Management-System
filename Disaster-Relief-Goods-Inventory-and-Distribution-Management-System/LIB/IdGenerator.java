package LIB;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Produces readable, sequential reference numbers such as DON-2026-0088 or ITM-003.
 * The counter row is incremented inside the caller's transaction, so numbers never repeat
 * and a rolled-back save does not burn a number.
 */
public final class IdGenerator {
    private IdGenerator() {
    }

    /** Example: next(connection, "DON", true, 4) -> DON-2026-0001. */
    public static String next(Connection connection, String prefix, boolean withYear, int width)
            throws SQLException {
        String key = withYear ? prefix + "-" + Time.today().getYear() : prefix;
        Db.update(connection, "INSERT INTO id_counters (counter_key, `last_value`) VALUES (?, LAST_INSERT_ID(1)) "
                + "ON DUPLICATE KEY UPDATE `last_value` = LAST_INSERT_ID(`last_value` + 1)", key);
        long number = Db.longValue(connection, "SELECT LAST_INSERT_ID()");
        return key + "-" + String.format("%0" + width + "d", number);
    }
}
