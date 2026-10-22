package GUI;

import GUI.Theme.Tone;
import LIB.Inventory;
import LIB.InventoryBatch;
import LIB.InventoryController;
import LIB.StockAdjustment;
import LIB.Time;
import LIB.Units;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/** Post a damage, spoilage or correction entry: form, review, then saved. */
class AdjustStockPage extends Page {
    private final String itemId;
    private Inventory item;
    private int step;
    private String type = "Decrease stock";
    private String reasonName = "Damage";
    private String quantityText = "";
    private String evidenceText = "";
    private String batchId;
    private String problem;
    private StockAdjustment saved;

    AdjustStockPage(String itemId) {
        this.itemId = itemId;
    }

    @Override String module() { return "Inventory"; }
    @Override String crumb() { return "Inventory / Adjust stock"; }

    @Override
    void load() throws Exception {
        item = new InventoryController().find(itemId, Session.user);
        if (item == null) {
            throw new IllegalStateException("This item no longer exists.");
        }
        if (batchId == null) {
            LocalDate today = Time.today();
            InventoryBatch pick = null;
            for (InventoryBatch batch : item.getBatches()) {
                if (batch.getRemainingQuantity() > 0 && (pick == null || batch.isExpired(today) && !pick.isExpired(today))) {
                    pick = batch;
                }
            }
            batchId = pick == null ? null : pick.getBatchId();
        }
    }

    private InventoryBatch selectedBatch() {
        return batchId == null ? null : item.findBatch(batchId);
    }

    private StockAdjustment.Reason reason() {
        return StockAdjustment.Reason.valueOf(reasonName.toUpperCase());
    }

    @Override
    JComponent build() {
        VBox page = new VBox(18);
        String title = step == 0 ? "Adjust stock" : step == 1 ? "Review stock adjustment" : "Adjustment posted";
        page.put(UI.pageHeader(title, item.getItemId() + " · " + item.getItemName() + " · "
                + (step == 2 ? "The movement is saved and cannot be overwritten." : "Verify this change before it is posted."), null));
        page.put(new Stepper(new String[] { "Select item & reason", "Review adjustment", "Saved movement" }, step));
        if (problem != null) {
            page.put(UI.alert(Tone.DANGER, "This adjustment cannot be posted", problem));
        }
        if (step == 2) {
            page.put(UI.alert(Tone.TEAL, "Adjustment posted · " + saved.getAdjustmentId(), Units.quantity(saved.getQuantity(),
                    saved.getUnit()) + " " + (saved.getType() == StockAdjustment.Type.DECREASE ? "removed from" : "added to")
                    + " " + saved.getItemName() + " (" + saved.getBeforeQuantity() + " → " + saved.getAfterQuantity() + "). "
                    + "Posted by " + saved.getCreatedByName() + " at " + Time.longStamp(saved.getCreatedAt()) + "."));
            page.put(UI.actions(UI.primary("Back to item", () -> go(new ItemDetailPage(itemId, null))),
                    UI.secondary("Back to inventory", () -> go(new InventoryPage()))));
            return page;
        }
        page.put(new Cols(22, 2.1, 1).put(step == 0 ? formCard() : reviewCard(), auditCard()));
        return page;
    }

    private JComponent formCard() {
        Input quantity = new Input(quantityText);
        quantity.getDocument().addDocumentListener(new SimpleListener(() -> quantityText = quantity.getText()));
        Dropdown<String> typeBox = Dropdown.of("Decrease stock", "Increase stock");
        typeBox.setSelected(type);
        typeBox.onChange(() -> {
            type = typeBox.getSelected();
            if (type.startsWith("Increase")) {
                reasonName = "Correction";
            }
            rebuild();
        });
        Dropdown<String> reasonBox = type.startsWith("Increase") ? Dropdown.of("Correction")
                : Dropdown.of("Damage", "Spoilage", "Correction");
        reasonBox.setSelected(reasonName);
        reasonBox.onChange(() -> reasonName = reasonBox.getSelected());
        List<InventoryBatch> batches = new ArrayList<>();
        for (InventoryBatch batch : item.getBatches()) {
            if (batch.getRemainingQuantity() > 0 || type.startsWith("Increase") && batch.getReceivedQuantity() > 0) {
                batches.add(batch);
            }
        }
        LocalDate today = Time.today();
        Dropdown<InventoryBatch> batchBox = new Dropdown<>(batches, batch -> batch.getBatchId() + " · "
                + Units.quantity(batch.getRemainingQuantity(), item.getUnit()) + (batch.getExpiryLocalDate() == null ? ""
                : " · " + (batch.isExpired(today) ? "expired " : "expires ") + Time.date(batch.getExpiryLocalDate())));
        batchBox.setSelected(selectedBatch());
        batchBox.onChange(() -> {
            batchId = batchBox.getSelected() == null ? null : batchBox.getSelected().getBatchId();
            rebuild();
        });
        Area evidence = new Area(evidenceText, 3);
        evidence.getDocument().addDocumentListener(new SimpleListener(() -> evidenceText = evidence.getText()));

        Card card = new Card(14);
        card.put(UI.h2("Adjustment details"));
        card.put(new Cols(14, 1, 1).put(new Field("Relief item", new Input(item.getItemName() + " · " + item.getItemId()))
                .readOnly(), new Field("Adjustment type *", typeBox)));
        card.put(new Cols(14, 1, 1).put(new Field("Quantity *", quantity, "In " + Units.plural(2, item.getUnit()) + ", whole number."),
                new Field("Reason category *", reasonBox, type.startsWith("Increase")
                        ? "Only a correction can increase stock." : "Damage · Spoilage · Correction")));
        card.put(new Field("Stock batch *", batchBox, "Choose the batch the goods belong to."));
        card.put(new Field("Reason / evidence *", evidence));
        card.put(new Cols(14, 1, 1).put(new Field("Adjustment date", new Input(Time.date(today))).readOnly(),
                new Field("Recording officer", new Input(Session.user.getName())).readOnly()));
        card.put(impact());
        card.put(new Cols(14, 1, 0, 0).put(UI.smallPara("Posting changes stock immediately and cannot be overwritten."),
                UI.secondary("Back to item", () -> go(new ItemDetailPage(itemId, null))),
                UI.primary("Review adjustment", this::toReview)));
        return card;
    }

    private JComponent reviewCard() {
        Card card = new Card(14);
        card.put(UI.h2("Adjustment details"));
        card.put(UI.fact("Relief item", item.getItemName() + " · " + item.getItemId()),
                UI.fact("Adjustment type", type), UI.fact("Quantity", Units.quantity(quantity(), item.getUnit())),
                UI.fact("Reason category", reasonName), UI.fact("Stock batch", batchId == null ? "—" : batchId),
                UI.fact("Reason / evidence", evidenceText.trim()), UI.fact("Adjustment date", Time.date(Time.today())),
                UI.fact("Recording officer", Session.user.getName()));
        card.put(impact());
        Btn confirm = UI.primary("Confirm adjustment");
        confirm.addActionListener(event -> post(confirm));
        card.put(new Cols(14, 1, 0, 0).put(UI.smallPara("Posting changes stock immediately and cannot be overwritten."),
                UI.secondary("Back to edit", () -> {
                    step = 0;
                    problem = null;
                    rebuild();
                }), confirm));
        return card;
    }

    private int quantity() {
        try {
            return Integer.parseInt(quantityText.trim().replace(",", ""));
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private JComponent impact() {
        int before = item.getQuantity();
        int change = type.startsWith("Decrease") ? -quantity() : quantity();
        int after = before + change;
        VBox box = new VBox(10);
        box.put(new Cols(14, 1, 1, 1).put(summary("Available before", Units.quantity(before, item.getUnit())),
                summary("Adjustment", (change >= 0 ? "+" : "−") + Units.quantity(Math.abs(change), item.getUnit())),
                summary("Available after", Units.quantity(Math.max(0, after), item.getUnit()))));
        if (after <= item.getLowStockThreshold() && quantity() > 0) {
            box.put(UI.alert(Tone.WARN, "Low-stock alert remains active", Units.quantity(Math.max(0, after), item.getUnit())
                    + " will remain at or below the threshold of " + item.getLowStockThreshold()
                    + ". This adjustment does not mark any household as assisted."));
        }
        return box;
    }

    private JComponent summary(String label, String value) {
        Card card = new Card(4);
        card.pad(14, 18, 14, 18);
        card.put(UI.small(label), UI.label(value, Theme.semibold(15), Theme.TEXT));
        return card;
    }

    private JComponent auditCard() {
        int before = item.getQuantity();
        int after = before + (type.startsWith("Decrease") ? -quantity() : quantity());
        Card card = new Card(12);
        card.put(UI.h2("Audit preview"), UI.left(UI.pill(step == 0 ? "Not yet posted" : "Ready to post", Tone.INFO)),
                UI.fact("Reference", "Assigned on confirmation"), UI.fact("Actor", Session.user.getName()),
                UI.fact("Time", "Assigned on confirmation"),
                UI.fact("Before / after", before + " → " + Math.max(0, after) + " " + Units.plural(2, item.getUnit())),
                UI.fact("Reason", reasonName + " · evidence required"),
                UI.note(Tone.INFO, "Correction, not erasure", "If a posted adjustment is wrong, record a new correction "
                        + "with a reason. The original movement remains in history."));
        return card;
    }

    private void toReview() {
        problem = null;
        try {
            int amount = Fmt.parseCount(quantityText, "Quantity");
            if (amount <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than 0.");
            }
            if (evidenceText.trim().isEmpty()) {
                throw new IllegalArgumentException("Enter the reason or evidence for this adjustment.");
            }
            InventoryBatch batch = selectedBatch();
            if (batch == null) {
                throw new IllegalArgumentException("Select the stock batch to adjust.");
            }
            if (type.startsWith("Decrease") && amount > batch.getRemainingQuantity()) {
                throw new IllegalArgumentException("Batch " + batch.getBatchId() + " only has "
                        + Units.quantity(batch.getRemainingQuantity(), item.getUnit()) + ".");
            }
            step = 1;
        } catch (IllegalArgumentException error) {
            problem = error.getMessage();
        }
        rebuild();
    }

    private void post(Btn confirm) {
        confirm.setEnabled(false);
        StockAdjustment.Type direction = type.startsWith("Decrease") ? StockAdjustment.Type.DECREASE
                : StockAdjustment.Type.INCREASE;
        Task.run(() -> new InventoryController().adjustStock(item, batchId, direction, reason(), quantity(),
                evidenceText, Time.today(), Session.user), result -> {
                    saved = result;
                    problem = null;
                    step = 2;
                    rebuild();
                }, error -> {
                    problem = Task.message(error);
                    step = 0;
                    rebuild();
                });
    }
}

/** A document listener that just runs one callback. */
final class SimpleListener implements javax.swing.event.DocumentListener {
    private final Runnable action;

    SimpleListener(Runnable action) {
        this.action = action;
    }

    @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { action.run(); }
    @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { action.run(); }
    @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { action.run(); }
}
