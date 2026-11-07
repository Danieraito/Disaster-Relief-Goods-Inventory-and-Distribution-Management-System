package GUI;

import GUI.Theme.Tone;
import LIB.BeneficiaryController;
import LIB.BeneficiaryRepository;
import LIB.Disaster;
import LIB.DisasterController;
import LIB.DisasterStats;
import LIB.DistributionController;
import LIB.DistributionRepository;
import LIB.Inventory;
import LIB.InventoryController;
import LIB.ReportService;
import LIB.Time;
import LIB.Units;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;

/** One disaster event: overview, households, releases and its report. */
class DisasterDetailPage extends Page {
    private final String disasterId;
    private final String flash;
    private int tab;
    private Disaster disaster;
    private DisasterStats stats;
    private BeneficiaryRepository.Page households;
    private List<DistributionRepository.Row> releases = new ArrayList<>();
    private List<Inventory> items = new ArrayList<>();

    DisasterDetailPage(String disasterId) {
        this(disasterId, null);
    }

    DisasterDetailPage(String disasterId, String flash) {
        this.disasterId = disasterId;
        this.flash = flash;
    }

    @Override String module() { return "Disasters"; }
    @Override String crumb() { return "Disasters / Event detail"; }

    @Override
    void load() throws Exception {
        DisasterController controller = new DisasterController();
        disaster = controller.find(disasterId, Session.user);
        if (disaster == null) {
            throw new IllegalStateException("This disaster no longer exists.");
        }
        Session.disasterId = disasterId;
        stats = controller.stats(disaster);
        households = new BeneficiaryController().page(disaster, new BeneficiaryRepository.Filter(), 1, 10, Session.user);
        DistributionRepository.Filter filter = new DistributionRepository.Filter();
        filter.disasterId = disasterId;
        filter.limit = 25;
        releases = new DistributionController().list(Session.workspace, filter, Session.user);
        items = new InventoryController().list(Session.workspace, false, Session.user);
    }

    @Override
    JComponent build() {
        VBox page = new VBox(20);
        Row buttons = new Row(10);
        if (disaster.isActive()) {
            if (Session.canManage()) {
                buttons.put(UI.secondary("Close event", this::close));
            }
            buttons.put(UI.secondary("Register household", () -> go(new RegisterHouseholdPage())));
            buttons.put(UI.primary("New distribution", () -> go(new DistributionFormPage())));
        }
        page.put(UI.pageHeader(disaster.getName(), disaster.getDisasterId() + " · Event overview and coordinated relief needs",
                buttons));
        if (flash != null) {
            page.put(UI.alert(Tone.TEAL, "Saved", flash));
        }
        if (disaster.isActive()) {
            page.put(UI.alert(Tone.TEAL, "Active event accepting household registrations", "Created by "
                    + disaster.getCreatedByName() + " on " + (disaster.getCreatedAt() == null ? Time.date(LocalDate.parse(disaster.getDate()))
                    : Time.longStamp(disaster.getCreatedAt())) + ". All linked assistance appears in this event's response report."));
        } else {
            page.put(UI.alert(Tone.INFO, "Closed event", "This event was closed"
                    + (disaster.getClosedAt() == null ? "" : " on " + Time.longStamp(disaster.getClosedAt()))
                    + ". Registration and new releases are no longer available; records stay in reports."));
        }
        page.put(new Cols(18, 1, 1, 1, 1).stretch().put(
                UI.stat("Registered families", Fmt.n(stats.registered), Fmt.n(stats.individuals) + " individuals", Tone.NEUTRAL),
                UI.stat("Assisted", Fmt.n(stats.assisted), stats.percentAssisted() + "% of registered families", Tone.TEAL),
                UI.stat("Awaiting assistance", Fmt.n(stats.pending()), "Across " + disaster.getAffectedAreas(), Tone.NEUTRAL),
                UI.stat("Vulnerable individuals", Fmt.n(stats.vulnerablePeople), "Readable breakdown below", Tone.NEUTRAL)));
        page.put(UI.tabs(Arrays.asList("Overview & needs", "Beneficiaries", "Distribution history", "Event report"), tab,
                index -> {
                    tab = index;
                    rebuild();
                }));
        if (tab == 0) {
            page.put(overview());
        } else if (tab == 1) {
            page.put(householdsTab());
        } else if (tab == 2) {
            page.put(releasesTab());
        } else {
            page.put(reportTab());
        }
        return page;
    }

    // ------------------------------------------------------------------ tabs

    private JComponent overview() {
        VBox box = new VBox(20);
        Card details = new Card(12);
        details.put(UI.h2("Event details"), UI.fact("Type / date", disaster.getType() + " · "
                + Time.date(LocalDate.parse(disaster.getDate()))), UI.fact("Severity", String.valueOf(disaster.getSeverity())),
                UI.fact("Affected areas", disaster.getAffectedAreas()),
                UI.fact("Evacuation site", disaster.getEvacuationSite().isEmpty() ? "—" : disaster.getEvacuationSite()),
                UI.fact("Response lead", disaster.getResponseLead().isEmpty() ? "—" : disaster.getResponseLead()));
        if (!disaster.getDescription().isEmpty()) {
            details.put(UI.para(disaster.getDescription()));
        }
        Card progress = new Card(14);
        progress.put(UI.h2("Assistance progress"), new Cols(10, 1, 0).put(UI.strong("Families served"),
                UI.muted(stats.assisted + " / " + stats.registered)),
                new Bar(stats.registered == 0 ? 0 : stats.assisted / (double) stats.registered));
        DataTable areas = new DataTable(new String[] { "Area", "Registered", "Assisted", "Pending" }, 1.4, 1, 1, 1);
        for (DisasterStats.AreaRow row : stats.areas) {
            areas.row(row.area, String.valueOf(row.registered), String.valueOf(row.assisted), String.valueOf(row.pending()));
        }
        if (stats.areas.isEmpty()) {
            areas.empty("No households registered yet.");
        }
        progress.put(areas);
        box.put(new Cols(22, 1, 1).put(details, progress));

        DataTable groups = new DataTable(new String[] { "Group", "Individuals" }, 2, 1);
        groups.row("Senior citizens", String.valueOf(stats.seniors)).row("Persons with disability (PWD)", String.valueOf(stats.pwd))
                .row("Pregnant members", String.valueOf(stats.pregnant)).row("Infants", String.valueOf(stats.infants));
        Card vulnerable = new Card(14);
        vulnerable.put(UI.h2("Vulnerable groups & housing"), groups,
                UI.fact("Totally damaged", stats.totallyDamaged + " households"),
                UI.fact("Partially damaged", stats.partiallyDamaged + " households"),
                UI.fact("Currently evacuated", stats.evacuated + " households"));
        box.put(new Cols(22, 1, 1).put(needsCard(), vulnerable));
        return box;
    }

    private JComponent needsCard() {
        Map<String, Integer> available = new LinkedHashMap<>();
        Map<String, Integer> buffer = new LinkedHashMap<>();
        Map<String, String> units = new LinkedHashMap<>();
        LocalDate today = Time.today();
        for (Inventory item : items) {
            available.merge(item.getCategory(), item.getAvailableQuantity(today), Integer::sum);
            buffer.merge(item.getCategory(), item.getLowStockThreshold(), Integer::sum);
            units.putIfAbsent(item.getCategory(), item.getUnit());
        }
        DataTable table = new DataTable(new String[] { "Assistance", "Needed", "Available", "Coverage" }, 1.6, 1, 1, 1);
        String[][] labels = { { "Food", "Food packs" }, { "Water", "Water" }, { "Hygiene kits", "Hygiene kits" },
            { "Clothing", "Clothing" }, { "Medicine", "Medicine" }, { "Shelter materials", "Shelter materials" } };
        int rows = 0;
        for (String[] label : labels) {
            Integer needed = stats.pendingNeeds.get(label[0]);
            if (needed == null || needed == 0) {
                continue;
            }
            rows++;
            int have = available.getOrDefault(label[0], 0);
            String unit = units.getOrDefault(label[0], "unit");
            Pill coverage = have < needed ? UI.pill("Short by " + Fmt.n(needed - have), Tone.DANGER)
                    : have - needed < buffer.getOrDefault(label[0], 0) ? UI.pill("Low buffer", Tone.WARN)
                    : UI.pill("Covered", Tone.TEAL);
            table.row(label[1], Units.quantity(needed, unit), Units.quantity(have, unit), UI.left(coverage));
        }
        if (rows == 0) {
            table.empty("No outstanding needs. Every registered household has been assisted.");
        }
        Card card = new Card(14);
        card.put(UI.h2("Outstanding assistance needs"), UI.muted("Estimated quantities for the " + stats.pending()
                + " pending households."), table, UI.smallPara("Planning estimate: 2 food packs, 12 liters of water and "
                + "1 hygiene kit per household. Verify individual needs before release."));
        return card;
    }

    private JComponent householdsTab() {
        DataTable table = new DataTable(new String[] { "Household / head", "Address", "Members / groups", "Status" },
                1.4, 1.5, 1.4, 1);
        for (BeneficiaryRepository.Row row : households.rows) {
            table.rowClick(() -> go(new HouseholdDetailPage(row.householdId, disaster)),
                    new String[] { row.householdId, row.head }, new String[] { row.address, row.purok },
                    new String[] { row.members + " members", BeneficiariesPage.groups(row) },
                    UI.left(UI.pill(row.assisted ? "Assisted" : "Pending", row.assisted ? Tone.TEAL : Tone.WARN)));
        }
        if (households.rows.isEmpty()) {
            table.empty("No households registered for this event yet.");
        }
        table.footer("Showing " + households.rows.size() + " of " + households.total + " households");
        Card card = new Card(14);
        card.put(UI.titleRow(UI.h2("Household records"), UI.secondary("Open all household records", () -> {
            Session.disasterId = disasterId;
            go(new BeneficiariesPage());
        })), table);
        return card;
    }

    private JComponent releasesTab() {
        DataTable table = new DataTable(new String[] { "Release / time", "Recipient", "Items released", "Releasing officer",
            "Status" }, 1.3, 1.6, 1.8, 1.2, 1);
        for (DistributionRepository.Row row : releases) {
            table.rowClick(() -> go(new DistributionDetailPage(row.distributionId)),
                    new String[] { row.distributionId, Time.stamp(row.recordedAt) }, row.recipient(),
                    String.join("\n", row.goods).split("\n"), row.officer.isEmpty() ? "—" : row.officer,
                    UI.left(DistributionPage.statusPill(row.status)));
        }
        if (releases.isEmpty()) {
            table.empty("No releases recorded for this event yet.");
        }
        Card card = new Card(14);
        card.put(UI.h2("Distribution history"), table);
        return card;
    }

    private JComponent reportTab() {
        Card card = new Card(14);
        ReportService.Params params = new ReportService.Params();
        params.barangay = Session.workspace;
        params.disasterId = disasterId;
        params.type = "Families & vulnerable groups";
        Row buttons = new Row(10);
        if (Session.user.canExportReports()) {
            buttons.put(UI.secondary("Export Excel", () -> Exports.report(shell, "Disaster & beneficiaries", params, true,
                    "Event: " + disaster.getName() + " (" + disasterId + ")")),
                    UI.secondary("Export PDF", () -> Exports.report(shell, "Disaster & beneficiaries", params, false,
                            "Event: " + disaster.getName() + " (" + disasterId + ")")));
        }
        card.put(UI.h2("Event report"), UI.para(disaster.getName() + " has " + stats.registered + " registered families ("
                + Fmt.n(stats.individuals) + " individuals). " + stats.assisted + " families have been assisted and "
                + stats.pending() + " are still waiting. " + stats.vulnerablePeople + " individuals belong to vulnerable "
                + "groups. Every linked release is part of this event's response report."), buttons);
        if (!Session.user.canExportReports()) {
            card.put(UI.mutedPara("Exporting reports is available to Barangay Officials and Admins."));
        }
        return card;
    }

    private void close() {
        Area reason = new Area("", 3);
        VBox form = new VBox(12);
        form.put(UI.para("Closing the event stops new household registrations and new releases for it. Existing records "
                + "stay in reports."), new Field("Reason for closing *", reason));
        String[] problem = new String[1];
        boolean ok = Dialogs.form(shell, "Close " + disaster.getName() + "?", null, form, "Close event", () -> {
            if (reason.getText().trim().isEmpty()) {
                return "Enter a reason for closing the event.";
            }
            try {
                new DisasterController().closeDisaster(disaster, reason.getText(), Session.user);
                return null;
            } catch (Exception error) {
                return Task.message(error);
            }
        }, 560);
        if (ok) {
            Session.disasterId = null;
            go(new DisastersPage());
        }
    }
}
