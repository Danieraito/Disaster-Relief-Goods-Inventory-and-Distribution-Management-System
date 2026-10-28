package SERVER;

import LIB.AuditRepository;
import LIB.Barangay;
import LIB.BarangayRepository;
import LIB.Database;
import LIB.UserAccount;
import LIB.UserAccountController;
import LIB.UserRepository;
import com.sun.net.httpserver.HttpExchange;
import java.security.SecureRandom;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * User Administration screen (Admins only): add accounts, change roles, deactivate or reactivate,
 * and reset passwords. New and reset passwords are random and shown to the Admin once; only the
 * hash is stored.
 */
final class UserApi {
    private static final String PASSWORD_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users = new UserRepository();
    private final BarangayRepository barangays = new BarangayRepository();
    private final UserAccountController controller = new UserAccountController();
    private final AuditRepository audits = new AuditRepository();

    private static void requireAdmin(UserAccount user) {
        if (!user.canManageUsers()) {
            throw new ApiError(403, "Only Admin accounts can manage users.");
        }
    }

    void list(HttpExchange exchange, UserAccount user) throws Exception {
        requireAdmin(user);
        List<Map<String, Object>> rows = Database.query(
                "SELECT u.account_id AS id, u.name, u.email, u.username, u.role, u.status, "
                        + "u.last_active AS lastActive, u.barangay_id AS barangayId, b.name AS barangay "
                        + "FROM users u LEFT JOIN barangays b ON b.barangay_id = u.barangay_id ORDER BY u.name");
        for (Map<String, Object> row : rows) {
            row.put("roleLabel", Api.roleLabel((String) row.get("role")));
        }
        List<Object> choices = new java.util.ArrayList<>();
        for (Barangay barangay : barangays.findAll()) {
            choices.add(Json.obj("id", barangay.getBarangayId(), "name", barangay.getName()));
        }
        Http.sendJson(exchange, 200, Json.obj("users", rows, "barangays", choices));
    }

    void invite(HttpExchange exchange, UserAccount admin) throws Exception {
        requireAdmin(admin);
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String name = Api.required(form, "name", "Full name");
        String email = Api.required(form, "email", "Email address");
        UserAccount.Role role = role(Api.text(form, "role"));
        Barangay barangay = role == UserAccount.Role.ADMIN ? null : barangay(Api.text(form, "barangayId"));
        if (users.findByEmail(email).isPresent()) {
            throw new ApiError(400, "An account with this email already exists.");
        }
        String password = temporaryPassword();

        String id = Api.inTransaction((Connection connection) -> {
            String accountId = Database.nextId(connection, "users", "account_id", "USR");
            String username = uniqueUsername(email);
            UserAccount account = controller.createAccount(accountId, name, username, email, password, role,
                    barangay, admin);
            UserRepository.save(connection, account);
            audits.record(connection, admin, "Users", barangay == null ? null : barangay.getBarangayId(),
                    "Account created", accountId, null, name + " · " + Api.roleLabel(role.name()), email, null, null);
            return accountId;
        });
        Http.sendJson(exchange, 201, Json.obj("ok", true, "id", id, "temporaryPassword", password));
    }

    void changeStatus(HttpExchange exchange, UserAccount admin) throws Exception {
        requireAdmin(admin);
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String accountId = Api.required(form, "id", "Account");
        boolean activate = "ACTIVE".equals(Api.text(form, "status"));
        String reason = Api.text(form, "reason");
        if (reason.isEmpty()) {
            throw new ApiError(400, "Give a reason for the access change.");
        }

        Api.inTransaction((Connection connection) -> {
            UserAccount account = UserRepository.findById(connection, accountId);
            if (account == null) {
                throw new ApiError(404, "Account not found.");
            }
            String before = account.getStatus().name();
            controller.setAccountStatus(account, activate ? UserAccount.Status.ACTIVE : UserAccount.Status.INACTIVE,
                    admin, users.findAll());
            UserRepository.save(connection, account);
            audits.record(connection, admin, "Users", barangayId(account),
                    activate ? "Account reactivated" : "Account deactivated", accountId,
                    account.getName() + " · " + before, account.getStatus().name(), reason, null, null);
            return null;
        });
        Http.sendJson(exchange, 200, Json.obj("ok", true));
    }

    void changeRole(HttpExchange exchange, UserAccount admin) throws Exception {
        requireAdmin(admin);
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String accountId = Api.required(form, "id", "Account");
        UserAccount.Role role = role(Api.text(form, "role"));
        Barangay barangay = role == UserAccount.Role.ADMIN ? null : barangay(Api.text(form, "barangayId"));

        Api.inTransaction((Connection connection) -> {
            UserAccount account = UserRepository.findById(connection, accountId);
            if (account == null) {
                throw new ApiError(404, "Account not found.");
            }
            String before = Api.roleLabel(account.getRole().name())
                    + (account.getBarangay() == null ? "" : " · " + account.getBarangay().getName());
            UserAccount updated = controller.changeRole(account, role, barangay, admin, users.findAll());
            UserRepository.save(connection, updated);
            audits.record(connection, admin, "Users", barangayId(updated), "Role changed", accountId,
                    account.getName() + " · " + before,
                    Api.roleLabel(role.name()) + (barangay == null ? "" : " · " + barangay.getName()), null, null, null);
            return null;
        });
        Http.sendJson(exchange, 200, Json.obj("ok", true));
    }

    void resetPassword(HttpExchange exchange, UserAccount admin) throws Exception {
        requireAdmin(admin);
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String accountId = Api.required(form, "id", "Account");
        String password = temporaryPassword();

        Api.inTransaction((Connection connection) -> {
            UserAccount account = UserRepository.findById(connection, accountId);
            if (account == null) {
                throw new ApiError(404, "Account not found.");
            }
            UserRepository.save(connection, controller.resetPassword(account, password, admin));
            audits.record(connection, admin, "Users", barangayId(account), "Password reset", accountId,
                    null, account.getName(), "Temporary password issued", null, null);
            return null;
        });
        Http.sendJson(exchange, 200, Json.obj("ok", true, "temporaryPassword", password));
    }

    // ------------------------------------------------------------------ helpers
    private static String barangayId(UserAccount account) {
        return account.getBarangay() == null ? null : account.getBarangay().getBarangayId();
    }

    private static UserAccount.Role role(String value) {
        try {
            return UserAccount.Role.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ApiError(400, "Choose a role.");
        }
    }

    private Barangay barangay(String barangayId) throws Exception {
        Barangay barangay = barangays.findById(barangayId).orElse(null);
        if (barangay == null) {
            throw new ApiError(400, "Choose a barangay workspace for this account.");
        }
        return barangay;
    }

    private String uniqueUsername(String email) throws Exception {
        String base = email.substring(0, email.indexOf('@')).replaceAll("[^A-Za-z0-9._-]", "");
        if (base.isEmpty()) {
            base = "user";
        }
        String candidate = base;
        int counter = 2;
        while (users.findByUsername(candidate).isPresent()) {
            candidate = base + counter++;
        }
        return candidate;
    }

    private static String temporaryPassword() {
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            password.append(PASSWORD_CHARACTERS.charAt(RANDOM.nextInt(PASSWORD_CHARACTERS.length())));
        }
        return password.append('#').append(2 + RANDOM.nextInt(8)).toString();
    }
}
