package LIB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class UserRepository {
    public Optional<UserAccount> authenticate(String username, String password) throws SQLException {
        if (username == null || username.trim().isEmpty() || password == null) {
            return Optional.empty();
        }

        String sql = "SELECT account_id, name, username, email, role, barangay_id, password_salt, "
                + "password_hash, status FROM user_accounts WHERE username = ?";
        try (Connection connection = DatabaseConnection.getConnection()) {
            String accountId;
            String name;
            String storedUsername;
            String email;
            UserAccount.Role role;
            String barangayId;
            byte[] salt;
            byte[] hash;
            UserAccount.Status status;

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, username.trim());
                try (ResultSet results = statement.executeQuery()) {
                    if (!results.next()) {
                        return Optional.empty();
                    }
                    accountId = results.getString("account_id");
                    name = results.getString("name");
                    storedUsername = results.getString("username");
                    email = results.getString("email");
                    role = UserAccount.Role.valueOf(results.getString("role"));
                    barangayId = results.getString("barangay_id");
                    salt = results.getBytes("password_salt");
                    hash = results.getBytes("password_hash");
                    status = UserAccount.Status.valueOf(results.getString("status"));
                }
            }

            Barangay barangay = BarangayRepository.findById(connection, barangayId);
            UserAccount account = UserAccount.restore(accountId, name, storedUsername, email,
                    role, barangay, salt, hash, status);
            return account.isActive() && account.verifyPassword(password)
                    ? Optional.of(account)
                    : Optional.empty();
        }
    }
}