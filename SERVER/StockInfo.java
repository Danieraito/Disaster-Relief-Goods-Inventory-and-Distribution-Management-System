package SERVER;

import LIB.Inventory;
import LIB.InventoryBatch;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Works out expired, expiring and available stock for one item from its batches. */
final class StockInfo {
    private final Inventory item;
    private final int total;
    private final int expiredQuantity;
    private final int expiringQuantity;
    private final int available;
    private final LocalDate earliestExpiry;
    private final boolean low;

    StockInfo(Inventory item, LocalDate today) {
        this.item = item;
        int expired = 0;
        int expiring = 0;
        LocalDate earliest = null;
        for (InventoryBatch batch : item.getBatches()) {
            if (batch.getRemainingQuantity() <= 0) {
                continue;
            }
            LocalDate expiry = parse(batch.getExpiryDate());
            if (expiry == null) {
                continue;
            }
            if (earliest == null || expiry.isBefore(earliest)) {
                earliest = expiry;
            }
            if (expiry.isBefore(today)) {
                expired += batch.getRemainingQuantity();
            } else if (!expiry.isAfter(today.plusDays(30))) {
                expiring += batch.getRemainingQuantity();
            }
        }
        this.total = item.getQuantity();
        this.expiredQuantity = expired;
        this.expiringQuantity = expiring;
        this.available = total - expired;
        this.earliestExpiry = earliest;
        this.low = available <= item.getLowStockThreshold();
    }

    private static LocalDate parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Most urgent problem first: expired, low stock, expiring soon, otherwise available. */
    private String statusKey() {
        if (expiredQuantity > 0) {
            return "expired";
        }
        if (low) {
            return "low";
        }
        if (expiringQuantity > 0) {
            return "expiring";
        }
        return "available";
    }

    private String statusText() {
        switch (statusKey()) {
            case "expired":
                return expiredQuantity + " expired";
            case "low":
                return "Low stock";
            case "expiring":
                return "Expiring soon";
            default:
                return "Available";
        }
    }

    Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getItemId());
        map.put("name", item.getItemName());
        map.put("category", item.getCategory());
        map.put("unit", item.getUnit());
        map.put("total", total);
        map.put("available", available);
        map.put("expiredQuantity", expiredQuantity);
        map.put("expiringQuantity", expiringQuantity);
        map.put("threshold", item.getLowStockThreshold());
        map.put("earliestExpiry", earliestExpiry == null ? null : earliestExpiry.toString());
        map.put("storage", item.getStorageLocation());
        map.put("barangay", item.getBarangay() == null ? null : item.getBarangay().getName());
        map.put("low", low);
        map.put("status", statusKey());
        map.put("statusText", statusText());

        List<Object> batches = new ArrayList<>();
        for (InventoryBatch batch : item.getBatches()) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("id", batch.getBatchId());
            b.put("received", batch.getReceivedDate());
            b.put("expiry", batch.getExpiryDate());
            b.put("receivedQuantity", batch.getReceivedQuantity());
            b.put("remainingQuantity", batch.getRemainingQuantity());
            batches.add(b);
        }
        map.put("batches", batches);
        return map;
    }
}
