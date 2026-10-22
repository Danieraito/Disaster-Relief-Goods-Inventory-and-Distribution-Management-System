package LIB;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One posted correction, damage or spoilage entry against a single inventory batch. */
public class StockAdjustment {
    public enum Type { INCREASE, DECREASE }

    public enum Reason {
        DAMAGE("Damage"), SPOILAGE("Spoilage"), CORRECTION("Correction");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private String adjustmentId;
    private final String itemId;
    private final String itemName;
    private final String unit;
    private final String batchId;
    private final Type type;
    private final Reason reason;
    private final int quantity;
    private final String evidence;
    private final LocalDate adjustedOn;
    private int beforeQuantity;
    private int afterQuantity;
    private LocalDateTime createdAt;
    private String createdByName;

    public StockAdjustment(String adjustmentId, Inventory item, String batchId, Type type, Reason reason,
            int quantity, String evidence, LocalDate adjustedOn) {
        if (item == null) {
            throw new IllegalArgumentException("Select a relief item.");
        }
        if (batchId == null || batchId.trim().isEmpty()) {
            throw new IllegalArgumentException("Select the batch to adjust.");
        }
        if (type == null || reason == null) {
            throw new IllegalArgumentException("Choose the adjustment type and reason category.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be a whole number greater than 0.");
        }
        if (evidence == null || evidence.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter the reason or evidence for this adjustment.");
        }
        if (type == Type.INCREASE && reason != Reason.CORRECTION) {
            throw new IllegalArgumentException("Only a correction can increase stock. Record new goods as a donation.");
        }
        this.adjustmentId = adjustmentId;
        this.itemId = item.getItemId();
        this.itemName = item.getItemName();
        this.unit = item.getUnit();
        this.batchId = batchId.trim();
        this.type = type;
        this.reason = reason;
        this.quantity = quantity;
        this.evidence = evidence.trim();
        this.adjustedOn = adjustedOn == null ? Time.today() : adjustedOn;
    }

    /** Rebuilds a stored adjustment for history screens. */
    public StockAdjustment(String adjustmentId, String itemId, String itemName, String unit, String batchId,
            Type type, Reason reason, int quantity, int before, int after, String evidence,
            LocalDate adjustedOn, LocalDateTime createdAt, String createdByName) {
        this.adjustmentId = adjustmentId;
        this.itemId = itemId;
        this.itemName = itemName;
        this.unit = unit;
        this.batchId = batchId;
        this.type = type;
        this.reason = reason;
        this.quantity = quantity;
        this.beforeQuantity = before;
        this.afterQuantity = after;
        this.evidence = evidence;
        this.adjustedOn = adjustedOn;
        this.createdAt = createdAt;
        this.createdByName = createdByName;
    }

    public String getAdjustmentId() { return adjustmentId; }
    void setAdjustmentId(String adjustmentId) { this.adjustmentId = adjustmentId; }
    public String getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public String getUnit() { return unit; }
    public String getBatchId() { return batchId; }
    public Type getType() { return type; }
    public Reason getReason() { return reason; }
    public int getQuantity() { return quantity; }
    public String getEvidence() { return evidence; }
    public LocalDate getAdjustedOn() { return adjustedOn; }
    public int getBeforeQuantity() { return beforeQuantity; }
    public int getAfterQuantity() { return afterQuantity; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getCreatedByName() { return createdByName; }

    void recordResult(int before, int after, LocalDateTime when, String by) {
        this.beforeQuantity = before;
        this.afterQuantity = after;
        this.createdAt = when;
        this.createdByName = by;
    }

    /** The signed change, for example -4 or +2. */
    public int signedQuantity() {
        return type == Type.DECREASE ? -quantity : quantity;
    }
}
