package LIB;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class UserRepository {
    private static final String COLUMNS = "account_id, name, username, email, role, barangay_id, password_salt, "
            + "password_hash, status, last_login_at";

    /** Old entry point: sign in with a username only (any role, any workspace). */
    public Optional<UserAccount> authenticate(String username, String password) throws SQLException {
        return authenticate(username, password, null, null);
    }

    /**
     * Signs in with an email address or username. When a role is given the account must have that role;
     * when a workspace is given a non-admin account must belong to it (admins may open any workspace).
     */
    public Optional<UserAccount> authenticate(String login, String password, UserAccount.Role role,
            String barangayId) throws SQLException {
        if (login == null || login.trim().isEmpty() || password == null) {
            return Optional.empty();
        }
        return Db.read(connection -> {
            UserAccount account = Db.one(connection, "SELECT " + COLUMNS + " FROM user_accounts "
                    + "WHERE LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?)",
                    row -> read(connection, row), login.trim(), login.trim());
            if (account == null || !account.isActive() || !account.verifyPassword(password)) {
                return Optional.empty();
            }
            if (role != null && account.getRole() != role) {
                return Optional.empty();
            }
            if (barangayId != null && account.getRole() != UserAccount.Role.ADMIN
                    && !account.getBarangay().getBarangayId().equals(barangayId)) {
                return Optional.empty();
            }
            Db.update(connection, "UPDATE user_accounts SET last_login_at = ? WHERE account_id = ?",
                    Time.now(), account.getAccountId());
            return Optional.of(account);
        });
    }

    public Optional<UserAccount> findById(String accountId) throws SQLException {
        return Db.read(connection -> Optional.ofNullable(Db.one(connection, "SELECT " + COLUMNS
                + " FROM user_accounts WHERE account_id = ?", row -> read(connection, row), accountId)));
    }

    public List<UserAccount> findAll() throws SQLException {
        return Db.read(connection -> Db.list(connection, "SELECT " + COLUMNS
                + " FROM user_accounts ORDER BY FIELD(role, 'ADMIN', 'BARANGAY_OFFICIAL', 'ENCODER'), name",
                row -> read(connection, row)));
    }

    /** Loads an account on a connection that is already open (used inside other repositories' reads). */
    UserAccount findByIdWith(Connection connection, String accountId) throws SQLException {
        return Db.one(connection, "SELECT " + COLUMNS + " FROM user_accounts WHERE account_id = ?",
                row -> read(connection, row), accountId);
    }

    public boolean isEmpty() throws SQLException {
        return Db.read(connection -> Db.intValue(connection, "SELECT COUNT(*) FROM user_accounts") == 0);
    }

    public int countActiveAdmins() throws SQLException {
        return Db.read(connection -> countActiveAdmins(connection));
    }

    private static int countActiveAdmins(Connection connection) throws SQLException {
        return Db.intValue(connection, "SELECT COUNT(*) FROM user_accounts WHERE role = 'ADMIN' AND status = 'ACTIVE'");
    }

    /** Saves a brand-new account. Pass the creating admin so the audit ledger shows who invited them. */
    public void create(UserAccount account, UserAccount actor) throws SQLException {
        Db.transaction(connection -> {
            if (Db.intValue(connection, "SELECT COUNT(*) FROM user_accounts WHERE LOWER(username) = LOWER(?)",
                    account.getUsername()) > 0) {
                throw new IllegalArgumentException("That username is already used by another account.");
            }
            if (Db.intValue(connection, "SELECT COUNT(*) FROM user_accounts WHERE LOWER(email) = LOWER(?)",
                    account.getEmail()) > 0) {
                throw new IllegalArgumentException("That email address is already used by another account.");
            }
            Db.update(connection, "INSERT INTO user_accounts (account_id, name, username, email, role, status, "
                    + "barangay_id, password_salt, password_hash, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    account.getAccountId(), account.getName(), account.getUsername(), account.getEmail(),
                    account.getRole(), account.getStatus(),
                    account.getBarangay() == null ? null : account.getBarangay().getBarangayId(),
                    account.getPasswordSaltCopy(), account.getPasswordHashCopy(), Time.now());
            if (actor != null) {
                AuditRepository.record(connection, actor, new AuditRepository.Entry("USER_INVITED",
                        AuditRepository.MODULE_USERS).reference(account.getEmail())
                        .summary(account.getName() + " · " + account.getRoleLabel())
                        .reason("Account created").result("Invitation issued")
                        .barangay(account.getBarangay() == null ? null : account.getBarangay().getBarangayId()));
            }
            return null;
        });
    }

    public void changeRole(UserAccount target, UserAccount.Role role, Barangay barangay, String reason,
            UserAccount actor) throws SQLException {
        if (role != UserAccount.Role.ADMIN && barangay == null) {
            throw new IllegalArgumentException("Choose a barangay workspace for this role.");
        }
        Db.transaction(connection -> {
            if (target.getRole() == UserAccount.Role.ADMIN && role != UserAccount.Role.ADMIN
                    && target.isActive() && countActiveAdmins(connection) <= 1) {
                throw new IllegalStateException("Keep at least one active Admin. Assign another Admin first.");
            }
            Db.update(connection, "UPDATE user_accounts SET role = ?, barangay_id = ? WHERE account_id = ?",
                    role, role == UserAccount.Role.ADMIN ? null : barangay.getBarangayId(), target.getAccountId());
            AuditRepository.record(connection, actor, new AuditRepository.Entry("USER_ROLE_CHANGED",
                    AuditRepository.MODULE_USERS).reference(target.getEmail()).summary(target.getName())
                    .before(target.getRoleLabel()).after(roleLabel(role)).reason(reason).result("Role updated"));
            return null;
        });
    }

    public void setStatus(UserAccount target, UserAccount.Status status, String reason, UserAccount actor)
            throws SQLException {
        Db.transaction(connection -> {
            if (status == UserAccount.Status.INACTIVE && target.getRole() == UserAccount.Role.ADMIN
                    && target.isActive() && countActiveAdmins(connection) <= 1) {
                throw new IllegalStateException("Keep at least one active Admin. Assign another Admin first.");
            }
            Db.update(connection, "UPDATE user_accounts SET status = ? WHERE account_id = ?", status,
                    target.getAccountId());
            AuditRepository.record(connection, actor, new AuditRepository.Entry(
                    status == UserAccount.Status.ACTIVE ? "USER_REACTIVATED" : "USER_DEACTIVATED",
                    AuditRepository.MODULE_USERS).reference(target.getEmail()).summary(target.getName())
                    .before(target.getStatus().name()).after(status.name()).reason(reason)
                    .result("Past transactions remain attributed"));
            return null;
        });
    }

    public void resetPassword(UserAccount target, String newPassword, UserAccount actor) throws SQLException {
        byte[][] credentials = UserAccount.newCredentials(newPassword);
        Db.transaction(connection -> {
            Db.update(connection, "UPDATE user_accounts SET password_salt = ?, password_hash = ? WHERE account_id = ?",
                    credentials[0], credentials[1], target.getAccountId());
            AuditRepository.record(connection, actor, new AuditRepository.Entry("PASSWORD_RESET",
                    AuditRepository.MODULE_USERS).reference(target.getEmail()).summary(target.getName())
                    .reason("Temporary password issued").result("Password replaced"));
            return null;
        });
    }

    public void changeOwnPassword(UserAccount account, String currentPassword, String newPassword)
            throws SQLException {
        if (!account.verifyPassword(currentPassword)) {
            throw new IllegalArgumentException("The current password is not correct.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Use at least 8 characters for the new password.");
        }
        byte[][] credentials = UserAccount.newCredentials(newPassword);
        Db.transaction(connection -> {
            Db.update(connection, "UPDATE user_accounts SET password_salt = ?, password_hash = ? WHERE account_id = ?",
                    credentials[0], credentials[1], account.getAccountId());
            AuditRepository.record(connection, account, new AuditRepository.Entry("PASSWORD_RESET",
                    AuditRepository.MODULE_USERS).reference(account.getEmail()).summary(account.getName())
                    .reason("Changed by the account owner").result("Password replaced"));
            return null;
        });
    }

    private static String roleLabel(UserAccount.Role role) {
        switch (role) {
            case ADMIN:
                return "Admin";
            case BARANGAY_OFFICIAL:
                return "Barangay Official";
            default:
                return "Encoder";
        }
    }

    private static UserAccount read(Connection connection, ResultSet row) throws SQLException {
        Barangay barangay = BarangayRepository.findById(connection, row.getString("barangay_id"));
        UserAccount account = UserAccount.restore(row.getString("account_id"), row.getString("name"),
                row.getString("username"), row.getString("email"), UserAccount.Role.valueOf(row.getString("role")),
                barangay, row.getBytes("password_salt"), row.getBytes("password_hash"),
                UserAccount.Status.valueOf(row.getString("status")));
        account.setLastLoginAt(Db.dateTime(row, "last_login_at"));
        return account;
    }
}
