public class BarangayOfficialAccount extends UserAccount {
    public BarangayOfficialAccount(String accountId, String name, String username) {
        super(accountId, name, username, Role.BARANGAY_OFFICIAL);
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
        return false;
    }

    @Override
    public boolean canExportReports() {
        return true;
    }
}
