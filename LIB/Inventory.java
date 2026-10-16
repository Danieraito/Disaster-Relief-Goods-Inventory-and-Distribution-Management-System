package LIB;

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
        if (lowStockThreshold < 0) {
            throw new IllegalArgumentException("Low-stock threshold cannot be negative.");
        }
        this.lowStockThreshold = lowStockThreshold;
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

    @Override
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

    // Used only when loading a saved batch from the database.
    void restoreBatch(String batchId, String receivedDate, String expiryDate,
            int receivedQuantity, int remainingQuantity) {
        InventoryBatch batch = new InventoryBatch(batchId, this, receivedDate, expiryDate, receivedQuantity);
        batch.restoreRemaining(remainingQuantity);
        batches.add(batch);
        quantity = Math.addExact(quantity, remainingQuantity);
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

    private InventoryBatch findBatch(String batchId) {
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
}