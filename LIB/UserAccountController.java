package LIB;

import java.util.List;

public class UserAccountController {
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

    /**
     * Deactivates or reactivates an account. An Admin cannot deactivate their own account,
     * and the last active Admin can never be deactivated.
     */
    public void setAccountStatus(UserAccount account, UserAccount.Status status,
            UserAccount administrator, List<UserAccount> allAccounts) {
        requireAdmin(administrator);
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null.");
        }
        if (status == UserAccount.Status.INACTIVE) {
            if (account.getAccountId().equals(administrator.getAccountId())) {
                throw new IllegalArgumentException("You cannot deactivate your own account.");
            }
            if (account.getRole() == UserAccount.Role.ADMIN && countActiveAdmins(allAccounts) <= 1) {
                throw new IllegalArgumentException("Keep at least one active Admin account.");
            }
        }
        account.setStatus(status);
    }

    /**
     * Returns the same account with a new role. The password hash, status and ID stay the same.
     * Admins have no barangay; other roles need one.
     */
    public UserAccount changeRole(UserAccount account, UserAccount.Role newRole, Barangay barangay,
            UserAccount administrator, List<UserAccount> allAccounts) {
        requireAdmin(administrator);
        if (account == null || newRole == null) {
            throw new IllegalArgumentException("Account and role are required.");
        }
        if (account.getRole() == UserAccount.Role.ADMIN && newRole != UserAccount.Role.ADMIN
                && account.isActive() && countActiveAdmins(allAccounts) <= 1) {
            throw new IllegalArgumentException("Keep at least one active Admin account.");
        }
        Barangay workspace = newRole == UserAccount.Role.ADMIN ? null : barangay;
        if (newRole != UserAccount.Role.ADMIN && workspace == null) {
            throw new IllegalArgumentException("Choose a barangay workspace for this role.");
        }
        return UserAccount.restore(account.getAccountId(), account.getName(), account.getUsername(),
                account.getEmail(), newRole, workspace, account.getPasswordSaltCopy(),
                account.getPasswordHashCopy(), account.getStatus());
    }

    /** Returns the same account with a new password (a new salt and hash are generated). */
    public UserAccount resetPassword(UserAccount account, String newPassword, UserAccount administrator) {
        requireAdmin(administrator);
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null.");
        }
        UserAccount updated = createAccount(account.getAccountId(), account.getName(), account.getUsername(),
                account.getEmail(), newPassword, account.getRole(), account.getBarangay(), administrator);
        updated.setStatus(account.getStatus());
        return updated;
    }

    private static int countActiveAdmins(List<UserAccount> accounts) {
        int count = 0;
        if (accounts != null) {
            for (UserAccount account : accounts) {
                if (account.getRole() == UserAccount.Role.ADMIN && account.isActive()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void requireAdmin(UserAccount administrator) {
        if (administrator == null || !administrator.isActive() || !administrator.canManageUsers()) {
            throw new SecurityException("User does not have permission to manage accounts.");
        }
    }
}
