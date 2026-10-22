package GUI;

import GUI.Theme.Tone;
import LIB.Inventory;
import LIB.InventoryBatch;
import LIB.Time;
import LIB.Units;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Works out the low-stock, expired and expiring-soon notices shown on the dashboard and inventory screens. */
final class StockAlerts {
    static final int EXPIRING_DAYS = 30;

    static final class Notice {
        final Tone tone;
        final String kind;
        final String title;
        final String message;
        final Inventory item;

        Notice(Tone tone, String kind, String title, String message, Inventory item) {
            this.tone = tone;
            this.kind = kind;
            this.title = title;
            this.message = message;
            this.item = item;
        }
    }

    private StockAlerts() {
    }

    static List<Notice> of(List<Inventory> items) {
        LocalDate today = Time.today();
        List<Notice> notices = new ArrayList<>();
        for (Inventory item : items) {
            if (item.isLowStock(today)) {
                boolean partlyExpired = item.hasExpiredStock(today);
                notices.add(new Notice(Tone.WARN, "LOW", item.getItemName() + " · " + Units.quantity(item.getQuantity(),
                        item.getUnit()) + (partlyExpired ? " recorded" : " remaining"),
                        "Threshold: " + Units.quantity(item.getLowStockThreshold(), item.getUnit()) + ". "
                        + (partlyExpired ? "Only " + Units.quantity(item.getAvailableQuantity(today), item.getUnit())
                                + " are available for release." : "Request replenishment before the next release."),
                        item));
            }
        }
        for (Inventory item : items) {
            for (InventoryBatch batch : item.getBatches()) {
                if (batch.isExpired(today) && batch.getRemainingQuantity() > 0) {
                    notices.add(new Notice(Tone.DANGER, "EXPIRED", item.getItemName() + " batch " + batch.getBatchId()
                            + " · " + Units.quantity(batch.getRemainingQuantity(), item.getUnit()) + " expired",
                            "Expired " + Time.date(batch.getExpiryLocalDate())
                            + ". Quarantine and log a spoilage adjustment.", item));
                }
            }
        }
        for (Inventory item : items) {
            if (item.isExpiringSoon(today, EXPIRING_DAYS)) {
                LocalDate expiry = item.getEarliestUsableExpiry(today);
                long days = ChronoUnit.DAYS.between(today, expiry);
                notices.add(new Notice(Tone.INFO, "EXPIRING", item.getItemName() + " · "
                        + Units.quantity(item.getQuantity(), item.getUnit()), "Expires " + Time.date(expiry)
                        + (days == 0 ? ", today" : ", in " + days + (days == 1 ? " day" : " days"))
                        + ". Prioritize this batch for safe release.", item));
            }
        }
        return notices;
    }

    static int count(List<Notice> notices, String kind) {
        int total = 0;
        for (Notice notice : notices) {
            if (notice.kind.equals(kind)) {
                total++;
            }
        }
        return total;
    }
}
