package LIB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Saves and loads user accounts. Only the password hash and salt are stored. */
public class UserRepository {

    public void save(UserAccount account) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                save(connection, account);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** Records that the user just used the system (shown on the User Administration screen). */
    public void touchLastActive(String accountId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users SET last_active = ? WHERE account_id = ?")) {
            statement.setString(1, Database.now());
            statement.setString(2, accountId);
            statement.executeUpdate();
        }
    }

    /** Loads one account using an open connection, or null. */
    public static UserAccount findById(Connection connection, String accountId) throws SQLException {
        if (accountId == null) {
            return null;
        }
        try (PreparedStatement statement = connection.prepareStatement(SELECT + " WHERE account_id = ?")) {
            statement.setString(1, accountId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? read(connection, rs) : null;
            }
        }
    }

    /** Saves an account using an open connection (part of a bigger transaction). */
    public static void save(Connection connection, UserAccount account) throws SQLException {
                if (account.getBarangay() != null) {
                    BarangayRepository.save(connection, account.getBarangay());
                }
                String sql = "INSERT INTO users (account_id, name, username, email, role, barangay_id, "
                        + "password_salt, password_hash, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(account_id) DO UPDATE SET name = excluded.name, "
                        + "username = excluded.username, email = excluded.email, role = excluded.role, "
                        + "barangay_id = excluded.barangay_id, password_salt = excluded.password_salt, "
                        + "password_hash = excluded.password_hash, status = excluded.status";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, account.getAccountId());
                    statement.setString(2, account.getName());
                    statement.setString(3, account.getUsername());
                    statement.setString(4, account.getEmail());
                    statement.setString(5, account.getRole().name());
                    if (account.getBarangay() == null) {
                        statement.setNull(6, Types.VARCHAR);
                    } else {
                        statement.setString(6, account.getBarangay().getBarangayId());
                    }
                    statement.setBytes(7, account.getPasswordSaltCopy());
                    statement.setBytes(8, account.getPasswordHashCopy());
                    statement.setString(9, account.getStatus().name());
                    statement.executeUpdate();
                }
    }

    public Optional<UserAccount> findByUsername(String username) throws SQLException {
        if (username == null) {
            return Optional.empty();
        }
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT + " WHERE username = ?")) {
            statement.setString(1, username.trim());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(read(connection, rs)) : Optional.empty();
            }
        }
    }

    public Optional<UserAccount> findByEmail(String email) throws SQLException {
        if (email == null) {
            return Optional.empty();
        }
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        SELECT + " WHERE lower(email) = lower(?)")) {
            statement.setString(1, email.trim());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(read(connection, rs)) : Optional.empty();
            }
        }
    }

    /** Same as authenticate, but the person signs in with their email address. */
    public Optional<UserAccount> authenticateByEmail(String email, String password) throws SQLException {
        Optional<UserAccount> account = findByEmail(email);
        if (account.isPresent() && account.get().isActive() && account.get().verifyPassword(password)) {
            return account;
        }
        return Optional.empty();
    }

    public Optional<UserAccount> findById(String accountId) throws SQLException {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT + " WHERE account_id = ?")) {
            statement.setString(1, accountId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(read(connection, rs)) : Optional.empty();
            }
        }
    }

    public List<UserAccount> findAll() throws SQLException {
        List<UserAccount> accounts = new ArrayList<>();
        try (Connection connection = Database.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(SELECT + " ORDER BY name")) {
            while (rs.next()) {
                accounts.add(read(connection, rs));
            }
        }
        return accounts;
    }

    /** Looks the user up in the database and checks the password. Empty if login fails. */
    public Optional<UserAccount> authenticate(String username, String password) throws SQLException {
        Optional<UserAccount> account = findByUsername(username);
        if (account.isPresent() && account.get().isActive() && account.get().verifyPassword(password)) {
            return account;
        }
        return Optional.empty();
    }

    private static final String SELECT = "SELECT account_id, name, username, email, role, barangay_id, "
            + "password_salt, password_hash, status FROM users";

    private static UserAccount read(Connection connection, ResultSet rs) throws SQLException {
        Barangay barangay = BarangayRepository.findById(connection, rs.getString("barangay_id"));
        return UserAccount.restore(
                rs.getString("account_id"),
                rs.getString("name"),
                rs.getString("username"),
                rs.getString("email"),
                UserAccount.Role.valueOf(rs.getString("role")),
                barangay,
                rs.getBytes("password_salt"),
                rs.getBytes("password_hash"),
                UserAccount.Status.valueOf(rs.getString("status")));
    }
}