public class InventoryController {
    public Inventory createInventoryItem(String itemId, String itemName, int quantity, String unit) {
        return new Inventory(itemId, itemName, quantity, unit);
    }

    public int getAvailableQuantity(Inventory inventory) {
        if (inventory == null) {
            throw new IllegalArgumentException("Inventory cannot be null.");
        }
        return inventory.getQuantity();
    }
}
