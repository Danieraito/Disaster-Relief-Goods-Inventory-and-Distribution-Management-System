package LIB;

public class UserAccountController {
    public UserAccount createAccount(String accountId, String name, String username, String email,
            String password, UserAccount.Role role, Barangay barangay, UserAccount administrator) {
        if (administrator == null || !administrator.isActive() || !administrator.canManageUsers()) {
            throw new SecurityException("User does not have permission to manage accounts.");
        }
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
        if (administrator == null || !administrator.isActive() || !administrator.canManageUsers()) {
            throw new SecurityException("User does not have permission to manage accounts.");
        }
        if (account == null) {
            throw new IllegalArgumentException("Account cannot be null.");
        }
        account.setStatus(status);
    }
}
