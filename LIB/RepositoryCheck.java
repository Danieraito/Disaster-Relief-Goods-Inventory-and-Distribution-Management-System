package LIB;

import java.util.List;
import java.util.Optional;

/** Quick check that accounts and inventory save to and load from the database. Not part of the final system. */
public class RepositoryCheck {
    public static void main(String[] args) throws Exception {
        Database.initialize(true);

        BarangayRepository barangays = new BarangayRepository();
        UserRepository users = new UserRepository();
        InventoryRepository inventory = new InventoryRepository();

        Barangay barangay = barangays.findById("BRG-001").orElseThrow();
        System.out.println("Workspace: " + barangay.getName());

        // Accounts: save, then log in using only what the database stored.
        users.save(new AdminAccount("USR-ADMIN", "System Admin", "admin", "admin@barangaycare.local", "Admin#2026"));
        users.save(new BarangayOfficialAccount("USR-OFF1", "Maria Santos", "msantos",
                "msantos@barangaycare.local", "Official#2026", barangay));

        Optional<UserAccount> good = users.authenticate("msantos", "Official#2026");
        Optional<UserAccount> bad = users.authenticate("msantos", "wrong-password");
        System.out.println("Login with correct password: " + good.isPresent()
                + (good.isPresent() ? " (" + good.get().getRole() + ", " + good.get().getBarangay().getName() + ")" : ""));
        System.out.println("Login with wrong password:   " + bad.isPresent());
        System.out.println("Admin login:                 " + users.authenticate("admin", "Admin#2026").isPresent());
        System.out.println("Accounts in database: " + users.findAll().size());

        // Inventory: add an item with one batch, then reload everything.
        Inventory canned = new Inventory("ITM-003", "Canned Sardines", "Food", 0, "can", 50, "Warehouse B", null, barangay);
        canned.addBatch("B-003", 120, "2026-10-10", "2028-01-01");
        inventory.save(canned);

        System.out.println("--- Inventory loaded from the database ---");
        List<Inventory> items = inventory.findByBarangay("BRG-001");
        for (Inventory item : items) {
            System.out.println(item.getItemId() + " | " + item.getItemName() + " | total: " + item.getQuantity());
            for (InventoryBatch batch : item.getBatches()) {
                System.out.println("    batch " + batch.getBatchId() + " | received: " + batch.getReceivedQuantity()
                        + " | remaining: " + batch.getRemainingQuantity());
            }
        }
        System.out.println("Repository check OK");
    }
}