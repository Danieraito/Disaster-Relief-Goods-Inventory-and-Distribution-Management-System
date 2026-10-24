package LIB;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Objects;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.SecretKey;
import java.security.spec.InvalidKeySpecException;

public abstract class UserAccount implements BarangayScoped {
    public enum Role {
        ADMIN,
        BARANGAY_OFFICIAL,
        ENCODER
    }

    public enum Status {
        ACTIVE,
        INACTIVE
    }

    private static final int PASSWORD_ITERATIONS = 210_000;
    private static final int PASSWORD_KEY_LENGTH = 256;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String accountId;
    private final String name;
    private final String username;
    private final String email;
    private final Role role;
    private final Barangay barangay;
    private final byte[] passwordSalt;
    private final byte[] passwordHash;
    private Status status = Status.ACTIVE;
    private LocalDateTime lastLoginAt;

    protected UserAccount(String accountId, String name, String username, String email,
            String password, Role role, Barangay barangay) {
        this.accountId = requireText(accountId, "accountId");
        this.name = requireText(name, "name");
        this.username = requireText(username, "username");
        this.email = requireText(email, "email");
        if (!this.email.contains("@")) {
            throw new IllegalArgumentException("Email address must contain @.");
        }
        this.role = Objects.requireNonNull(role, "role");
        if (role != Role.ADMIN && barangay == null) {
            throw new IllegalArgumentException("Non-admin accounts must belong to a barangay.");
        }
        this.barangay = barangay;
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        this.passwordSalt = salt;
        this.passwordHash = hashPassword(requirePassword(password), salt);
    }

    protected UserAccount(String accountId, String name, String username, String email,
            Role role, Barangay barangay, byte[] passwordSalt, byte[] passwordHash, Status status) {
        this.accountId = requireText(accountId, "accountId");
        this.name = requireText(name, "name");
        this.username = requireText(username, "username");
        this.email = requireText(email, "email");
        if (!this.email.contains("@")) {
            throw new IllegalArgumentException("Email address must contain @.");
        }
        this.role = Objects.requireNonNull(role, "role");
        if (role != Role.ADMIN && barangay == null) {
            throw new IllegalArgumentException("Non-admin accounts must belong to a barangay.");
        }
        this.barangay = barangay;
        this.passwordSalt = Objects.requireNonNull(passwordSalt, "passwordSalt").clone();
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash").clone();
        this.status = Objects.requireNonNull(status, "status");
    }

    static UserAccount restore(String accountId, String name, String username, String email,
            Role role, Barangay barangay, byte[] passwordSalt, byte[] passwordHash, Status status) {
        Objects.requireNonNull(role, "role");
        switch (role) {
            case ADMIN:
                return new AdminAccount(accountId, name, username, email, passwordSalt, passwordHash, status);
            case BARANGAY_OFFICIAL:
                return new BarangayOfficialAccount(accountId, name, username, email, barangay,
                        passwordSalt, passwordHash, status);
            case ENCODER:
                return new EncoderAccount(accountId, name, username, email, barangay,
                        passwordSalt, passwordHash, status);
            default:
                throw new IllegalArgumentException("Unsupported account role: " + role);
        }
    }

    /** Creates a fresh salt and PBKDF2 hash for a new or reset password: [0] = salt, [1] = hash. */
    static byte[][] newCredentials(String password) {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        return new byte[][] { salt, hashPassword(requirePassword(password), salt) };
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public String getRoleLabel() {
        switch (role) {
            case ADMIN:
                return "Admin";
            case BARANGAY_OFFICIAL:
                return "Barangay Official";
            default:
                return "Encoder";
        }
    }

    byte[] getPasswordSaltCopy() {
        return passwordSalt.clone();
    }

    byte[] getPasswordHashCopy() {
        return passwordHash.clone();
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

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public Barangay getBarangay() {
        return barangay;
    }

    public Status getStatus() {
        return status;
    }

    void setStatus(Status status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean verifyPassword(String password) {
        if (password == null) {
            return false;
        }
        return MessageDigest.isEqual(passwordHash, hashPassword(password, passwordSalt));
    }

    public boolean canAccessBarangay(Barangay target) {
        return role == Role.ADMIN || belongsToBarangay(target);
    }

    public boolean canAccess(BarangayScoped record) {
        return record != null && canAccessBarangay(record.getBarangay());
    }

    public abstract boolean canEnterRecords();

    public abstract boolean canManageRecords();

    public abstract boolean canApproveDistributions();

    public abstract boolean canManageUsers();

    public abstract boolean canExportReports();

    private static byte[] hashPassword(String password, byte[] salt) {
        char[] passwordCharacters = password.toCharArray();
        PBEKeySpec keySpec = new PBEKeySpec(passwordCharacters, salt,
                PASSWORD_ITERATIONS, PASSWORD_KEY_LENGTH);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            SecretKey key = factory.generateSecret(keySpec);
            return key.getEncoded();
        } catch (java.security.NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalStateException("Unable to securely hash account password.", exception);
        } finally {
            keySpec.clearPassword();
            java.util.Arrays.fill(passwordCharacters, '\0');
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        return value.trim();
    }

    private static String requirePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be blank.");
        }
        return password;
    }
}
