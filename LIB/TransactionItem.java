package LIB;

import java.util.Objects;

public class TransactionItem {
    private final Inventory inventory;
    private final int quantity;
    private final String batchReference;
    private final String expiryDate;

    public TransactionItem(Inventory inventory, int quantity) {
        this(inventory, quantity, null, null);
    }

    public TransactionItem(Inventory inventory, int quantity, String batchReference, String expiryDate) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Transaction item quantity must be positive.");
        }
        this.quantity = quantity;
        this.batchReference = batchReference;
        this.expiryDate = expiryDate;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getBatchReference() {
        return batchReference;
    }

    public String getExpiryDate() {
        return expiryDate;
    }
}
