public class Inventory {
    private String itemId;
    private String itemName;
    private int quantity;
    private String unit;

    public Inventory(String itemId, String itemName, int quantity, String unit) {
        this.itemId = itemId;
        this.itemName = itemName;
        setQuantity(quantity);
        this.unit = unit;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Inventory quantity cannot be negative.");
        }
        this.quantity = quantity;
    }

    public void addStock(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Stock addition must be positive.");
        }
        quantity = Math.addExact(quantity, amount);
    }

    public void removeStock(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Stock removal must be positive.");
        }
        if (amount > quantity) {
            throw new IllegalArgumentException("Cannot remove more stock than is available.");
        }
        quantity -= amount;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}
