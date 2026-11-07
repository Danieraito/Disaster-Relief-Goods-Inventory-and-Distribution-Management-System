package GUI;

import GUI.Theme.Tone;
import LIB.Donations;
import LIB.DonationController;
import LIB.DonationRepository;
import LIB.Donor;
import LIB.Inventory;
import LIB.InventoryController;
import LIB.ReportService;
import LIB.Time;
import LIB.TransactionStatus;
import java.awt.Component;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/** Donation history with the pending approvals and the entry points for new donations and donors. */
class DonationsPage extends Page {
    private static final String ALL_TYPES = "All donor types";
    private static final String ALL_ITEMS = "All items";
    private static final String ALL_STATUS = "All status";

    private final String flash;
    private String search;
    private String period = "Last 30 days";
    private String donorType = ALL_TYPES;
    private String itemName = ALL_ITEMS;
    private String status = ALL_STATUS;
    private List<DonationRepository.Row> rows = new ArrayList<>();
    private List<DonationRepository.Row> confirmedPeriod = new ArrayList<>();
    private List<Inventory> items = new ArrayList<>();
    private int donorCount;
    private int pending;
    private int recordedStock;
    private final VBox tableHolder = new VBox(0);
    private javax.swing.Timer debounce;

    DonationsPage() {
        this(null, "");
    }

    DonationsPage(String flash) {
        this(flash, "");
    }

    DonationsPage(String flash, String search) {
        this.flash = flash;
        this.search = search == null ? "" : search;
    }

    @Override String module() { return "Donations"; }
    @Override String crumb() { return "Donations"; }

    private LocalDate from() {
        LocalDate today = Time.today();
        switch (period) {
            case "Last 7 days":
                return today.minusDays(7);
            case "Last 90 days":
                return today.minusDays(90);
            case "This month":
                return today.withDayOfMonth(1);
            case "All time":
                return null;
            default:
                return today.minusDays(30);
        }
    }

    private DonationRepository.Filter filter() {
        DonationRepository.Filter filter = new DonationRepository.Filter();
        filter.text = search;
        filter.from = from();
        filter.donorType = donorType.equals(ALL_TYPES) ? null : donorType.equals("Individual") ? Donor.Type.INDIVIDUAL
                : donorType.equals("Organization") ? Donor.Type.ORGANIZATION : Donor.Type.AGENCY;
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
        DonationController controller = new DonationController();
        items = new InventoryController().list(Session.workspace, false, Session.user);
        rows = controller.list(Session.workspace, filter(), Session.user);
        DonationRepository.Filter all = new DonationRepository.Filter();
        all.status = TransactionStatus.APPROVED;
        all.from = Time.today().minusDays(30);
        confirmedPeriod = controller.list(Session.workspace, all, Session.user);
        DonationRepository.Filter waiting = new DonationRepository.Filter();
        waiting.status = TransactionStatus.PENDING;
        pending = controller.list(Session.workspace, waiting, Session.user).size();
        donorCount = controller.donorRows(Session.workspace).size();
        recordedStock = 0;
        for (Inventory item : items) {
            recordedStock += item.getQuantity();
        }
    }

    @Override
    JComponent build() {
        VBox page = new VBox(20);
        page.put(UI.pageHeader("Donations", "Record incoming goods, keep donor records and issue acknowledgments.",
                UI.actions(UI.secondary("Manage donors", () -> go(new DonorsPage())),
                        UI.primary("Record donation", () -> go(new DonationFormPage())))));
        if (flash != null) {
            page.put(UI.alert(Tone.TEAL, "Saved", flash));
        }
        if (pending > 0 && Session.canManage()) {
            page.put(UI.alert(Tone.WARN, pending + (pending == 1 ? " donation is" : " donations are") + " waiting for approval",
                    "Stock is posted only after a Barangay Official or Admin confirms the donation.",
                    UI.secondary("Show pending", () -> {
                        status = "Pending approval";
                        reload();
                    })));
        }
        DonationRepository.Row latest = confirmedPeriod.isEmpty() ? null : confirmedPeriod.get(0);
        page.put(new Cols(18, 1, 1, 1, 1).stretch().put(
                UI.stat("Donations this period", String.valueOf(confirmedPeriod.size()), Time.shortDate(Time.today().minusDays(30))
                        + " – " + Time.date(Time.today()), Tone.NEUTRAL),
                UI.stat("Active donors", String.valueOf(donorCount), "Individuals, organizations & agencies", Tone.NEUTRAL),
                UI.stat("Latest donation", latest == null ? "—" : Fmt.n(latest.totalUnits),
                        latest == null ? "No confirmed donations yet" : "Mixed units · " + latest.donationId, Tone.NEUTRAL),
                UI.stat("Current total stock", Fmt.n(recordedStock), items.size() + " active item records", Tone.TEAL)));

        Btn export = UI.secondary("Export donations", this::export);
        export.setVisible(Session.user.canExportReports());
        VBox heading = new VBox(4);
        heading.put(UI.h2("Donation history"), UI.muted("Stock is posted only after transaction review and confirmation."));
        Card card = new Card(14);
        card.put(UI.titleRow(heading, export), filters(), tableHolder);
        fillTable();
        page.put(card);
        return page;
    }

    private JComponent filters() {
        Input box = new Input(search).placeholder("Search donation ID or donor");
        box.getDocument().addDocumentListener(new SimpleListener(() -> {
            search = box.getText();
            if (debounce != null) {
                debounce.stop();
            }
            debounce = new javax.swing.Timer(350, event -> refreshRows());
            debounce.setRepeats(false);
            debounce.start();
        }));
        Dropdown<String> periodBox = Dropdown.of("Last 7 days", "Last 30 days", "This month", "Last 90 days", "All time");
        periodBox.setSelected(period);
        periodBox.onChange(() -> {
            period = periodBox.getSelected();
            refreshRows();
        });
        Dropdown<String> typeBox = Dropdown.of(ALL_TYPES, "Individual", "Organization", "Government agency");
        typeBox.setSelected(donorType);
        typeBox.onChange(() -> {
            donorType = typeBox.getSelected();
            refreshRows();
        });
        List<String> names = new ArrayList<>();
        names.add(ALL_ITEMS);
        for (Inventory item : items) {
            names.add(item.getItemName());
        }
        Dropdown<String> itemBox = new Dropdown<>(names, text -> text);
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
        Field[] cells = { new Field(null, periodBox), new Field(null, typeBox), new Field(null, itemBox), new Field(null, statusBox) };
        for (Field cell : cells) {
            cell.setPreferredSize(new java.awt.Dimension(160, 44));
        }
        return new Cols(12, 1, 0, 0, 0, 0).put(new Field(null, box), cells[0], cells[1], cells[2], cells[3]);
    }

    private void refreshRows() {
        Task.run(() -> new DonationController().list(Session.workspace, filter(), Session.user), result -> {
            rows = result;
            fillTable();
        }, error -> Dialogs.error(shell, error));
    }

    private void fillTable() {
        tableHolder.removeAll();
        DataTable table = new DataTable(new String[] { "Reference / received", "Donor", "Items received", "Receiving officer",
            "Inventory impact", "Action" }, 1.4, 1.4, 1.8, 1.2, 1.3, 0.9);
        for (DonationRepository.Row row : rows) {
            boolean confirmed = row.status == TransactionStatus.APPROVED;
            Component impact = confirmed ? DataTable.toComponent(row.goods.size() == 1
                    ? "+" + row.goods.get(0).split(" ", 3)[0] + " " + row.goods.get(0).split(" ", 3)[1]
                    : "+" + Fmt.n(row.totalUnits) + " mixed units") : statusBadge(row);
            String action = confirmed ? "View receipt" : row.status == TransactionStatus.PENDING && Session.canManage() ? "Review"
                    : row.status == TransactionStatus.DRAFT ? "Edit draft" : "View";
            table.rowClick(() -> go(new DonationDetailPage(row.donationId, null)),
                    new String[] { row.donationId, Time.stamp(row.recordedAt != null ? row.recordedAt : row.date.atStartOfDay()) },
                    new String[] { row.donorName, typeLabel(row.donorType) }, String.join("\n", row.goods).split("\n"),
                    row.officer.isEmpty() ? (row.createdBy.isEmpty() ? "—" : row.createdBy + " (submitted)") : row.officer,
                    impact, UI.link(action, () -> go(new DonationDetailPage(row.donationId, null))));
        }
        if (rows.isEmpty()) {
            table.empty("No donations match this view.");
        }
        table.footer("Showing " + rows.size() + " donations · Select a donation to view its receipt or review it.");
        tableHolder.put(table);
        tableHolder.revalidate();
        tableHolder.repaint();
    }

    private static Component statusBadge(DonationRepository.Row row) {
        switch (row.status) {
            case PENDING:
                return UI.left(UI.pill("Pending approval", Tone.WARN));
            case DRAFT:
                return UI.left(UI.pill("Draft", Tone.NEUTRAL));
            default:
                return UI.left(UI.pill("Rejected", Tone.DANGER));
        }
    }

    static String typeLabel(Donor.Type type) {
        switch (type) {
            case ORGANIZATION:
                return "Organization";
            case AGENCY:
                return "Government agency";
            default:
                return "Individual";
        }
    }

    private void export() {
        ReportService.Params params = new ReportService.Params();
        params.barangay = Session.workspace;
        params.type = "By period";
        params.from = from();
        Exports.report(shell, "Donations", params, true, "Period: " + period);
    }
}
