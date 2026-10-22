package LIB;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.UUID;

public class UserAccountController {
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository repository = new UserRepository();

    public UserAccount createAccount(String accountId, String name, String username, String email,
            String password, UserAccount.Role role, Barangay barangay, UserAccount administrator) {
        requireAdmin(administrator);
        if (role == null) {
            throw new IllegalArgumentException("Account role cannot be null.");
        }
        if (role == UserAccount.Role.ADMIN && barangay != null) {
            throw new IllegalArgumentException("Admin accounts are not assigned to a barangay workspace.");
        }

        switch (role) {
            case ADMIN:
                return new AdminAccount(accountId, name, username, email, password);
            case BARANGAY_OFFICIAL:
                return new BarangayOfficialAccount(accountId, name, username, email, password, barangay);
            case ENCODER:
                return new EncoderAccount(accountId, name, username, email, password, barangay);
            default:
                throw new IllegalArgumentException("Unsupported account role: " + role);
        }
    }

    public void setAccountStatus(UserAccount account, UserAccount.Status status,
            UserAccount administrator) {
        requireAdmin(administrator);
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null.");
        }
        account.setStatus(status);
    }

    // ----- database-backed account management used by the User administration screen -----

    /** Creates the account with a temporary password and returns that password to show once. */
    public String invite(String name, String email, UserAccount.Role role, Barangay barangay,
            UserAccount administrator) throws SQLException {
        requireAdmin(administrator);
        String cleanEmail = email == null ? "" : email.trim();
        if (!cleanEmail.contains("@") || cleanEmail.startsWith("@") || cleanEmail.endsWith("@")) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        String password = generateTemporaryPassword();
        UserAccount account = createAccount(UUID.randomUUID().toString(), name, cleanEmail, cleanEmail, password,
                role, role == UserAccount.Role.ADMIN ? null : barangay, administrator);
        repository.create(account, administrator);
        return password;
    }

    public void changeRole(UserAccount target, UserAccount.Role role, Barangay barangay, String reason,
            UserAccount administrator) throws SQLException {
        requireAdmin(administrator);
        requireReason(reason);
        repository.changeRole(target, role, barangay, reason, administrator);
    }

    public void deactivate(UserAccount target, String reason, UserAccount administrator) throws SQLException {
        requireAdmin(administrator);
        requireReason(reason);
        repository.setStatus(target, UserAccount.Status.INACTIVE, reason, administrator);
    }

    public void reactivate(UserAccount target, String reason, UserAccount administrator) throws SQLException {
        requireAdmin(administrator);
        requireReason(reason);
        repository.setStatus(target, UserAccount.Status.ACTIVE, reason, administrator);
    }

    /** Issues a new temporary password and returns it to show once. */
    public String resetPassword(UserAccount target, UserAccount administrator) throws SQLException {
        requireAdmin(administrator);
        String password = generateTemporaryPassword();
        repository.resetPassword(target, password, administrator);
        return password;
    }

    public static String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder();
        for (int index = 0; index < 10; index++) {
            password.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }

    private void requireAdmin(UserAccount administrator) {
        if (administrator == null || !administrator.isActive() || !administrator.canManageUsers()) {
            throw new SecurityException("User does not have permission to manage accounts.");
        }
    }

    private void requireReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter the reason for this access change.");
        }
    }
}
