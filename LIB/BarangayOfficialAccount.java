package LIB;

public class BarangayOfficialAccount extends UserAccount {
    public BarangayOfficialAccount(String accountId, String name, String username, String email,
            String password, Barangay barangay) {
        super(accountId, name, username, email, password, Role.BARANGAY_OFFICIAL, barangay);
    }

    BarangayOfficialAccount(String accountId, String name, String username, String email,
            Barangay barangay, byte[] passwordSalt, byte[] passwordHash, UserAccount.Status status) {
        super(accountId, name, username, email, Role.BARANGAY_OFFICIAL, barangay,
                passwordSalt, passwordHash, status);
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