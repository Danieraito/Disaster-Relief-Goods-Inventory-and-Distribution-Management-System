package SERVER;

import LIB.AuditRepository;
import LIB.Barangay;
import LIB.BarangayRepository;
import LIB.Inventory;
import LIB.InventoryController;
import LIB.InventoryRepository;
import LIB.UserAccount;
import LIB.UserRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The JSON "API" the web pages talk to. It signs people in, then hands each request to the
 * class for that screen (DisasterApi, HouseholdApi, DonationApi, DistributionApi, UserApi, AuditApi).
 */
final class ApiHandler implements HttpHandler {

    private final Sessions sessions;
    private final UserRepository users = new UserRepository();
    private final BarangayRepository barangays = new BarangayRepository();
    private final InventoryRepository inventory = new InventoryRepository();
    private final InventoryController inventoryController = new InventoryController();
    private final AuditRepository audits = new AuditRepository();

    private final DisasterApi disasterApi = new DisasterApi();
    private final HouseholdApi householdApi = new HouseholdApi();
    private final DonationApi donationApi = new DonationApi();
    private final DistributionApi distributionApi = new DistributionApi();
    private final UserApi userApi = new UserApi();
    private final AuditApi auditApi = new AuditApi();

    ApiHandler(Sessions sessions) {
        this.sessions = sessions;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String route = method + " " + path;

            // Pages anyone can call before signing in.
            if (route.equals("GET /api/workspaces")) {
                workspaces(exchange);
                return;
            }
            if (route.equals("POST /api/login")) {
                login(exchange);
                return;
            }
            if (route.equals("POST /api/logout")) {
                logout(exchange);
                return;
            }

            UserAccount user = requireUser(exchange);
            switch (route) {
                case "GET /api/me":
                    Http.sendJson(exchange, 200, userJson(user));
                    break;
                case "GET /api/inventory":
                    listInventory(exchange, user);
                    break;
                case "POST /api/inventory":
                    createItem(exchange, user);
                    break;
                case "POST /api/inventory/remove":
                    removeStock(exchange, user);
                    break;
                case "POST /api/inventory/delete":
                    deleteItem(exchange, user);
                    break;
                case "POST /api/inventory/update":
                    updateItem(exchange, user);
                    break;
                case "GET /api/backup":
                    backup(exchange, user);
                    break;

                case "GET /api/disasters":
                    disasterApi.list(exchange, user);
                    break;
                case "POST /api/disasters":
                    disasterApi.create(exchange, user);
                    break;
                case "POST /api/disasters/status":
                    disasterApi.changeStatus(exchange, user);
                    break;
                case "POST /api/centers":
                    disasterApi.createCenter(exchange, user);
                    break;

                case "GET /api/households":
                    householdApi.list(exchange, user);
                    break;
                case "POST /api/households":
                    householdApi.register(exchange, user);
                    break;
                case "POST /api/households/update":
                    householdApi.update(exchange, user);
                    break;

                case "GET /api/donations":
                    donationApi.list(exchange, user);
                    break;
                case "POST /api/donations":
                    donationApi.record(exchange, user);
                    break;
                case "POST /api/donations/decide":
                    donationApi.decide(exchange, user);
                    break;
                case "POST /api/donors":
                    donationApi.createDonor(exchange, user);
                    break;

                case "GET /api/distributions":
                    distributionApi.list(exchange, user);
                    break;
                case "POST /api/distributions":
                    distributionApi.record(exchange, user);
                    break;
                case "POST /api/distributions/decide":
                    distributionApi.decide(exchange, user);
                    break;
                case "POST /api/distributions/bulk":
                    distributionApi.bulk(exchange, user);
                    break;
                case "POST /api/packs":
                    distributionApi.createPack(exchange, user);
                    break;
                case "POST /api/packs/delete":
                    distributionApi.deletePack(exchange, user);
                    break;

                case "GET /api/audit":
                    auditApi.list(exchange, user);
                    break;
                case "POST /api/reports/exported":
                    auditApi.reportExported(exchange, user);
                    break;

                case "GET /api/users":
                    userApi.list(exchange, user);
                    break;
                case "POST /api/users":
                    userApi.invite(exchange, user);
                    break;
                case "POST /api/users/status":
                    userApi.changeStatus(exchange, user);
                    break;
                case "POST /api/users/role":
                    userApi.changeRole(exchange, user);
                    break;
                case "POST /api/users/reset":
                    userApi.resetPassword(exchange, user);
                    break;
                default:
                    throw new ApiError(404, "Not found");
            }
        } catch (ApiError e) {
            Http.sendJson(exchange, e.status, Json.obj("error", e.getMessage()));
        } catch (SecurityException e) {
            // Thrown by the model classes when a role is not allowed to do something.
            Http.sendJson(exchange, 403, Json.obj("error", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Thrown by the model classes when a business rule is broken (for example not enough stock).
            Http.sendJson(exchange, 400, Json.obj("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            Http.sendJson(exchange, 500, Json.obj("error", "Server error: " + e.getMessage()));
        } finally {
            exchange.close();
        }
    }

    // ------------------------------------------------------------ sign in/out
    private void workspaces(HttpExchange exchange) throws IOException, SQLException {
        List<Object> list = new ArrayList<>();
        for (Barangay barangay : barangays.findAll()) {
            list.add(Json.obj("id", barangay.getBarangayId(), "name", barangay.getName(),
                    "municipality", barangay.getMunicipality()));
        }
        Http.sendJson(exchange, 200, Json.obj("workspaces", list));
    }

    private void login(HttpExchange exchange) throws IOException, SQLException {
        Map<String, String> form = Http.parseForm(exchange);
        String email = form.getOrDefault("email", "").trim();
        String password = form.getOrDefault("password", "");
        String workspaceId = form.getOrDefault("workspace", "");

        UserAccount.Role role;
        try {
            role = UserAccount.Role.valueOf(form.getOrDefault("role", ""));
        } catch (IllegalArgumentException e) {
            throw new ApiError(401, "Role or password not recognized");
        }

        Optional<UserAccount> found = users.authenticateByEmail(email, password);
        boolean ok = found.isPresent() && found.get().getRole() == role;
        // Non-admin accounts can only sign in to their own barangay workspace.
        if (ok && role != UserAccount.Role.ADMIN) {
            Barangay own = found.get().getBarangay();
            ok = own != null && own.getBarangayId().equals(workspaceId);
        }
        if (!ok) {
            throw new ApiError(401, "Role or password not recognized");
        }
        String token = sessions.create(found.get());
        users.touchLastActive(found.get().getAccountId());
        exchange.getResponseHeaders().add("Set-Cookie", "sid=" + token + "; Path=/; HttpOnly; SameSite=Strict");
        Http.sendJson(exchange, 200, userJson(found.get()));
    }

    private void logout(HttpExchange exchange) throws IOException {
        sessions.remove(Http.cookie(exchange, "sid"));
        exchange.getResponseHeaders().add("Set-Cookie", "sid=; Path=/; Max-Age=0");
        Http.sendJson(exchange, 200, Json.obj("ok", true));
    }

    /**
     * Finds who is signed in. The account is re-read from the database on every request, so a
     * role change or deactivation by an Admin takes effect straight away.
     */
    private UserAccount requireUser(HttpExchange exchange) throws SQLException {
        String token = Http.cookie(exchange, "sid");
        UserAccount remembered = sessions.find(token);
        if (remembered == null) {
            throw new ApiError(401, "Please sign in.");
        }
        UserAccount current = users.findById(remembered.getAccountId()).orElse(null);
        if (current == null || !current.isActive()) {
            sessions.remove(token);
            throw new ApiError(401, "Please sign in.");
        }
        return current;
    }

    private Map<String, Object> userJson(UserAccount user) throws SQLException {
        List<Object> choices = new ArrayList<>();
        if (user.getRole() == UserAccount.Role.ADMIN) {
            for (Barangay barangay : barangays.findAll()) {
                choices.add(Json.obj("id", barangay.getBarangayId(), "name", barangay.getName()));
            }
        }
        return Json.obj(
                "id", user.getAccountId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole().name(),
                "roleLabel", Api.roleLabel(user.getRole().name()),
                "workspace", user.getBarangay() == null ? "All barangays" : user.getBarangay().getName(),
                "barangayId", user.getBarangay() == null ? null : user.getBarangay().getBarangayId(),
                "barangays", choices,
                "canEnterRecords", user.canEnterRecords(),
                "canManageRecords", user.canManageRecords(),
                "canApprove", user.canApproveDistributions(),
                "canExportReports", user.canExportReports(),
                "canManageUsers", user.canManageUsers());
    }

    // --------------------------------------------------------------- inventory
    private List<Inventory> inventoryFor(UserAccount user) throws SQLException {
        List<Inventory> items = new ArrayList<>();
        if (user.getRole() == UserAccount.Role.ADMIN) {
            for (Barangay barangay : barangays.findAll()) {
                items.addAll(inventory.findByBarangay(barangay.getBarangayId()));
            }
        } else {
            items.addAll(inventory.findByBarangay(user.getBarangay().getBarangayId()));
        }
        return items;
    }

    private void listInventory(HttpExchange exchange, UserAccount user) throws IOException, SQLException {
        LocalDate today = LIB.Database.today();
        java.util.Set<String> used = InventoryRepository.usedItemIds();
        List<Object> list = new ArrayList<>();
        for (Inventory item : inventoryFor(user)) {
            Map<String, Object> map = new StockInfo(item, today).toMap();
            map.put("barangayId", item.getBarangay() == null ? null : item.getBarangay().getBarangayId());
            map.put("used", used.contains(item.getItemId()));
            list.add(map);
        }
        List<Object> choices = new ArrayList<>();
        if (user.getRole() == UserAccount.Role.ADMIN) {
            for (Barangay barangay : barangays.findAll()) {
                choices.add(Json.obj("id", barangay.getBarangayId(), "name", barangay.getName()));
            }
        }
        Http.sendJson(exchange, 200, Json.obj("items", list, "barangays", choices, "today", today.toString()));
    }

    private void createItem(HttpExchange exchange, UserAccount user) throws Exception {
        Map<String, String> form = Http.parseForm(exchange);

        String name = form.getOrDefault("name", "").trim();
        String unit = form.getOrDefault("unit", "").trim();
        if (name.isEmpty() || unit.isEmpty()) {
            throw new ApiError(400, "Item name and unit are required.");
        }
        if (unit.matches("\\d+")) {
            throw new ApiError(400, "The unit should be a word such as sack, can or liter, not a number.");
        }

        int quantity;
        int threshold;
        try {
            quantity = Integer.parseInt(form.getOrDefault("quantity", "0").trim());
            threshold = Integer.parseInt(form.getOrDefault("threshold", "0").trim());
        } catch (NumberFormatException e) {
            throw new ApiError(400, "Quantity and threshold must be whole numbers.");
        }

        String expiry = form.getOrDefault("expiry", "").trim();
        if (!expiry.isEmpty()) {
            try {
                LocalDate.parse(expiry);
            } catch (DateTimeParseException e) {
                throw new ApiError(400, "The expiry date must look like 2027-03-31.");
            }
        }

        // Admins choose the barangay; everyone else adds to their own workspace.
        Barangay target = Api.targetBarangay(user, form.getOrDefault("barangayId", ""));

        String itemId = "ITM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Inventory item = inventoryController.createInventoryItem(itemId, name,
                form.getOrDefault("category", "").trim(), quantity, unit, threshold,
                form.getOrDefault("location", "").trim(), expiry.isEmpty() ? null : expiry, target, user);
        Api.inTransaction((Connection connection) -> {
            InventoryRepository.save(connection, item);
            audits.record(connection, user, "Inventory", target.getBarangayId(), "Item added", itemId,
                    null, name + " " + quantity + " " + unit, "Opening stock", null, null);
            return null;
        });
        Http.sendJson(exchange, 201, Json.obj("ok", true, "id", itemId));
    }

    /**
     * Removes stock from one batch (wrong entry, damaged, expired, lost, recount). The change and
     * the reason are saved together with an ADJ- reference in the audit log.
     */
    private void removeStock(HttpExchange exchange, UserAccount user) throws Exception {
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String itemId = Api.required(form, "itemId", "Item");
        String batchId = Api.required(form, "batchId", "Batch");
        int quantity = Api.wholeNumber(Api.required(form, "quantity", "Quantity"), "Quantity");
        String reason = Api.required(form, "reason", "Reason");
        String note = Api.text(form, "note");

        String reference = Api.inTransaction((Connection connection) -> {
            Inventory item = InventoryRepository.findById(connection, itemId);
            if (item == null) {
                throw new ApiError(404, "Item not found.");
            }
            Api.requireAccess(user, item.getBarangay().getBarangayId());
            int batchBefore = 0;
            for (LIB.InventoryBatch batch : item.getBatches()) {
                if (batch.getBatchId().equals(batchId)) {
                    batchBefore = batch.getRemainingQuantity();
                }
            }
            int before = item.getQuantity();
            inventoryController.removeStock(item, batchId, quantity, reason, user);
            InventoryRepository.saveBatches(connection, item);
            String adjustmentId = LIB.Database.nextId(connection, "audit_logs", "reference", "ADJ");
            audits.record(connection, user, "Inventory", item.getBarangay().getBarangayId(),
                    "Stock removed · " + reason, adjustmentId,
                    item.getItemName() + " " + before + " " + item.getUnit(),
                    item.getItemName() + " " + item.getQuantity() + " " + item.getUnit(),
                    quantity + " " + item.getUnit() + " from batch " + batchId + " (" + batchBefore + " → "
                            + (batchBefore - quantity) + ")" + (note.isEmpty() ? "" : " · " + note), null, null);
            return adjustmentId;
        });
        Http.sendJson(exchange, 200, Json.obj("ok", true, "reference", reference));
    }

    /** Deletes an item that was added by mistake and never used in a donation or distribution. */
    private void deleteItem(HttpExchange exchange, UserAccount user) throws Exception {
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String itemId = Api.required(form, "itemId", "Item");
        String reason = Api.required(form, "reason", "Reason");

        Api.inTransaction((Connection connection) -> {
            Inventory item = InventoryRepository.findById(connection, itemId);
            if (item == null) {
                throw new ApiError(404, "Item not found.");
            }
            Api.requireAccess(user, item.getBarangay().getBarangayId());
            inventoryController.requireDeletable(item, InventoryRepository.isUsedInTransactions(connection, itemId), user);
            InventoryRepository.delete(connection, itemId);
            audits.record(connection, user, "Inventory", item.getBarangay().getBarangayId(), "Item deleted", itemId,
                    item.getItemName() + " " + item.getQuantity() + " " + item.getUnit(), "Deleted", reason, null, null);
            return null;
        });
        Http.sendJson(exchange, 200, Json.obj("ok", true));
    }

    /** Edits an item's name, category, threshold and storage location. Every change is audited. */
    private void updateItem(HttpExchange exchange, UserAccount user) throws Exception {
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String itemId = Api.required(form, "itemId", "Item");
        String name = Api.required(form, "name", "Item name");
        int threshold = Api.wholeNumber(Api.required(form, "threshold", "Low-stock threshold"), "Low-stock threshold");
        String category = Api.text(form, "category");
        String location = Api.text(form, "location");

        Api.inTransaction((Connection connection) -> {
            Inventory item = InventoryRepository.findById(connection, itemId);
            if (item == null) {
                throw new ApiError(404, "Item not found.");
            }
            Api.requireAccess(user, item.getBarangay().getBarangayId());
            List<String> before = new ArrayList<>();
            List<String> after = new ArrayList<>();
            compare("Name", item.getItemName(), name, before, after);
            compare("Category", item.getCategory(), category, before, after);
            compare("Threshold", String.valueOf(item.getLowStockThreshold()), String.valueOf(threshold), before, after);
            compare("Storage", item.getStorageLocation(), location, before, after);
            if (before.isEmpty()) {
                throw new ApiError(400, "Nothing was changed.");
            }
            inventoryController.updateItemDetails(item, name, category, threshold, location, user);
            InventoryRepository.save(connection, item);
            audits.record(connection, user, "Inventory", item.getBarangay().getBarangayId(), "Item details updated",
                    itemId, String.join("; ", before), String.join("; ", after),
                    Api.text(form, "reason").isEmpty() ? item.getItemName() : Api.text(form, "reason"), null, null);
            return null;
        });
        Http.sendJson(exchange, 200, Json.obj("ok", true));
    }

    private static void compare(String label, String oldValue, String newValue, List<String> before, List<String> after) {
        String a = oldValue == null ? "" : oldValue.trim();
        String b = newValue == null ? "" : newValue.trim();
        if (!a.equals(b)) {
            before.add(label + " " + (a.isEmpty() ? "(blank)" : a));
            after.add(label + " " + (b.isEmpty() ? "(blank)" : b));
        }
    }

    /**
     * Admins can download a copy of the whole database. SQLite's VACUUM INTO writes a consistent
     * copy even while the system is in use.
     */
    private void backup(HttpExchange exchange, UserAccount user) throws Exception {
        if (!user.canManageUsers()) {
            throw new ApiError(403, "Only Admin accounts can download a backup.");
        }
        java.nio.file.Path copy = java.nio.file.Files.createTempFile("barangaycare-backup", ".db");
        java.nio.file.Files.delete(copy);
        try {
            synchronized (LIB.Database.WRITE_LOCK) {
                try (Connection connection = LIB.Database.getConnection();
                        java.sql.Statement statement = connection.createStatement()) {
                    statement.execute("VACUUM INTO '" + copy.toString().replace("'", "''") + "'");
                }
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(copy);
            String name = "barangaycare-backup_" + LIB.Database.now().replace(' ', '_').replace(':', '-') + ".db";
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + name + "\"");
            Http.send(exchange, 200, "application/octet-stream", bytes);
            audits.recordAlone(user, "Users", null, "Database backup downloaded", name, null,
                    bytes.length / 1024 + " KB", null);
        } finally {
            java.nio.file.Files.deleteIfExists(copy);
        }
    }
}
