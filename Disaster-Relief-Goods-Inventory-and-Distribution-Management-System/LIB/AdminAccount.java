package LIB;

public class AdminAccount extends UserAccount {
    public AdminAccount(String accountId, String name, String username, String email, String password) {
        super(accountId, name, username, email, password, Role.ADMIN, null);
    }

    AdminAccount(String accountId, String name, String username, String email,
            byte[] passwordSalt, byte[] passwordHash, UserAccount.Status status) {
        super(accountId, name, username, email, Role.ADMIN, null, passwordSalt, passwordHash, status);
    }

    @Override
    public boolean canEnterRecords() {
        return true;
    }

    @Override
    public boolean canManageRecords() {
        return true;
    }

    @Override
    public boolean canApproveDistributions() {
        return true;
    }

    @Override
    public boolean canManageUsers() {
        return true;
    }

    @Override
    public boolean canExportReports() {
        return true;
    }
}
