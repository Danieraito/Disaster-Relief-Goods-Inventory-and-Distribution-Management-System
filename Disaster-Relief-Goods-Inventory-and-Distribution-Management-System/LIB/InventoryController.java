package LIB;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class InventoryController {
    private final InventoryRepository repository = new InventoryRepository();

    public Inventory createInventoryItem(String itemId, String itemName, int quantity, String unit) {
        return new Inventory(itemId, itemName, quantity, unit);
    }

    public Inventory createInventoryItem(String itemId, String itemName, String category, int quantity,
            String unit, int lowStockThreshold, String storageLocation, String expiryDate) {
        return new Inventory(itemId, itemName, category, quantity, unit,
                lowStockThreshold, storageLocation, expiryDate);
    }

    public Inventory createInventoryItem(String itemId, String itemName, String category, int quantity,
            String unit, int lowStockThreshold, String storageLocation, String expiryDate,
            Barangay barangay, UserAccount creator) {
        if (creator == null || !creator.isActive() || !creator.canEnterRecords()
                || !creator.canAccessBarangay(barangay)) {
            throw new SecurityException("User cannot create inventory in this barangay workspace.");
        }
        return new Inventory(itemId, itemName, category, quantity, unit,
                lowStockThreshold, storageLocation, expiryDate, barangay);
    }

    public int getAvailableQuantity(Inventory inventory) {
        if (inventory == null) {
            throw new IllegalArgumentException("Inventory cannot be null.");
        }
        return inventory.getQuantity();
    }

    // ----- workflows used by the Inventory screens (permission checks first, then the repository) -----

    public List<Inventory> list(Barangay workspace, boolean includeArchived, UserAccount user) throws SQLException {
        requireAccess(user, workspace);
        return repository.findByBarangay(workspace.getBarangayId(), includeArchived);
    }

    public Inventory find(String itemId, UserAccount user) throws SQLException {
        Inventory item = repository.findById(itemId);
        if (item != null) {
            requireAccess(user, item.getBarangay());
        }
        return item;
    }

    public Inventory addItem(String name, String category, int openingQuantity, String unit, int threshold,
            String storageLocation, String expiryDate, String note, Barangay barangay, UserAccount user)
            throws SQLException {
        if (name == null || name.trim().isEmpty() || unit == null || unit.trim().isEmpty()
                || storageLocation == null || storageLocation.trim().isEmpty()) {
            throw new IllegalArgumentException("Item name, unit and storage location are required.");
        }
        Inventory duplicate = repository.findDuplicate(barangay.getBarangayId(), name, unit);
        if (duplicate != null) {
            throw new IllegalArgumentException(duplicate.getItemName() + " (" + duplicate.getItemId()
                    + ") already exists. Record new goods as a donation instead.");
        }
        return repository.createItem(name, category, openingQuantity, unit, threshold, storageLocation,
                expiryDate, note, barangay, user);
    }

    public InventoryRepository.EditResult updateItem(Inventory item, String name, String category, String unit,
            int threshold, String storage, String reason, UserAccount user) throws SQLException {
        if (user == null || !user.isActive() || !user.canEnterRecords() || !user.canAccess(item)) {
            throw new SecurityException("User cannot edit inventory in this barangay workspace.");
        }
        if (name == null || name.trim().isEmpty() || unit == null || unit.trim().isEmpty()
                || storage == null || storage.trim().isEmpty()) {
            throw new IllegalArgumentException("Item name, unit and storage location are required.");
        }
        if (threshold < 0) {
            throw new IllegalArgumentException("Low-stock threshold cannot be negative.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a reason for this change.");
        }
        return repository.updateDetails(item, name, category, unit, threshold, storage, reason.trim(), user);
    }

    public StockAdjustment adjustStock(Inventory item, String batchId, StockAdjustment.Type type,
            StockAdjustment.Reason reason, int quantity, String evidence, LocalDate date, UserAccount user)
            throws SQLException {
        requireManager(user, item, "adjust stock");
        return repository.adjustStock(item.getItemId(), batchId, type, reason, quantity, evidence, date, user);
    }

    public void archiveItem(Inventory item, String reason, UserAccount user) throws SQLException {
        requireManager(user, item, "archive items");
        requireReason(reason);
        repository.setArchived(item.getItemId(), true, reason.trim(), user);
    }

    public void restoreItem(Inventory item, UserAccount user) throws SQLException {
        requireManager(user, item, "restore items");
        repository.setArchived(item.getItemId(), false, "Restored to active inventory", user);
    }

    /** Deletes only an unused, zero-stock item after the officer typed its name to confirm. */
    public void deleteItem(Inventory item, String reason, String typedName, UserAccount user) throws SQLException {
        requireManager(user, item, "delete items");
        requireReason(reason);
        if (typedName == null || !typedName.trim().equalsIgnoreCase(item.getItemName())) {
            throw new IllegalArgumentException("Type the item name exactly to confirm the deletion.");
        }
        repository.deleteItem(item.getItemId(), reason.trim(), user);
    }

    /** True when the item has no stock and no donation, release or adjustment records. */
    public boolean canDelete(Inventory item) throws SQLException {
        return item.getQuantity() == 0 && !repository.hasMovements(item.getItemId());
    }

    public List<StockMovement> history(Inventory item) throws SQLException {
        return repository.findMovements(item.getItemId());
    }

    private void requireAccess(UserAccount user, Barangay barangay) {
        if (user == null || !user.isActive() || !user.canAccessBarangay(barangay)) {
            throw new SecurityException("User cannot open inventory in this barangay workspace.");
        }
    }

    private void requireManager(UserAccount user, Inventory item, String action) {
        if (user == null || !user.isActive() || !user.canManageRecords() || !user.canAccess(item)) {
            throw new SecurityException("Only a Barangay Official or Admin of this workspace can " + action + ".");
        }
    }

    private void requireReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a reason for this action.");
        }
    }
}
