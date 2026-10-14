public class UserAccount {
    public enum Role {
        ADMIN,
        BARANGAY_OFFICIAL,
        ENCODER
    }

    private String accountId;
    private String name;
    private String username;
    private Role role;

    public UserAccount(String accountId, String name, String username, Role role) {
        this.accountId = accountId;
        this.name = name;
        this.username = username;
        this.role = role;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
