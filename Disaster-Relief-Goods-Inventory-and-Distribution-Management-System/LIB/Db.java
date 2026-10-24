package LIB;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Small JDBC helper used by the repositories so each query stays one readable line.
 * Every method takes the Connection explicitly so several statements can share one transaction.
 */
public final class Db {
    public interface RowMapper<T> {
        T map(ResultSet row) throws SQLException;
    }

    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private Db() {
    }

    /** Runs the work inside one database transaction (commit on success, rollback on any failure). */
    public static <T> T transaction(Work<T> work) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    /** Runs read-only work on a pooled connection. */
    public static <T> T read(Work<T> work) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return work.run(connection);
        }
    }

    public static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int index = 0; index < params.length; index++) {
            Object value = params[index];
            int position = index + 1;
            if (value == null) {
                statement.setObject(position, null);
            } else if (value instanceof LocalDate) {
                statement.setDate(position, Date.valueOf((LocalDate) value));
            } else if (value instanceof LocalDateTime) {
                statement.setTimestamp(position, Timestamp.valueOf((LocalDateTime) value));
            } else if (value instanceof Boolean) {
                statement.setBoolean(position, (Boolean) value);
            } else if (value instanceof Enum) {
                statement.setString(position, ((Enum<?>) value).name());
            } else {
                statement.setObject(position, value);
            }
        }
    }

    public static int update(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate();
        }
    }

    public static <T> List<T> list(Connection connection, String sql, RowMapper<T> mapper, Object... params)
            throws SQLException {
        List<T> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    rows.add(mapper.map(results));
                }
            }
        }
        return rows;
    }

    public static <T> T one(Connection connection, String sql, RowMapper<T> mapper, Object... params)
            throws SQLException {
        List<T> rows = list(connection, sql, mapper, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static int intValue(Connection connection, String sql, Object... params) throws SQLException {
        Integer value = one(connection, sql, row -> {
            int number = row.getInt(1);
            return row.wasNull() ? 0 : number;
        }, params);
        return value == null ? 0 : value;
    }

    public static long longValue(Connection connection, String sql, Object... params) throws SQLException {
        Long value = one(connection, sql, row -> {
            long number = row.getLong(1);
            return row.wasNull() ? 0L : number;
        }, params);
        return value == null ? 0L : value;
    }

    public static LocalDate date(ResultSet row, String column) throws SQLException {
        Date value = row.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    public static LocalDateTime dateTime(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    public static String text(ResultSet row, String column) throws SQLException {
        String value = row.getString(column);
        return value == null ? "" : value;
    }
}
