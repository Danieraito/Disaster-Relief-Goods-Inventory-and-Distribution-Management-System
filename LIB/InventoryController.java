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

    public int getAvailableQuantity(Inventory inventory) {
        if (inventory == null) {
            throw new IllegalArgumentException("Inventory cannot be null.");
        }
        return inventory.getQuantity();
    }
}
