package LIB;

import java.util.Objects;

public class InventoryBatch {
    private final String batchId;
    private final Inventory inventory;
    private final String receivedDate;
    private final String expiryDate;
    private final int receivedQuantity;
    private int remainingQuantity;

    public InventoryBatch(String batchId, Inventory inventory, String receivedDate,
            String expiryDate, int quantity) {
        if (batchId == null || batchId.trim().isEmpty()) {
            throw new IllegalArgumentException("batchId cannot be blank.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Batch quantity must be positive.");
        }
        this.batchId = batchId.trim();
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.receivedDate = receivedDate;
        this.expiryDate = expiryDate;
        this.receivedQuantity = quantity;
        this.remainingQuantity = quantity;
    }

    public String getBatchId() {
        return batchId;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public String getReceivedDate() {
        return receivedDate;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public int getReceivedQuantity() {
        return receivedQuantity;
    }

    public int getRemainingQuantity() {
        return remainingQuantity;
    }

    // Used only when loading a saved batch from the database.
    void restoreRemaining(int remaining) {
        if (remaining < 0 || remaining > receivedQuantity) {
            throw new IllegalArgumentException("Remaining quantity must be between 0 and the received quantity.");
        }
        this.remainingQuantity = remaining;
    }

    void removeStock(int amount) {
        if (amount <= 0 || amount > remainingQuantity) {
            throw new IllegalArgumentException("Batch deduction must be positive and within available batch stock.");
        }
        remainingQuantity -= amount;
    }
}