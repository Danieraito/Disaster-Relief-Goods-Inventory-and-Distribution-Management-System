public abstract class UserAccount {
    public enum Role {
        ADMIN,
        BARANGAY_OFFICIAL,
        ENCODER
    }

    private final String accountId;
    private final String name;
    private final String username;
    private final Role role;

    protected UserAccount(String accountId, String name, String username, Role role) {
        this.accountId = accountId;
        this.name = name;
        this.username = username;
        this.role = role;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getName() {
        return name;
    }

    public String getUsername() {
        return username;
    }

    public Role getRole() {
        return role;
    }

    public abstract boolean canEnterRecords();

    public abstract boolean canManageRecords();

    public abstract boolean canApproveDistributions();

    public abstract boolean canManageUsers();

    public abstract boolean canExportReports();
}
