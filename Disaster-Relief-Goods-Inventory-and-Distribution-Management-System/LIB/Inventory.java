package LIB;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Inventory implements BarangayScoped {
    private String itemId;
    private String itemName;
    private String category;
    private int quantity;
    private String unit;
    private int lowStockThreshold;
    private String storageLocation;
    private final Barangay barangay;
    private final List<InventoryBatch> batches = new ArrayList<>();
    private boolean archived;
    private String archivedReason = "";
    private LocalDateTime createdAt;
    private String createdByName = "";
    private LocalDateTime updatedAt;
    private String updatedByName = "";

    public Inventory(String itemId, String itemName, int quantity, String unit) {
        this(itemId, itemName, "Uncategorized", quantity, unit, 0, "", "", null);
    }

    public Inventory(String itemId, String itemName, String category, int quantity, String unit,
            int lowStockThreshold, String storageLocation, String expiryDate) {
        this(itemId, itemName, category, quantity, unit, lowStockThreshold,
                storageLocation, expiryDate, null);
    }

    public Inventory(String itemId, String itemName, String category, int quantity, String unit,
            int lowStockThreshold, String storageLocation, String expiryDate, Barangay barangay) {
        this.itemId = itemId;
        this.itemName = itemName;
        if (quantity < 0) {
            throw new IllegalArgumentException("Inventory quantity cannot be negative.");
        }
        this.quantity = quantity;
        this.category = category;
        this.unit = unit;
        setLowStockThreshold(lowStockThreshold);
        this.storageLocation = storageLocation;
        this.barangay = barangay;
        if (quantity > 0) {
            String openingBatchId = "OPENING-" + itemId;
            batches.add(new InventoryBatch(openingBatchId, this, "", expiryDate, quantity));
        }
    }

    public String getItemId() {
        return itemId;
    }

    public Barangay getBarangay() {
        return barangay;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getQuantity() {
        return quantity;
    }

    public void addStock(int amount) {
        addBatch("BATCH-" + UUID.randomUUID(), amount, "", "");
    }

    void validateStockAddition(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Stock addition must be positive.");
        }
        Math.addExact(quantity, amount);
    }

    public InventoryBatch addBatch(String batchId, int amount, String receivedDate, String batchExpiryDate) {
        validateBatchAddition(batchId, amount);
        InventoryBatch batch = new InventoryBatch(batchId, this, receivedDate, batchExpiryDate, amount);
        batches.add(batch);
        quantity = Math.addExact(quantity, amount);
        return batch;
    }

    void validateBatchAddition(String batchId, int amount) {
        validateStockAddition(amount);
        if (batchId == null || batchId.trim().isEmpty()) {
            throw new IllegalArgumentException("batchId cannot be blank.");
        }
        for (InventoryBatch batch : batches) {
            if (batch.getBatchId().equals(batchId.trim())) {
                throw new IllegalArgumentException("Batch ID already exists for this inventory item.");
            }
        }
    }

    public List<InventoryBatch> getBatches() {
        return Collections.unmodifiableList(new ArrayList<>(batches));
    }

    public void removeStock(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Stock removal must be positive.");
        }
        InventoryBatch onlyBatch = null;
        for (InventoryBatch batch : batches) {
            if (batch.getRemainingQuantity() > 0) {
                if (onlyBatch != null) {
                    throw new IllegalStateException("Select an inventory batch when stock spans multiple batches.");
                }
                onlyBatch = batch;
            }
        }
        if (onlyBatch == null) {
            throw new IllegalArgumentException("No batch has stock available.");
        }
        removeBatchStock(onlyBatch.getBatchId(), amount);
    }

    void validateBatchRemoval(String batchId, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Stock removal must be positive.");
        }
        InventoryBatch batch = findBatch(batchId);
        if (batch == null || amount > batch.getRemainingQuantity()) {
            throw new IllegalArgumentException("Batch does not have enough available stock.");
        }
    }

    void removeBatchStock(String batchId, int amount) {
        validateBatchRemoval(batchId, amount);
        InventoryBatch batch = findBatch(batchId);
        batch.removeStock(amount);
        quantity -= amount;
    }

    public InventoryBatch findBatch(String batchId) {
        if (batchId == null) {
            return null;
        }
        for (InventoryBatch batch : batches) {
            if (batch.getBatchId().equals(batchId)) {
                return batch;
            }
        }
        return null;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(int lowStockThreshold) {
        if (lowStockThreshold < 0) {
            throw new IllegalArgumentException("Low-stock threshold cannot be negative.");
        }
        this.lowStockThreshold = lowStockThreshold;
    }

    public boolean isLowStock() {
        return quantity <= lowStockThreshold;
    }

    public String getStorageLocation() {
        return storageLocation;
    }

    public void setStorageLocation(String storageLocation) {
        this.storageLocation = storageLocation;
    }

    public String getExpiryDate() {
        String commonExpiryDate = null;
        boolean foundAvailableBatch = false;
        for (InventoryBatch batch : batches) {
            if (batch.getRemainingQuantity() == 0) {
                continue;
            }
            if (!foundAvailableBatch) {
                commonExpiryDate = batch.getExpiryDate();
                foundAvailableBatch = true;
            } else if (!Objects.equals(commonExpiryDate, batch.getExpiryDate())) {
                return null;
            }
        }
        return foundAvailableBatch ? commonExpiryDate : null;
    }
    // ----- availability helpers used by the dashboard, inventory and distribution screens -----

    /** Stock that may be released: batches that have not expired. */
    public int getAvailableQuantity(LocalDate asOf) {
        int total = 0;
        for (InventoryBatch batch : batches) {
            if (!batch.isExpired(asOf)) {
                total += batch.getRemainingQuantity();
            }
        }
        return total;
    }

    /** Stock that is on the shelf but past its expiry date (quarantined, never releasable). */
    public int getExpiredQuantity(LocalDate asOf) {
        int total = 0;
        for (InventoryBatch batch : batches) {
            if (batch.isExpired(asOf)) {
                total += batch.getRemainingQuantity();
            }
        }
        return total;
    }

    /** Earliest expiry among batches that still hold stock (may already be in the past). */
    public LocalDate getEarliestExpiry() {
        LocalDate earliest = null;
        for (InventoryBatch batch : batches) {
            LocalDate expiry = batch.getExpiryLocalDate();
            if (batch.getRemainingQuantity() > 0 && expiry != null && (earliest == null || expiry.isBefore(earliest))) {
                earliest = expiry;
            }
        }
        return earliest;
    }

    /** Earliest expiry among usable batches only. */
    public LocalDate getEarliestUsableExpiry(LocalDate asOf) {
        LocalDate earliest = null;
        for (InventoryBatch batch : batches) {
            LocalDate expiry = batch.getExpiryLocalDate();
            if (batch.getRemainingQuantity() > 0 && !batch.isExpired(asOf) && expiry != null
                    && (earliest == null || expiry.isBefore(earliest))) {
                earliest = expiry;
            }
        }
        return earliest;
    }

    public boolean hasExpiredStock(LocalDate asOf) {
        return getExpiredQuantity(asOf) > 0;
    }

    public boolean isExpiringSoon(LocalDate asOf, int days) {
        LocalDate expiry = getEarliestUsableExpiry(asOf);
        return expiry != null && !expiry.isAfter(asOf.plusDays(days));
    }

    /** Low stock is judged on usable stock, so expired goods never hide a shortage. */
    public boolean isLowStock(LocalDate asOf) {
        return getAvailableQuantity(asOf) <= lowStockThreshold;
    }

    /** Applies a posted adjustment to one batch; the caller persists the result. */
    public void applyAdjustment(StockAdjustment adjustment) {
        InventoryBatch batch = findBatch(adjustment.getBatchId());
        if (batch == null) {
            throw new IllegalArgumentException("That batch does not belong to this item.");
        }
        if (adjustment.getType() == StockAdjustment.Type.DECREASE) {
            if (adjustment.getQuantity() > batch.getRemainingQuantity()) {
                throw new IllegalArgumentException("The batch only has " + batch.getRemainingQuantity()
                        + " " + unit + " left.");
            }
            removeBatchStock(batch.getBatchId(), adjustment.getQuantity());
        } else {
            batch.restoreStock(adjustment.getQuantity());
            quantity = Math.addExact(quantity, adjustment.getQuantity());
        }
    }

    public boolean isArchived() {
        return archived;
    }

    public String getArchivedReason() {
        return archivedReason;
    }

    void setArchived(boolean archived, String reason) {
        this.archived = archived;
        this.archivedReason = reason == null ? "" : reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    void setCreated(LocalDateTime when, String by) {
        this.createdAt = when;
        this.createdByName = by == null ? "" : by;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedByName() {
        return updatedByName;
    }

    void setUpdated(LocalDateTime when, String by) {
        this.updatedAt = when;
        this.updatedByName = by == null ? "" : by;
    }
}
