package LIB;

import java.time.LocalDate;
import java.util.Objects;

public class InventoryBatch {
    private final String batchId;
    private final Inventory inventory;
    private final String receivedDate;
    private final String expiryDate;
    private int receivedQuantity;
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

    public LocalDate getExpiryLocalDate() {
        return Time.parse(expiryDate);
    }

    public int getReceivedQuantity() {
        return receivedQuantity;
    }

    public int getRemainingQuantity() {
        return remainingQuantity;
    }

    /** A batch is expired on the day after its expiry date; expired goods can never be released. */
    public boolean isExpired(LocalDate asOf) {
        LocalDate expiry = getExpiryLocalDate();
        return expiry != null && expiry.isBefore(asOf);
    }

    void removeStock(int amount) {
        if (amount <= 0 || amount > remainingQuantity) {
            throw new IllegalArgumentException("Batch deduction must be positive and within available batch stock.");
        }
        remainingQuantity -= amount;
    }

    /** Used by a "Correction" adjustment when a count was recorded too low. */
    void restoreStock(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Stock increase must be positive.");
        }
        remainingQuantity = Math.addExact(remainingQuantity, amount);
        if (remainingQuantity > receivedQuantity) {
            receivedQuantity = remainingQuantity;
        }
    }
}
