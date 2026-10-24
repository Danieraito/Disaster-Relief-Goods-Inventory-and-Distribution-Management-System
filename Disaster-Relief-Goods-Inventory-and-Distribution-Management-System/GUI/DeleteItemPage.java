package GUI;

import GUI.Theme.Tone;
import LIB.Inventory;
import LIB.InventoryController;
import LIB.Time;
import javax.swing.JComponent;

/** Delete an unused item, or archive a stocked or used one instead. */
class DeleteItemPage extends Page {
    private final String itemId;
    private Inventory item;
    private boolean deletable;

    DeleteItemPage(String itemId) {
        this.itemId = itemId;
    }

    @Override String module() { return "Inventory"; }
    @Override String crumb() { return "Inventory / Delete confirmation"; }

    @Override
    void load() throws Exception {
        InventoryController controller = new InventoryController();
        item = controller.find(itemId, Session.user);
        if (item == null) {
            throw new IllegalStateException("This item no longer exists.");
        }
        deletable = controller.canDelete(item);
    }

    @Override
    JComponent build() {
        VBox page = new VBox(18);
        page.put(UI.pageHeader("Delete an item safely", "Review stock and linked records before removing an item.", null));
        page.put(new Cols(22, 1.3, 1).put(deletable ? deleteCard() : blockedCard(), archiveCard()));
        return page;
    }

    private JComponent deleteCard() {
        Card card = new Card(14);
        card.put(UI.h2("Confirm deletion · " + item.getItemName()),
                UI.left(UI.pill("Zero stock · no linked transactions", Tone.NEUTRAL)),
                UI.fact("Item ID", item.getItemId()), UI.fact("Category", item.getCategory()),
                UI.fact("Current quantity", ItemText.quantity(item)),
                UI.fact("Created", item.getCreatedAt() == null ? "—" : Time.date(item.getCreatedAt().toLocalDate())
                        + (item.getCreatedByName().isEmpty() ? "" : " · " + item.getCreatedByName())),
                UI.alert(Tone.DANGER, "This record will no longer appear in inventory",
                        "Only unused, zero-stock records can be deleted. A deletion audit record, including your reason, will be retained."));
        Area reason = new Area("", 3);
        Input typed = new Input("");
        Check verified = new Check("I verified the item ID and understand this action removes the unused item record.");
        Btn delete = UI.danger("Delete item");
        delete.setEnabled(false);
        Runnable refresh = () -> delete.setEnabled(!reason.getText().trim().isEmpty() && typed.getText().trim()
                .equalsIgnoreCase(item.getItemName()) && verified.isSelected());
        reason.getDocument().addDocumentListener(new SimpleListener(refresh));
        typed.getDocument().addDocumentListener(new SimpleListener(refresh));
        verified.addActionListener(event -> refresh.run());
        VBox error = new VBox(0);
        delete.addActionListener(event -> {
            delete.setEnabled(false);
            Task.run(() -> new InventoryController().deleteItem(item, reason.getText(), typed.getText(), Session.user),
                    () -> go(new InventoryPage()), failure -> {
                        error.removeAll();
                        error.put(UI.alert(Tone.DANGER, "The item was not deleted", Task.message(failure)));
                        error.revalidate();
                        delete.setEnabled(true);
                    });
        });
        card.put(new Field("Reason for deletion *", reason), new Field("Type the item name to confirm *", typed),
                verified, error, new Cols(14, 1, 0, 0).put(UI.smallPara("Changes are recorded with your name, date and time."),
                        UI.secondary("Keep item", () -> go(new ItemDetailPage(itemId, null))), delete));
        return card;
    }

    private JComponent blockedCard() {
        Card card = new Card(14);
        card.put(UI.h2("Delete " + item.getItemName()), UI.left(UI.pill("Stocked or used item", Tone.WARN)),
                UI.fact("Item ID", item.getItemId()), UI.fact("Current stock", ItemText.quantity(item)),
                UI.fact("Linked records", "Donations, releases or adjustments"),
                UI.alert(Tone.WARN, "Cannot delete a stocked or used item", item.getItemName()
                        + " has stock or transaction history. Account for stock through authorized releases or adjustments, "
                        + "then archive the item instead."),
                UI.left(UI.secondary("Return to item details", () -> go(new ItemDetailPage(itemId, null)))));
        return card;
    }

    private JComponent archiveCard() {
        Card card = new Card(14);
        card.put(UI.h2(deletable ? "Archive instead" : "When deletion is blocked"));
        card.put(UI.para("Archiving hides an inactive item from new transactions, but preserves its movement history "
                + "and reporting references."));
        if (item.isArchived()) {
            card.put(UI.alert(Tone.INFO, "Already archived", "Restore it from the Archived tab of Inventory."));
            return card;
        }
        Area reason = new Area("", 3);
        Btn archive = UI.secondary("Archive item");
        archive.setEnabled(false);
        reason.getDocument().addDocumentListener(new SimpleListener(() -> archive.setEnabled(!reason.getText().trim().isEmpty())));
        VBox error = new VBox(0);
        archive.addActionListener(event -> {
            archive.setEnabled(false);
            Task.run(() -> new InventoryController().archiveItem(item, reason.getText(), Session.user),
                    () -> go(new InventoryPage(4)), failure -> {
                        error.removeAll();
                        error.put(UI.alert(Tone.DANGER, "The item was not archived", Task.message(failure)));
                        error.revalidate();
                        archive.setEnabled(true);
                    });
        });
        card.put(new Field("Reason for archiving *", reason), error, UI.left(archive));
        return card;
    }
}
