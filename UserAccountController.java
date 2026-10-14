public class UserAccountController {
    public UserAccount createAccount(String accountId, String name, String username,
            UserAccount.Role role, UserAccount administrator) {
        if (administrator == null || !administrator.canManageUsers()) {
            throw new SecurityException("User does not have permission to manage accounts.");
        }
        if (role == null) {
            throw new IllegalArgumentException("Account role cannot be null.");
        }

        switch (role) {
            case ADMIN:
                return new AdminAccount(accountId, name, username);
            case BARANGAY_OFFICIAL:
                return new BarangayOfficialAccount(accountId, name, username);
            case ENCODER:
                return new EncoderAccount(accountId, name, username);
            default:
                throw new IllegalArgumentException("Unsupported account role: " + role);
        }
    }
}
