package GUI;

import GUI.Theme.Tone;
import LIB.CategoryRules;
import LIB.Inventory;
import LIB.InventoryController;
import LIB.InventoryRepository;
import LIB.StockMovement;
import LIB.Time;
import LIB.Units;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Add relief item / Edit item. */
class ItemFormPage extends Page {
    static final String[] CATEGORIES = { "Food", "Water", "Hygiene kits", "Clothing", "Medicine", "Shelter materials" };
    static final String[] UNITS = { "Pack", "Box", "kg", "Liter", "Kit", "Piece" };

    private final String itemId;
    private Inventory item;
    private LocalDate lastMovement;
    private boolean unitLocked;
    private String flash;
    private String fatal;

    ItemFormPage() {
        this.itemId = null;
    }

    ItemFormPage(String itemId, String flash) {
        this.itemId = itemId;
        this.flash = flash;
    }

    private boolean isNew() {
        return itemId == null;
    }

    @Override String module() { return "Inventory"; }
    @Override String crumb() { return isNew() ? "Inventory / Add item" : "Inventory / Edit item"; }

    @Override
    void load() throws Exception {
        if (isNew()) {
            return;
        }
        InventoryController controller = new InventoryController();
        item = controller.find(itemId, Session.user);
        if (item == null) {
            throw new IllegalStateException("This item no longer exists.");
        }
        unitLocked = new LIB.InventoryRepository().hasMovements(itemId);
        lastMovement = null;
        for (StockMovement movement : controller.history(item)) {
            if (movement.changesStock()) {
                lastMovement = movement.when.toLocalDate();
                break;
            }
        }
    }

    @Override
    JComponent build() {
        LocalDate today = Time.today();
        Input name = new Input(isNew() ? "" : item.getItemName());
        Dropdown<String> category = Dropdown.of(CATEGORIES);
        category.setSelected(isNew() ? "Food" : item.getCategory());
        List<String> unitOptions = new ArrayList<>(Arrays.asList(UNITS));
        String currentUnit = isNew() ? "Pack" : labelFor(item.getUnit(), unitOptions);
        Dropdown<String> unit = new Dropdown<>(unitOptions, text -> text);
        unit.setSelected(currentUnit);
        Input quantity = new Input(isNew() ? "" : ItemText.quantity(item) + " · Read only");
        Input expiry = new Input(isNew() ? "" : item.getEarliestExpiry() == null ? "Not applicable" : Time.date(item.getEarliestExpiry()));
        Input threshold = new Input(isNew() ? "" : String.valueOf(item.getLowStockThreshold()));
        Input storage = new Input(isNew() ? "" : item.getStorageLocation());
        Area note = new Area("", isNew() ? 3 : 3);
        Input opening = new Input("");
        if (isNew()) {
            opening.placeholder("0");
            expiry.placeholder("e.g. 31 Mar 2027");
        }

        Field fName = new Field("Item name *", name);
        Field fCategory = new Field("Category *", category);
        Field fUnit = new Field("Unit *", unit, isNew() ? "Pack, box, kg, liter, kit or piece"
                : unitLocked ? "Unit cannot change while there are stock movements." : "Pack, box, kg, liter, kit or piece");
        Field fOpening = new Field("Opening quantity *", opening, "Enter 0 or a positive whole number.");
        Field fQuantity = new Field("Current quantity", quantity, "Use Adjust Stock to change quantities.").readOnly();
        Field fExpiry = isNew() ? new Field("Expiry date", expiry, "Required for food, water and medicine batches.")
                : new Field("Expiry date", expiry, "Expiry belongs to each stock batch.").readOnly();
        Field fThreshold = new Field("Low-stock threshold *", threshold, isNew() ? "Alert when available stock falls below this number."
                : "Available stock: " + Units.quantity(item.getAvailableQuantity(today), item.getUnit()) + ".");
        Field fStorage = new Field("Storage location *", storage);
        Field fNote = new Field(isNew() ? "Opening stock note" : "Reason for change *", note);
        if (unitLocked) {
            unit.setEnabled(false);
        }

        VBox errorBanner = new VBox(0);
        Wrap bottom = new Wrap(isNew() ? "Complete the required fields to enable saving."
                : "Changes are recorded with your name, date and time.", Theme.regular(12), Theme.MUTED);
        Btn save = UI.primary(isNew() ? "Save item" : "Save changes");

        Runnable validate = () -> {
            List<String> problems = new ArrayList<>();
            boolean complete = !name.getText().trim().isEmpty() && !storage.getText().trim().isEmpty()
                    && !threshold.getText().trim().isEmpty() && (isNew() ? !opening.getText().trim().isEmpty()
                            : !note.getText().trim().isEmpty());
            fOpening.error(null);
            fThreshold.error(null);
            fExpiry.error(null);
            if (isNew() && !opening.getText().trim().isEmpty()) {
                try {
                    Fmt.parseCount(opening.getText(), "Quantity");
                } catch (IllegalArgumentException error) {
                    fOpening.error("Enter 0 or a positive whole number.");
                    problems.add("Quantity must be a whole number of 0 or more.");
                }
            }
            if (!threshold.getText().trim().isEmpty()) {
                try {
                    Fmt.parseCount(threshold.getText(), "Low-stock threshold");
                } catch (IllegalArgumentException error) {
                    fThreshold.error("Enter 0 or a positive whole number.");
                    problems.add("The low-stock threshold must be a whole number of 0 or more.");
                }
            }
            if (isNew() && !expiry.getText().trim().isEmpty()) {
                try {
                    LocalDate date = Fmt.parseDate(expiry.getText());
                    if (date != null && date.isBefore(today)) {
                        fExpiry.error("This date has already passed.");
                        problems.add("Expired goods cannot be added as stock.");
                    }
                } catch (IllegalArgumentException error) {
                    fExpiry.error(error.getMessage());
                    problems.add("The expiry date is not valid.");
                }
            }
            errorBanner.removeAll();
            if (!problems.isEmpty()) {
                errorBanner.put(UI.alert(Tone.DANGER, "Check the highlighted fields before saving",
                        problems.get(0) + (isNew() ? " No inventory record has been created." : " Nothing was changed.")));
            }
            save.setEnabled(problems.isEmpty() && complete);
            bottom.setText(!problems.isEmpty() ? "Correct the highlighted fields to enable saving."
                    : !complete ? (isNew() ? "Complete the required fields to enable saving."
                            : "Enter the reason for the change to enable saving.")
                            : "Changes are recorded with your name, date and time.");
            errorBanner.revalidate();
            errorBanner.repaint();
        };
        DocumentListener listener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { validate.run(); }
            @Override public void removeUpdate(DocumentEvent event) { validate.run(); }
            @Override public void changedUpdate(DocumentEvent event) { validate.run(); }
        };
        for (javax.swing.text.JTextComponent component : new javax.swing.text.JTextComponent[] { name, opening, expiry,
            threshold, storage, note }) {
            component.getDocument().addDocumentListener(listener);
        }
        category.onChange(validate);
        unit.onChange(validate);

        Card form = new Card(14);
        form.put(UI.h2(isNew() ? "Item information" : "Item details"),
                UI.muted(isNew() ? "Required fields are marked with *." : "Update item details without changing recorded stock."));
        form.put(new Cols(14, 1.4, 1).put(fName, fCategory));
        if (isNew()) {
            form.put(new Cols(14, 1, 1).put(fUnit, fOpening), new Cols(14, 1, 1).put(fExpiry, fThreshold));
        } else {
            form.put(new Cols(14, 1, 1).put(fUnit, fQuantity), new Cols(14, 1, 1).put(fExpiry, fThreshold));
        }
        form.put(fStorage, fNote);
        Btn cancel = UI.secondary(isNew() ? "Cancel" : "Back to inventory", () -> go(isNew() ? new InventoryPage()
                : new ItemDetailPage(itemId, null)));
        save.addActionListener(event -> submit(name, category, unit, opening, expiry, threshold, storage, note, save,
                errorBanner));
        form.put(new Cols(14, 1, 0, 0).put(bottom, cancel, save));

        VBox page = new VBox(18);
        page.put(UI.pageHeader(isNew() ? "Add relief item" : "Edit item · " + item.getItemName(),
                isNew() ? "Create an item record and record its opening stock."
                        : item.getItemId() + " · Update item details without changing recorded stock.", null));
        if (flash != null) {
            page.put(UI.alert(Tone.TEAL, "Item changes saved", flash));
        }
        page.put(errorBanner, new Cols(22, 2.2, 1).put(form, isNew() ? addSide(today) : editSide(today)));
        validate.run();
        return page;
    }

    private static String labelFor(String unit, List<String> options) {
        for (String option : options) {
            if (option.equalsIgnoreCase(unit)) {
                return option;
            }
        }
        options.add(unit);
        return unit;
    }

    private JComponent addSide(LocalDate today) {
        Card card = new Card(12);
        card.put(UI.h2("Before you save"), UI.left(UI.pill("New item record", Tone.INFO)),
                UI.para("Opening stock creates an auditable inventory entry. Later changes to quantities must use Adjust Stock."),
                UI.fact("Created by", Session.user.getName()), UI.fact("Role", Session.user.getRoleLabel()),
                UI.fact("Date", Time.date(today)), UI.fact("Item ID", "Assigned after save"),
                UI.note(Tone.INFO, "Prevent duplicate records", "Search existing items before creating a new one. "
                        + "Donations to an existing item should be recorded in Donations."),
                UI.smallPara("Saved feedback: the new item receives an ITM reference and appears in Inventory with its opening quantity."));
        return card;
    }

    private JComponent editSide(LocalDate today) {
        Card card = new Card(12);
        Tone tone = item.isLowStock(today) ? Tone.WARN : Tone.TEAL;
        card.put(UI.h2("Record context"), UI.left(UI.pill(item.isLowStock(today) ? "Low stock" : "Available", tone)),
                UI.fact("Item ID", item.getItemId()), UI.fact("Stock", ItemText.quantity(item)),
                UI.fact("Created", (item.getCreatedAt() == null ? "—" : Time.date(item.getCreatedAt().toLocalDate()))
                        + (item.getCreatedByName().isEmpty() ? "" : " · " + item.getCreatedByName())),
                UI.fact("Last movement", lastMovement == null ? "—" : Time.date(lastMovement)),
                UI.fact("Last edit", item.getUpdatedAt() == null ? "—" : Time.stamp(item.getUpdatedAt())),
                UI.para("Each edit logs the previous and new value, reason, user, date and time. Stock movements remain unchanged."),
                UI.left(UI.secondary("View item history", () -> go(new ItemDetailPage(itemId, null)))));
        return card;
    }

    private void submit(Input name, Dropdown<String> category, Dropdown<String> unit, Input opening, Input expiry,
            Input threshold, Input storage, Area note, Btn save, VBox errorBanner) {
        save.setEnabled(false);
        String unitValue = unit.getSelected().toLowerCase();
        try {
            int limit = Fmt.parseCount(threshold.getText(), "Low-stock threshold");
            if (isNew()) {
                int count = Fmt.parseCount(opening.getText(), "Quantity");
                LocalDate date = Fmt.parseDate(expiry.getText());
                if (CategoryRules.needsExpiry(category.getSelected()) && count > 0 && date == null) {
                    throw new IllegalArgumentException("Enter the expiry date for " + category.getSelected().toLowerCase()
                            + " items.");
                }
                Task.run(() -> new InventoryController().addItem(name.getText(), category.getSelected(), count, unitValue,
                        limit, storage.getText(), date == null ? null : date.toString(), note.getText(),
                        Session.workspace, Session.user), created -> go(new ItemDetailPage(created.getItemId(),
                        "Item saved — " + created.getItemName() + " (" + created.getItemId() + ") was added with "
                        + Units.quantity(count, created.getUnit()) + " of opening stock.")), error -> fail(error, save, errorBanner));
            } else {
                Task.run(() -> new InventoryController().updateItem(item, name.getText(), category.getSelected(),
                        unitValue, limit, storage.getText(), note.getText(), Session.user),
                        (InventoryRepository.EditResult result) -> {
                            flash = result.message;
                            reload();
                        }, error -> fail(error, save, errorBanner));
            }
        } catch (IllegalArgumentException error) {
            fail(error, save, errorBanner);
        }
    }

    private void fail(Throwable error, Btn save, VBox errorBanner) {
        save.setEnabled(true);
        errorBanner.removeAll();
        errorBanner.put(UI.alert(Tone.DANGER, "This could not be saved", Task.message(error)));
        errorBanner.revalidate();
        errorBanner.repaint();
    }
}
