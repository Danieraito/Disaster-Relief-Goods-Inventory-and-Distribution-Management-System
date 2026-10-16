package LIB;

public class EncoderAccount extends UserAccount {
    public EncoderAccount(String accountId, String name, String username, String email,
            String password, Barangay barangay) {
        super(accountId, name, username, email, password, Role.ENCODER, barangay);
    }

    EncoderAccount(String accountId, String name, String username, String email,
            Barangay barangay, byte[] passwordSalt, byte[] passwordHash, UserAccount.Status status) {
        super(accountId, name, username, email, Role.ENCODER, barangay,
                passwordSalt, passwordHash, status);
    }

    @Override
    public boolean canEnterRecords() {
        return true;
    }

    @Override
    public boolean canManageRecords() {
        return false;
    }

    @Override
    public boolean canApproveDistributions() {
        return false;
    }

    @Override
    public boolean canManageUsers() {
        return false;
    }

    @Override
    public boolean canExportReports() {
        return false;
    }
}