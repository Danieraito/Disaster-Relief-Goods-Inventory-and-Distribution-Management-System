public class AdminAccount extends UserAccount {
    public AdminAccount(String accountId, String name, String username) {
        super(accountId, name, username, Role.ADMIN);
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
