package LIB;

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
}
