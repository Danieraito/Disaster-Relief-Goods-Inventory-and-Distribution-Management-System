package GUI;

import GUI.Theme.Tone;
import LIB.BeneficiaryController;
import LIB.BeneficiaryRepository;
import LIB.Disaster;
import LIB.DisasterController;
import LIB.DisasterStats;
import LIB.DistributionController;
import LIB.DistributionRepository;
import LIB.EvacuationCenter;
import LIB.EvacuationCenterRepository;
import LIB.Inventory;
import LIB.InventoryController;
import LIB.ReportService;
import LIB.Time;
import LIB.TransactionStatus;
import LIB.Units;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/** Release history for the chosen disaster. */
class DistributionPage extends Page {
    private static final String ALL_CENTERS = "All evacuation centers";
    private static final String ALL_ITEMS = "All items";
    private static final String ALL_STATUS = "All status";

    private final String flash;
    private List<Disaster> disasters = new ArrayList<>();
    private Disaster disaster;
    private DisasterStats stats;
    private List<Inventory> items = new ArrayList<>();
    private List<EvacuationCenter> centers = new ArrayList<>();
    private List<DistributionRepository.Row> rows = new ArrayList<>();
    private int pending;
    private int recordedStock;
    private String search = "";
    private String centerName = ALL_CENTERS;
    private String period = "Last 30 days";
    private String itemName = ALL_ITEMS;
    private String status = ALL_STATUS;
    private final VBox tableHolder = new VBox(0);
    private javax.swing.Timer debounce;

    DistributionPage() {
        this(null);
    }

    DistributionPage(String flash) {
        this.flash = flash;
    }

    @Override String module() { return "Distribution"; }
    @Override String crumb() { return "Distribution"; }

    static Pill statusPill(TransactionStatus status) {
        switch (status) {
            case APPROVED:
                return UI.pill("Confirmed", Tone.TEAL);
            case PENDING:
                return UI.pill("Pending approval", Tone.WARN);
            case DRAFT:
                return UI.pill("Draft", Tone.NEUTRAL);
            default:
                return UI.pill("Rejected", Tone.DANGER);
        }
    }

    private LocalDate from() {
        LocalDate today = Time.today();
        switch (period) {
            case "Last 7 days":
                return today.minusDays(7);
            case "Last 90 days":
                return today.minusDays(90);
            case "All time":
                return null;
            default:
                return today.minusDays(30);
        }
    }

    private DistributionRepository.Filter filter() {
        DistributionRepository.Filter filter = new DistributionRepository.Filter();
        filter.disasterId = disaster == null ? null : disaster.getDisasterId();
        filter.text = centerName.equals(ALL_CENTERS) ? search : (search.isEmpty() ? centerName : search);
        filter.from = from();
        for (Inventory item : items) {
            if (item.getItemName().equals(itemName)) {
                filter.itemId = item.getItemId();
            }
        }
        filter.status = status.equals(ALL_STATUS) ? null : status.equals("Confirmed") ? TransactionStatus.APPROVED
                : status.equals("Pending approval") ? TransactionStatus.PENDING : status.equals("Draft")
                        ? TransactionStatus.DRAFT : TransactionStatus.REJECTED;
        return filter;
    }

    @Override
    void load() throws Exception {
        DisasterController disasterController = new DisasterController();
        disasters = disasterController.list(Session.workspace, Session.user);
        disaster = DisasterBand.resolve(disasters);
        items = new InventoryController().list(Session.workspace, false, Session.user);
        centers = new EvacuationCenterRepository().findByBarangay(Session.workspace);
        recordedStock = 0;
        for (Inventory item : items) {
            recordedStock += item.getQuantity();
        }
        if (disaster == null) {
            return;
        }
        stats = disasterController.stats(disaster);
        DistributionController controller = new DistributionController();
        rows = controller.list(Session.workspace, filter(), Session.user);
        DistributionRepository.Filter waiting = new DistributionRepository.Filter();
        waiting.disasterId = disaster.getDisasterId();
        waiting.status = TransactionStatus.PENDING;
        pending = controller.list(Session.workspace, waiting, Session.user).size();
    }

    @Override
    JComponent build() {
        VBox page = new VBox(20);
        page.put(UI.pageHeader("Distribution", "Release available goods to evacuation centers and retain a claim record.",
                disaster != null && disaster.isActive() ? UI.primary("New distribution", () -> go(new DistributionFormPage())) : null));
        if (disaster == null) {
            page.put(DisasterBand.empty(this, "Releases are always recorded against an event."));
            return page;
        }
        page.put(DisasterBand.of(this, disaster, disasters));
        if (flash != null) {
            page.put(UI.alert(Tone.TEAL, "Saved", flash));
        }
        if (pending > 0 && Session.canManage()) {
            page.put(UI.alert(Tone.WARN, pending + (pending == 1 ? " release is" : " releases are") + " waiting for approval",
                    "Stock is deducted only after a Barangay Official or Admin confirms the release.",
                    UI.secondary("Show pending", () -> {
                        status = "Pending approval";
                        reload();
                    })));
        }
        DistributionRepository.Row latest = null;
        for (DistributionRepository.Row row : rows) {
            if (row.status == TransactionStatus.APPROVED) {
                latest = row;
                break;
            }
        }
        Inventory watch = null;
        LocalDate today = Time.today();
        for (Inventory item : items) {
            if (watch == null || item.getAvailableQuantity(today) - item.getLowStockThreshold()
                    < watch.getAvailableQuantity(today) - watch.getLowStockThreshold()) {
                watch = item;
            }
        }
        page.put(new Cols(18, 1, 1, 1, 1).stretch().put(
                UI.stat("Families assisted", Fmt.n(stats.assisted), disaster.getName() + " · "
                        + (stats.registered == 0 ? "0" : String.format("%.1f", stats.assisted * 100.0 / stats.registered)) + "%", Tone.NEUTRAL),
                UI.stat("Families pending", Fmt.n(stats.pending()), "Of " + Fmt.n(stats.registered) + " registered households", Tone.NEUTRAL),
                UI.stat("Latest release", latest == null ? "—" : Fmt.n(latest.goods.size()),
                        latest == null ? "No confirmed releases yet" : latest.distributionId + " · item lines", Tone.TEAL),
                watch == null ? UI.stat("Stock", Fmt.n(recordedStock), "No inventory items yet", Tone.NEUTRAL)
                        : UI.stat("Available " + watch.getItemName().toLowerCase(), Fmt.n(watch.getAvailableQuantity(today)),
                                "Threshold " + Fmt.n(watch.getLowStockThreshold()) + " · " + (watch.isLowStock(today)
                                        ? "below buffer" : "above buffer"), watch.isLowStock(today) ? Tone.WARN : Tone.NEUTRAL)));
        Btn export = UI.secondary("Export releases", this::export);
        export.setVisible(Session.user.canExportReports());
        VBox heading = new VBox(4);
        heading.put(UI.h2("Distribution history"), UI.muted("All confirmed releases are tied to a disaster, evacuation center, "
                + "barangay and officer."));
        Card card = new Card(14);
        card.put(UI.titleRow(heading, export), filters(), tableHolder);
        fillTable();
        page.put(card);
        return page;
    }

    private JComponent filters() {
        Input box = new Input(search).placeholder("Search release ID, evacuation center, barangay or household representative");
        box.getDocument().addDocumentListener(new SimpleListener(() -> {
            search = box.getText();
            if (debounce != null) {
                debounce.stop();
            }
            debounce = new javax.swing.Timer(350, event -> refreshRows());
            debounce.setRepeats(false);
            debounce.start();
        }));
        List<String> centerNames = new ArrayList<>();
        centerNames.add(ALL_CENTERS);
        for (EvacuationCenter center : centers) {
            centerNames.add(center.getName());
        }
        Dropdown<String> centerBox = new Dropdown<>(centerNames, text -> text);
        centerBox.setSelected(centerName);
        centerBox.onChange(() -> {
            centerName = centerBox.getSelected();
            refreshRows();
        });
        Dropdown<String> periodBox = Dropdown.of("Last 7 days", "Last 30 days", "Last 90 days", "All time");
        periodBox.setSelected(period);
        periodBox.onChange(() -> {
            period = periodBox.getSelected();
            refreshRows();
        });
        List<String> itemNames = new ArrayList<>();
        itemNames.add(ALL_ITEMS);
        for (Inventory item : items) {
            itemNames.add(item.getItemName());
        }
        Dropdown<String> itemBox = new Dropdown<>(itemNames, text -> text);
        itemBox.setSelected(itemName);
        itemBox.onChange(() -> {
            itemName = itemBox.getSelected();
            refreshRows();
        });
        Dropdown<String> statusBox = Dropdown.of(ALL_STATUS, "Confirmed", "Pending approval", "Draft", "Rejected");
        statusBox.setSelected(status);
        statusBox.onChange(() -> {
            status = statusBox.getSelected();
            refreshRows();
        });
        Field[] cells = { new Field(null, centerBox), new Field(null, periodBox), new Field(null, itemBox), new Field(null, statusBox) };
        for (Field cell : cells) {
            cell.setPreferredSize(new java.awt.Dimension(170, 44));
        }
        return new Cols(12, 1, 0, 0, 0, 0).put(new Field(null, box), cells[0], cells[1], cells[2], cells[3]);
    }

    private void refreshRows() {
        Task.run(() -> new DistributionController().list(Session.workspace, filter(), Session.user), result -> {
            rows = result;
            fillTable();
        }, error -> Dialogs.error(shell, error));
    }

    private void fillTable() {
        tableHolder.removeAll();
        DataTable table = new DataTable(new String[] { "Release / time", "Evacuation center / barangay", "Items released",
            "Releasing officer", "Household representative", "Status" }, 1.3, 1.6, 1.6, 1.2, 1.3, 1);
        for (DistributionRepository.Row row : rows) {
            table.rowClick(() -> go(new DistributionDetailPage(row.distributionId)),
                    new String[] { row.distributionId, Time.stamp(row.recordedAt) },
                    new String[] { row.centerName != null ? row.centerName : row.representative + " household",
                        row.centerName != null ? row.barangayName : "Direct household release · " + row.barangayName },
                    String.join("\n", row.goods).split("\n"), row.officer.isEmpty() ? (row.createdBy.isEmpty() ? "—"
                            : row.createdBy + " (submitted)") : row.officer, row.representative.isEmpty() ? "—" : row.representative,
                    UI.left(statusPill(row.status)));
        }
        if (rows.isEmpty()) {
            table.empty("No releases match this view.");
        }
        table.footer("Showing " + rows.size() + " releases · Select a release to view its receipt.");
        tableHolder.put(table);
        tableHolder.revalidate();
        tableHolder.repaint();
    }

    private void export() {
        ReportService.Params params = new ReportService.Params();
        params.barangay = Session.workspace;
        params.type = "By disaster";
        params.disasterId = disaster.getDisasterId();
        params.from = from();
        Exports.report(shell, "Distribution", params, true, "Event: " + disaster.getName(), "Period: " + period);
    }
}
