package LIB;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class LoginController {
    public Optional<UserAccount> authenticate(List<UserAccount> accounts, String username, String password) {
        Objects.requireNonNull(accounts, "accounts");
        if (username == null || username.trim().isEmpty() || password == null) {
            return Optional.empty();
        }
        for (UserAccount account : accounts) {
            if (account != null && account.isActive()
                    && account.getUsername().equals(username.trim())
                    && account.verifyPassword(password)) {
                return Optional.of(account);
            }
        }
        return Optional.empty();
    }

    /** Database sign-in used by the login screen: email or username, the chosen role and workspace. */
    public Optional<UserAccount> signIn(String login, String password, UserAccount.Role role, Barangay workspace)
            throws SQLException {
        return new UserRepository().authenticate(login, password, role,
                workspace == null ? null : workspace.getBarangayId());
    }
}
