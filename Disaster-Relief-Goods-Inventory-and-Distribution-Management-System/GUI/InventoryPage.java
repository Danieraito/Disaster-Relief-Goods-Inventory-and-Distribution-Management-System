package GUI;

import GUI.Theme.Tone;
import LIB.Inventory;
import LIB.InventoryBatch;
import LIB.InventoryController;
import LIB.ReportService;
import LIB.Time;
import LIB.Units;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Small wording helpers shared by the inventory screens. */
final class ItemText {
    private ItemText() {
    }

    static Row statusPills(Inventory item, LocalDate today) {
        Row row = new Row(6);
        if (item.isArchived()) {
            row.put(UI.pill("Archived", Tone.NEUTRAL));
            return row;
        }
        boolean any = false;
        if (item.hasExpiredStock(today)) {
            row.put(UI.pill(Fmt.n(item.getExpiredQuantity(today)) + " expired", Tone.DANGER));
            any = true;
        }
        if (item.isLowStock(today)) {
            row.put(UI.pill("Low stock", Tone.WARN));
            any = true;
        } else if (item.isExpiringSoon(today, StockAlerts.EXPIRING_DAYS)) {
            row.put(UI.pill("Expiring soon", Tone.WARN));
            any = true;
        }
        if (!any) {
            row.put(UI.pill("Available", Tone.TEAL));
        }
        return row;
    }

    /** The earliest expiry of the batches that still hold stock, with a footnote mark when batches differ. */
    static String expiry(Inventory item) {
        LocalDate earliest = item.getEarliestExpiry();
        if (earliest == null) {
            return "Not applicable";
        }
        Set<LocalDate> dates = new LinkedHashSet<>();
        for (InventoryBatch batch : item.getBatches()) {
            if (batch.getRemainingQuantity() > 0 && batch.getExpiryLocalDate() != null) {
                dates.add(batch.getExpiryLocalDate());
            }
        }
        return Time.date(earliest) + (dates.size() > 1 ? "¹" : "");
    }

    static String quantity(Inventory item) {
        return Units.quantity(item.getQuantity(), item.getUnit());
    }
}

class InventoryPage extends Page {
    private static final String ALL_CATEGORIES = "All categories";
    private static final String ALL_LOCATIONS = "All locations";

    private int tab;
    private String search = "";
    private String category = ALL_CATEGORIES;
    private String location = ALL_LOCATIONS;
    private List<Inventory> active = new ArrayList<>();
    private List<Inventory> archived = new ArrayList<>();
    private final VBox tableHolder = new VBox(0);

    InventoryPage() {
        this(0);
    }

    InventoryPage(int tab) {
        this.tab = tab;
    }

    @Override String module() { return "Inventory"; }
    @Override String crumb() { return "Inventory"; }

    @Override
    void load() throws Exception {
        active = new ArrayList<>();
        archived = new ArrayList<>();
        for (Inventory item : new InventoryController().list(Session.workspace, true, Session.user)) {
            (item.isArchived() ? archived : active).add(item);
        }
        active.sort(java.util.Comparator.comparing(Inventory::getItemId));
        archived.sort(java.util.Comparator.comparing(Inventory::getItemId));
    }

    private List<Inventory> inTab(int index) {
        LocalDate today = Time.today();
        if (index == 4) {
            return archived;
        }
        List<Inventory> out = new ArrayList<>();
        for (Inventory item : active) {
            if (index == 0 || index == 1 && item.isLowStock(today)
                    || index == 2 && item.isExpiringSoon(today, StockAlerts.EXPIRING_DAYS)
                    || index == 3 && item.hasExpiredStock(today)) {
                out.add(item);
            }
        }
        return out;
    }

    @Override
    JComponent build() {
        LocalDate today = Time.today();
        int recorded = 0;
        int expiredUnits = 0;
        Set<String> lowCategories = new LinkedHashSet<>();
        Set<String> expiredUnitsText = new LinkedHashSet<>();
        for (Inventory item : active) {
            recorded += item.getQuantity();
            expiredUnits += item.getExpiredQuantity(today);
            if (item.isLowStock(today)) {
                lowCategories.add(item.getCategory().toLowerCase());
            }
            if (item.hasExpiredStock(today)) {
                expiredUnitsText.add(item.getCategory().toLowerCase() + " " + Units.plural(2, item.getUnit()));
            }
        }
        List<Inventory> low = inTab(1);
        List<Inventory> expiring = inTab(2);
        List<Inventory> expired = inTab(3);

        VBox page = new VBox(20);
        Btn add = UI.primary("Add item", () -> go(new ItemFormPage()));
        page.put(UI.pageHeader("Inventory", "Track available goods, storage locations and safe release dates.", add));
        page.put(new Cols(18, 1, 1, 1, 1).stretch().put(
                UI.stat("Recorded stock", Fmt.n(recorded), "Mixed units · " + active.size() + " active item records", Tone.NEUTRAL),
                UI.stat("Low-stock items", String.valueOf(low.size()), low.isEmpty() ? "All above threshold"
                        : String.join(" & ", lowCategories), Tone.WARN),
                UI.stat("Expiring in 30 days", String.valueOf(expiring.size()), expiring.isEmpty() ? "Nothing close to expiry"
                        : expiring.get(0).getItemName() + " · " + Time.shortDate(expiring.get(0).getEarliestUsableExpiry(today)),
                        Tone.NEUTRAL),
                UI.stat("Expired stock", Fmt.n(expiredUnits), expiredUnits == 0 ? "No expired goods"
                        : String.join(" & ", expiredUnitsText) + " · unavailable", Tone.DANGER)));
        if (!expired.isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (Inventory item : expired) {
                lines.add(item.getItemName() + ": " + Units.quantity(item.getQuantity(), item.getUnit()) + " recorded, "
                        + Fmt.n(item.getExpiredQuantity(today)) + " expired and quarantined, "
                        + Fmt.n(item.getAvailableQuantity(today)) + " available.");
            }
            page.put(UI.alert(Tone.DANGER, "Expired goods are excluded from distribution availability",
                    String.join(" ", lines) + " Use Adjust Stock to document approved disposal."));
        }

        Btn export = UI.secondary("Export stock report", this::exportStock);
        export.setVisible(Session.user.canExportReports());
        Card records = new Card(14);
        VBox heading = new VBox(4);
        heading.put(UI.h2("Relief item records"), UI.muted("Stock is shown by its own unit; mixed units are not equivalent quantities."));
        records.put(UI.titleRow(heading, export));
        records.put(UI.tabs(Arrays.asList("All items", "Low stock (" + low.size() + ")", "Expiring (" + expiring.size() + ")",
                "Expired (" + expired.size() + ")", "Archived"), tab, index -> {
                    tab = index;
                    rebuild();
                }));
        records.put(filters());
        records.put(tableHolder);
        refreshTable();
        page.put(records);
        return page;
    }

    private JComponent filters() {
        Input box = new Input(search).placeholder("Search item name or item ID");
        box.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { changed(); }
            @Override public void removeUpdate(DocumentEvent event) { changed(); }
            @Override public void changedUpdate(DocumentEvent event) { changed(); }

            private void changed() {
                search = box.getText();
                refreshTable();
            }
        });
        List<String> categories = new ArrayList<>();
        categories.add(ALL_CATEGORIES);
        List<String> locations = new ArrayList<>();
        locations.add(ALL_LOCATIONS);
        for (Inventory item : active) {
            if (!categories.contains(item.getCategory())) {
                categories.add(item.getCategory());
            }
            String place = item.getStorageLocation() == null ? "" : item.getStorageLocation();
            if (!place.isEmpty() && !locations.contains(place)) {
                locations.add(place);
            }
        }
        Dropdown<String> categoryBox = new Dropdown<>(categories, text -> text);
        categoryBox.setSelected(categories.contains(category) ? category : ALL_CATEGORIES);
        categoryBox.onChange(() -> {
            category = categoryBox.getSelected();
            refreshTable();
        });
        Dropdown<String> locationBox = new Dropdown<>(locations, text -> text);
        locationBox.setSelected(locations.contains(location) ? location : ALL_LOCATIONS);
        locationBox.onChange(() -> {
            location = locationBox.getSelected();
            refreshTable();
        });
        Field categoryCell = new Field(null, categoryBox);
        Field locationCell = new Field(null, locationBox);
        categoryCell.setPreferredSize(new java.awt.Dimension(190, 44));
        locationCell.setPreferredSize(new java.awt.Dimension(190, 44));
        return new Cols(12, 1, 0, 0).put(new Field(null, box), categoryCell, locationCell);
    }

    private void refreshTable() {
        tableHolder.removeAll();
        LocalDate today = Time.today();
        List<Inventory> rows = new ArrayList<>();
        String needle = search.trim().toLowerCase();
        for (Inventory item : inTab(tab)) {
            boolean matches = needle.isEmpty() || item.getItemName().toLowerCase().contains(needle)
                    || item.getItemId().toLowerCase().contains(needle);
            if (matches && (category.equals(ALL_CATEGORIES) || item.getCategory().equals(category))
                    && (location.equals(ALL_LOCATIONS) || location.equals(item.getStorageLocation()))) {
                rows.add(item);
            }
        }
        DataTable table = new DataTable(new String[] { "Item / category", "Quantity / unit", "Threshold", "Expiry",
            "Storage", "Status / action" }, 2.2, 1.4, 1.2, 1.4, 1.5, 2.2);
        for (Inventory item : rows) {
            String quantity = ItemText.quantity(item) + (item.hasExpiredStock(today)
                    ? "\n" + Fmt.n(item.getAvailableQuantity(today)) + " available" : "");
            Row status = new Row(10);
            status.put(ItemText.statusPills(item, today));
            status.put(UI.link("View / Edit", () -> go(new ItemDetailPage(item.getItemId(), null))));
            if (item.isArchived() && Session.canManage()) {
                status.put(UI.link("Restore", () -> restore(item)));
            }
            table.rowClick(() -> go(new ItemDetailPage(item.getItemId(), null)),
                    new String[] { item.getItemName(), item.getCategory() + " · " + item.getItemId() }, quantity,
                    Units.quantity(item.getLowStockThreshold(), item.getUnit()), ItemText.expiry(item),
                    item.getStorageLocation(), status);
        }
        if (rows.isEmpty()) {
            table.empty(tab == 4 ? "No archived items." : "No items match this view.");
        }
        table.footer("Showing " + rows.size() + " of " + inTab(tab).size() + (tab == 4 ? " archived" : " active")
                + " records · ¹ Earliest batch expiry · Select an item to view batches, adjust stock or delete safely.");
        tableHolder.put(table);
        tableHolder.revalidate();
        tableHolder.repaint();
    }

    private void restore(Inventory item) {
        Task.run(() -> new InventoryController().restoreItem(item, Session.user), this::reload,
                error -> Dialogs.error(shell, error));
    }

    private void exportStock() {
        ReportService.Params params = new ReportService.Params();
        params.barangay = Session.workspace;
        params.type = "Current stock";
        Exports.report(shell, "Inventory", params, true, "Report: Current stock");
    }
}
