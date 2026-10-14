package LIB;

public class BarangayOfficialAccount extends UserAccount {
    public BarangayOfficialAccount(String accountId, String name, String username, String email,
            String password, Barangay barangay) {
        super(accountId, name, username, email, password, Role.BARANGAY_OFFICIAL, barangay);
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
