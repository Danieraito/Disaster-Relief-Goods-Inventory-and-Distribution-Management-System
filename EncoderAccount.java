public class EncoderAccount extends UserAccount {
    public EncoderAccount(String accountId, String name, String username) {
        super(accountId, name, username, Role.ENCODER);
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
