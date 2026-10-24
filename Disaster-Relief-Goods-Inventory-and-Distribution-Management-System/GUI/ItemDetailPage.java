package GUI;

import GUI.Theme.Tone;
import LIB.Inventory;
import LIB.InventoryBatch;
import LIB.InventoryController;
import LIB.StockMovement;
import LIB.Time;
import LIB.Units;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;

/** One item: figures, batches and the full stock history. */
class ItemDetailPage extends Page {
    private final String itemId;
    private final String flash;
    private Inventory item;
    private List<StockMovement> history = new ArrayList<>();
    private int tab;

    ItemDetailPage(String itemId, String flash) {
        this.itemId = itemId;
        this.flash = flash;
    }

    @Override String module() { return "Inventory"; }
    @Override String crumb() { return "Inventory / Item detail"; }

    @Override
    void load() throws Exception {
        InventoryController controller = new InventoryController();
        item = controller.find(itemId, Session.user);
        if (item == null) {
            throw new IllegalStateException("This item no longer exists.");
        }
        history = controller.history(item);
    }

    @Override
    JComponent build() {
        LocalDate today = Time.today();
        LocalDate since = today.minusDays(30);
        int received = 0;
        int released = 0;
        int damaged = 0;
        int spoiled = 0;
        int corrected = 0;
        for (StockMovement movement : history) {
            if (movement.when.toLocalDate().isBefore(since)) {
                continue;
            }
            if (movement.kind == StockMovement.Kind.DONATION || movement.kind == StockMovement.Kind.OPENING) {
                received += movement.delta;
            } else if (movement.kind == StockMovement.Kind.RELEASE) {
                released += -movement.delta;
            } else if (movement.kind == StockMovement.Kind.ADJUSTMENT && movement.delta < 0) {
                if ("DAMAGE".equals(movement.reasonCategory)) {
                    damaged += -movement.delta;
                } else if ("SPOILAGE".equals(movement.reasonCategory)) {
                    spoiled += -movement.delta;
                } else {
                    corrected += -movement.delta;
                }
            }
        }
        List<String> outParts = new ArrayList<>();
        if (released > 0) {
            outParts.add(released + " released");
        }
        if (damaged > 0) {
            outParts.add(damaged + " damaged");
        }
        if (spoiled > 0) {
            outParts.add(spoiled + " spoiled");
        }
        if (corrected > 0) {
            outParts.add(corrected + " corrected");
        }
        int available = item.getAvailableQuantity(today);
        boolean low = item.isLowStock(today);

        Row buttons = new Row(10);
        buttons.put(UI.secondary("Edit item", () -> go(new ItemFormPage(itemId, null))));
        if (Session.canManage() && !item.isArchived()) {
            buttons.put(UI.primary("Adjust stock", () -> go(new AdjustStockPage(itemId))));
        }
        VBox page = new VBox(20);
        page.put(UI.pageHeader(item.getItemName(), item.getItemId() + " · " + item.getCategory() + " · "
                + item.getStorageLocation(), buttons));
        if (flash != null) {
            page.put(UI.alert(Tone.TEAL, "Saved", flash));
        }
        if (item.isArchived()) {
            Btn restore = UI.secondary("Restore item", this::restore);
            restore.setVisible(Session.canManage());
            page.put(UI.alert(Tone.INFO, "This item is archived", "It is hidden from new transactions; its movement history "
                    + "and reporting references are kept. Reason: " + item.getArchivedReason(), restore));
        }
        String unit = item.getUnit();
        page.put(new Cols(18, 1, 1, 1, 1).stretch().put(
                UI.stat("Recorded quantity", Fmt.n(item.getQuantity()), Units.plural(2, unit).substring(0, 1).toUpperCase()
                        + Units.plural(2, unit).substring(1) + " · " + (available == item.getQuantity() ? "all available"
                        : Fmt.n(available) + " available"), Tone.NEUTRAL),
                UI.stat("Low-stock threshold", Fmt.n(item.getLowStockThreshold()), low
                        ? Units.quantity(Math.max(1, item.getLowStockThreshold() - available + 1), unit) + " below threshold"
                        : "Above threshold", low ? Tone.WARN : Tone.NEUTRAL),
                UI.stat("Received this period", Fmt.n(received), Time.shortDate(since) + " – " + Time.date(today), Tone.NEUTRAL),
                UI.stat("Released / adjusted", Fmt.n(released + damaged + spoiled + corrected),
                        outParts.isEmpty() ? "Nothing released" : String.join(" · ", outParts), Tone.NEUTRAL)));
        if (low && !item.isArchived()) {
            page.put(UI.alert(Tone.WARN, "Replenishment needed", "Available stock is below " + Units.quantity(
                    item.getLowStockThreshold(), unit) + ". Receive donations to replenish stock; do not overwrite the "
                    + "quantity in Edit Item."));
        }
        page.put(new Cols(22, 1, 1).put(record(today), batches(today)));
        page.put(historyCard());
        return page;
    }

    private JComponent record(LocalDate today) {
        Card card = new Card(12);
        card.put(UI.h2("Item record"), UI.fact("Category", item.getCategory()), UI.fact("Unit", capitalized(item.getUnit())),
                UI.fact("Expiry", ItemText.expiry(item)), UI.fact("Storage", item.getStorageLocation()),
                UI.fact("Last updated", lastUpdated()));
        return card;
    }

    private String lastUpdated() {
        for (StockMovement movement : history) {
            if (movement.kind == StockMovement.Kind.EDIT || movement.changesStock()) {
                return movement.actor + " · " + Time.stamp(movement.when);
            }
        }
        return item.getCreatedAt() == null ? "—" : item.getCreatedByName() + " · " + Time.stamp(item.getCreatedAt());
    }

    private static String capitalized(String text) {
        return text == null || text.isEmpty() ? "" : text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    private JComponent batches(LocalDate today) {
        DataTable table = new DataTable(new String[] { "Batch", "Quantity", "Availability" }, 1.6, 1, 1.2);
        int shown = 0;
        for (InventoryBatch batch : item.getBatches()) {
            if (batch.getRemainingQuantity() == 0) {
                continue;
            }
            shown++;
            Pill status = batch.isExpired(today) ? UI.pill("Expired", Tone.DANGER)
                    : batch.getExpiryLocalDate() != null && !batch.getExpiryLocalDate().isAfter(today.plusDays(StockAlerts.EXPIRING_DAYS))
                    ? UI.pill("Expiring soon", Tone.WARN) : UI.pill("Available", Tone.TEAL);
            table.row(new String[] { batch.getBatchId(), batch.getExpiryLocalDate() == null ? "No expiry"
                    : "Expires " + Time.date(batch.getExpiryLocalDate()) }, Units.quantity(batch.getRemainingQuantity(),
                    item.getUnit()), UI.left(status));
        }
        if (shown == 0) {
            table.empty("No stock on hand.");
        }
        Card card = new Card(14);
        card.put(UI.h2("Stock batch"), table);
        if (Session.canManage() && !item.isArchived()) {
            card.put(UI.left(UI.secondary("Delete item safely", () -> go(new DeleteItemPage(itemId)))));
        }
        return card;
    }

    private JComponent historyCard() {
        List<String> tabs = Arrays.asList("All movements", "Adjustments", "Record edits");
        Card card = new Card(14);
        VBox head = new VBox(4);
        head.put(UI.h2("Stock adjustment history"),
                UI.muted("Immutable movement records · all times Philippine Standard Time."));
        card.put(head, UI.tabs(tabs, tab, index -> {
            tab = index;
            rebuild();
        }));
        DataTable table = new DataTable(new String[] { "Reference / time", "Movement", "Before → after", "Reason / source",
            "Recorded by" }, 1.4, 1.2, 1.3, 1.6, 1.2);
        int count = 0;
        for (StockMovement movement : history) {
            if (tab == 1 && movement.kind != StockMovement.Kind.ADJUSTMENT
                    || tab == 2 && movement.kind != StockMovement.Kind.EDIT) {
                continue;
            }
            if (++count > 15) {
                break;
            }
            String change;
            String title;
            if (movement.kind == StockMovement.Kind.EDIT) {
                title = "Record edit";
                change = Fmt.beforeAfter(movement.editBefore, movement.editAfter) + "\nStock stays " + movement.before;
            } else {
                title = Fmt.pretty(movement.title);
                change = movement.before + " → " + movement.after + " " + Units.plural(movement.after, item.getUnit());
            }
            table.row(new String[] { movement.reference, Time.stamp(movement.when) }, title, change.split("\n"),
                    movement.detail, movement.actor);
        }
        if (count == 0) {
            table.empty("No records in this view.");
        }
        table.footer("Movement totals include all release records; only recent entries are shown. "
                + "No history is removed when an item is archived.");
        card.put(table);
        return card;
    }

    private void restore() {
        Task.run(() -> new InventoryController().restoreItem(item, Session.user), this::reload,
                error -> Dialogs.error(shell, error));
    }
}
