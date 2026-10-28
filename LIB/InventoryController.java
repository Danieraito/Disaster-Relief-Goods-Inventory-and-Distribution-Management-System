package LIB;

public class InventoryController {
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

    /**
     * Removes stock from one batch, for example a wrong entry, damaged or expired goods.
     * Only Admins and Barangay Officials may adjust stock, and a reason is required.
     */
    public void removeStock(Inventory inventory, String batchId, int amount, String reason, UserAccount user) {
        if (inventory == null) {
            throw new IllegalArgumentException("Inventory cannot be null.");
        }
        if (user == null || !user.isActive() || !user.canManageRecords() || !user.canAccess(inventory)) {
            throw new SecurityException("Only Admins and Barangay Officials can adjust stock in this workspace.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Give a reason for removing stock.");
        }
        if (batchId == null || batchId.trim().isEmpty()) {
            throw new IllegalArgumentException("Choose the batch to remove stock from.");
        }
        inventory.removeBatchStock(batchId.trim(), amount);
    }

    /**
     * Changes an item's details (name, category, low-stock threshold, storage location).
     * The unit is not changed here because existing quantities were counted in it.
     */
    public void updateItemDetails(Inventory inventory, String itemName, String category, int lowStockThreshold,
            String storageLocation, UserAccount user) {
        if (inventory == null) {
            throw new IllegalArgumentException("Inventory cannot be null.");
        }
        if (user == null || !user.isActive() || !user.canManageRecords() || !user.canAccess(inventory)) {
            throw new SecurityException("Only Admins and Barangay Officials can edit inventory items.");
        }
        if (itemName == null || itemName.trim().isEmpty()) {
            throw new IllegalArgumentException("Item name is required.");
        }
        inventory.setLowStockThreshold(lowStockThreshold);
        inventory.setItemName(itemName.trim());
        inventory.setCategory(category == null ? "" : category.trim());
        inventory.setStorageLocation(storageLocation == null ? "" : storageLocation.trim());
    }

    /**
     * Checks that an item may be deleted: only by Admins or Officials, and only if it was never
     * used in a donation or distribution (those records must keep pointing to it).
     */
    public void requireDeletable(Inventory inventory, boolean usedInTransactions, UserAccount user) {
        if (inventory == null) {
            throw new IllegalArgumentException("Inventory cannot be null.");
        }
        if (user == null || !user.isActive() || !user.canManageRecords() || !user.canAccess(inventory)) {
            throw new SecurityException("Only Admins and Barangay Officials can delete inventory items.");
        }
        if (usedInTransactions) {
            throw new IllegalArgumentException(inventory.getItemName()
                    + " is used in donations or distributions, so it cannot be deleted. Use Remove stock instead.");
        }
    }

    public int getAvailableQuantity(Inventory inventory) {
        if (inventory == null) {
            throw new IllegalArgumentException("Inventory cannot be null.");
        }
        return inventory.getQuantity();
    }
}
