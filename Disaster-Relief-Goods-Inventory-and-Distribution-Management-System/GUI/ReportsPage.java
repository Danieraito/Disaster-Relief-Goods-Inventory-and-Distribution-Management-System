package GUI;

import GUI.Theme.Tone;
import LIB.BeneficiaryController;
import LIB.BeneficiaryRepository;
import LIB.DisasterController;
import LIB.Disaster;
import LIB.DonationController;
import LIB.Donor;
import LIB.DonorRepository;
import LIB.ReportService;
import LIB.ReportTable;
import LIB.Time;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;

/** One place to build stock, beneficiary, donation and release reports and export them. */
class ReportsPage extends Page {
    private static final String[] GROUPS = { "Inventory", "Disaster & beneficiaries", "Donations", "Distribution" };
    private static final String ALL_CATEGORIES = "All items & categories";
    private static final String ALL_DISASTERS = "All disasters";
    private static final String ALL_DONORS = "All donors";
    private static final String ALL_HOUSEHOLDS = "All households";

    private int group;
    private String type;
    private String period = "Last 30 days";
    private String category = ALL_CATEGORIES;
    private Disaster disaster;
    private Donor donor;
    private BeneficiaryRepository.Row household;
    private List<Disaster> disasters = new ArrayList<>();
    private List<Donor> donors = new ArrayList<>();
    private List<BeneficiaryRepository.Row> households = new ArrayList<>();
    private ReportTable table;
    private String problem;

    @Override String module() { return "Reports"; }
    @Override String crumb() { return "Reports"; }

    private String[] types() {
        switch (group) {
            case 0:
                return ReportService.INVENTORY_TYPES;
            case 1:
                return ReportService.BENEFICIARY_TYPES;
            case 2:
                return ReportService.DONATION_TYPES;
            default:
                return ReportService.DISTRIBUTION_TYPES;
        }
    }

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

    private ReportService.Params params() {
        ReportService.Params params = new ReportService.Params();
        params.barangay = Session.workspace;
        params.type = type;
        params.category = category.equals(ALL_CATEGORIES) ? null : category;
        params.from = group >= 2 ? from() : null;
        params.disasterId = disaster == null ? null : disaster.getDisasterId();
        params.donorId = donor == null ? null : donor.getDonorId();
        params.householdId = household == null ? null : household.householdId;
        return params;
    }

    @Override
    void load() throws Exception {
        if (type == null || !Arrays.asList(types()).contains(type)) {
            type = types()[0];
        }
        disasters = new DisasterController().list(Session.workspace, Session.user);
        List<DonorRepository.Row> donorRows = new DonationController().donorRows(Session.workspace);
        donors = new ArrayList<>();
        for (DonorRepository.Row row : donorRows) {
            donors.add(row.donor);
        }
        if (group == 3 && households.isEmpty()) {
            Disaster scope = disaster != null ? disaster : disasters.isEmpty() ? null : disasters.get(0);
            if (scope != null) {
                households = new BeneficiaryController().page(scope, new BeneficiaryRepository.Filter(), 1, 300, Session.user).rows;
            }
        }
        problem = null;
        try {
            table = new ReportService().build(GROUPS[group], params());
        } catch (Exception error) {
            table = null;
            problem = Task.message(error);
        }
    }

    @Override
    JComponent build() {
        boolean canExport = Session.user.canExportReports();
        Row exports = new Row(10);
        if (canExport && table != null) {
            exports.put(UI.secondary("Export Excel", () -> Exports.save(shell, table, true, filterLines())),
                    UI.primary("Export PDF", () -> Exports.save(shell, table, false, filterLines())));
        }
        VBox page = new VBox(20);
        page.put(UI.pageHeader("Reports", "One reporting workspace for stock, donations, household needs and releases.", exports));
        if (!canExport) {
            page.put(UI.alert(Tone.INFO, "Read-only reports", "Encoders can view reports on screen. Exporting is available to "
                    + "Barangay Officials and Admins."));
        }
        page.put(UI.tabs(Arrays.asList(GROUPS), group, index -> {
            group = index;
            type = null;
            category = ALL_CATEGORIES;
            reload();
        }));
        page.put(filterCard());
        if (problem != null) {
            page.put(UI.alert(Tone.DANGER, "The report could not be built", problem));
            return page;
        }
        List<JComponent> stats = new ArrayList<>();
        for (String[] pair : table.summary) {
            stats.add(UI.stat(pair[0], pair[1], pair[0].toLowerCase().contains("expired") ? "Quarantined, never released"
                    : pair[0].toLowerCase().contains("low") ? "Needs replenishment" : " ", pair[0].toLowerCase().contains("expired")
                    ? Tone.DANGER : pair[0].toLowerCase().contains("low") ? Tone.WARN : Tone.NEUTRAL));
        }
        if (!stats.isEmpty()) {
            page.put(new Cols(18, equalWeights(stats.size())).stretch().put(stats.toArray(new JComponent[0])));
        }
        double[] weights = new double[table.headers.size()];
        Arrays.fill(weights, 1);
        weights[0] = 1.8;
        DataTable data = new DataTable(table.headers.toArray(new String[0]), weights);
        for (List<String> row : table.rows) {
            data.row(row.toArray());
        }
        if (table.rows.isEmpty()) {
            data.empty("Nothing matches these filters.");
        }
        data.footer(table.rows.size() + " rows" + (table.note.isEmpty() ? "" : " · " + table.note));
        VBox heading = new VBox(4);
        heading.put(UI.h2(table.title), UI.muted("Generated by " + Session.user.getName() + " · " + Time.longStamp(Time.now())));
        Card result = new Card(14);
        result.put(heading, data);
        page.put(result);
        return page;
    }

    private static double[] equalWeights(int count) {
        double[] weights = new double[count];
        Arrays.fill(weights, 1);
        return weights;
    }

    private String[] filterLines() {
        List<String> lines = new ArrayList<>();
        lines.add("Report: " + GROUPS[group] + " · " + type);
        if (group >= 2) {
            lines.add("Period: " + period);
        }
        if (!category.equals(ALL_CATEGORIES)) {
            lines.add("Category: " + category);
        }
        if (disaster != null) {
            lines.add("Disaster: " + disaster.getName());
        }
        if (donor != null) {
            lines.add("Donor: " + donor.getName());
        }
        if (household != null) {
            lines.add("Beneficiary: " + household.head);
        }
        return lines.toArray(new String[0]);
    }

    private JComponent filterCard() {
        Dropdown<String> typeBox = Dropdown.of(types());
        typeBox.setSelected(type);
        typeBox.onChange(() -> {
            type = typeBox.getSelected();
            reload();
        });
        List<String> categories = new ArrayList<>();
        categories.add(ALL_CATEGORIES);
        categories.addAll(Arrays.asList(BeneficiaryRepository.NEED_CATEGORIES));
        Dropdown<String> categoryBox = new Dropdown<>(categories, text -> text);
        categoryBox.setSelected(category);
        categoryBox.onChange(() -> category = categoryBox.getSelected());
        List<JComponent> cells = new ArrayList<>();
        cells.add(new Field("Report type", typeBox));
        if (group == 0) {
            cells.add(new Field("As of", new Input(Time.longStamp(Time.now()))).readOnly());
        } else if (group >= 2) {
            Dropdown<String> periodBox = Dropdown.of("Last 7 days", "Last 30 days", "This month", "Last 90 days", "All time");
            periodBox.setSelected(period);
            periodBox.onChange(() -> period = periodBox.getSelected());
            cells.add(new Field("Period", periodBox));
        }
        if (group != 1) {
            cells.add(new Field("Item / category", categoryBox));
        }
        if (group == 1 || group == 3) {
            List<Disaster> options = new ArrayList<>(disasters);
            Dropdown<Disaster> disasterBox = new Dropdown<>(options, item -> item.getName() + " · " + item.getDisasterId());
            disasterBox.placeholder(ALL_DISASTERS);
            disasterBox.noSelection();
            disasterBox.setSelected(disaster);
            disasterBox.onChange(() -> {
                disaster = disasterBox.getSelected();
                households = new ArrayList<>();
                household = null;
            });
            cells.add(new Field("Disaster / area", disasterBox, "Leave empty for all disasters."));
        }
        if (group == 2) {
            Dropdown<Donor> donorBox = new Dropdown<>(donors, Donor::getName);
            donorBox.placeholder(ALL_DONORS);
            donorBox.noSelection();
            donorBox.setSelected(donor);
            donorBox.onChange(() -> donor = donorBox.getSelected());
            cells.add(new Field("Donor", donorBox, "Leave empty for all donors."));
        }
        if (group == 3) {
            Dropdown<BeneficiaryRepository.Row> householdBox = new Dropdown<>(households, row -> row.head + " · " + row.householdId);
            householdBox.placeholder(ALL_HOUSEHOLDS);
            householdBox.noSelection();
            householdBox.setSelected(household);
            householdBox.onChange(() -> household = householdBox.getSelected());
            cells.add(new Field("Beneficiary", householdBox, households.isEmpty() ? "Choose a disaster to list its households." : "Optional."));
        }
        Btn generate = UI.primary("Generate report", this::reload);
        Card card = new Card(14);
        VBox heading = new VBox(4);
        heading.put(UI.h2("Report filters"), UI.muted("Exports include the selected filters, generation time and generating officer."));
        card.put(heading);
        List<JComponent> row = new ArrayList<>();
        for (JComponent cell : cells) {
            row.add(cell);
            if (row.size() == 3) {
                card.put(new Cols(14, 1, 1, 1).put(row.toArray(new JComponent[0])));
                row.clear();
            }
        }
        if (!row.isEmpty()) {
            double[] weights = new double[row.size()];
            Arrays.fill(weights, 1);
            card.put(new Cols(14, weights).put(row.toArray(new JComponent[0])));
        }
        card.put(UI.left(generate));
        return card;
    }
}
