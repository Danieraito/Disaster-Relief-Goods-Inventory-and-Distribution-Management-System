package GUI;

import GUI.Theme.Tone;
import LIB.BeneficiaryController;
import LIB.BeneficiaryRepository;
import LIB.Disaster;
import LIB.DisasterController;
import LIB.DisasterStats;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;

/** Households registered for the chosen disaster. */
class BeneficiariesPage extends Page {
    private static final String ALL_PUROKS = "All puroks";
    private static final String ALL_DAMAGE = "All damage";
    private static final String ALL_STATUS = "All status";
    private static final int PAGE_SIZE = 10;

    private List<Disaster> disasters = new ArrayList<>();
    private Disaster disaster;
    private DisasterStats stats;
    private int[] counts = new int[4];
    private List<String> puroks = new ArrayList<>();
    private BeneficiaryRepository.Page households;
    private int tab;
    private int pageNumber = 1;
    private String search = "";
    private String purok = ALL_PUROKS;
    private String damage = ALL_DAMAGE;
    private final VBox tableHolder = new VBox(0);
    private javax.swing.Timer debounce;

    static String groups(BeneficiaryRepository.Row row) {
        List<String> labels = new ArrayList<>();
        if (row.seniors > 0) {
            labels.add("Senior");
        }
        if (row.pwd > 0) {
            labels.add("PWD");
        }
        if (row.pregnant > 0) {
            labels.add("pregnant");
        }
        if (row.infants > 0) {
            labels.add("infant");
        }
        return labels.isEmpty() ? "No special category" : String.join(", ", labels);
    }

    @Override String module() { return "Beneficiaries"; }
    @Override String crumb() { return "Beneficiaries"; }

    @Override
    void load() throws Exception {
        DisasterController controller = new DisasterController();
        disasters = controller.list(Session.workspace, Session.user);
        disaster = DisasterBand.resolve(disasters);
        if (disaster == null) {
            return;
        }
        stats = controller.stats(disaster);
        BeneficiaryController beneficiaries = new BeneficiaryController();
        counts = beneficiaries.tabCounts(disaster, Session.user);
        puroks = beneficiaries.puroks(disaster);
        households = beneficiaries.page(disaster, filter(), pageNumber, PAGE_SIZE, Session.user);
        pageNumber = households.page;
    }

    private BeneficiaryRepository.Filter filter() {
        BeneficiaryRepository.Filter filter = new BeneficiaryRepository.Filter();
        filter.tab = new String[] { "ALL", "PENDING", "ASSISTED", "VULNERABLE" }[tab];
        filter.text = search;
        filter.purok = purok.equals(ALL_PUROKS) ? null : purok;
        filter.damage = damage.equals(ALL_DAMAGE) ? null : damage;
        return filter;
    }

    @Override
    JComponent build() {
        VBox page = new VBox(20);
        page.put(UI.pageHeader("Beneficiaries", "Register affected households and keep their assistance needs in view.",
                disaster != null && disaster.isActive() ? UI.primary("Register household", () -> go(new RegisterHouseholdPage())) : null));
        if (disaster == null) {
            page.put(DisasterBand.empty(this, "Households are registered against an event."));
            return page;
        }
        page.put(DisasterBand.of(this, disaster, disasters));
        page.put(new Cols(18, 1, 1, 1, 1).stretch().put(
                UI.stat("Families", Fmt.n(stats.registered), "Unique households in this event", Tone.NEUTRAL),
                UI.stat("Individuals", Fmt.n(stats.individuals), "From family member records", Tone.NEUTRAL),
                UI.stat("Vulnerable individuals", Fmt.n(stats.vulnerablePeople), stats.seniors + " senior · " + stats.pwd
                        + " PWD · " + stats.pregnant + " pregnant · " + stats.infants + " infants", Tone.NEUTRAL),
                UI.stat("Pending assistance", Fmt.n(stats.pending()), stats.assisted + " households assisted", Tone.WARN)));
        Card card = new Card(14);
        VBox heading = new VBox(4);
        heading.put(UI.h2("Household records"), UI.muted("Status and prior assistance are specific to " + disaster.getName() + "."));
        card.put(heading, UI.tabs(Arrays.asList("All households", "Pending (" + counts[1] + ")", "Assisted (" + counts[2] + ")",
                "Vulnerable groups"), tab, index -> {
                    tab = index;
                    pageNumber = 1;
                    reload();
                }), filters(), tableHolder);
        fillTable();
        page.put(card);
        page.put(UI.alert(Tone.INFO, "Household IDs stay unique", "Review similar names and addresses before registration. "
                + "One household may have separate assistance records for different disasters."));
        return page;
    }

    private JComponent filters() {
        Input box = new Input(search).placeholder("Search household ID, head of household or address");
        box.getDocument().addDocumentListener(new SimpleListener(() -> {
            search = box.getText();
            pageNumber = 1;
            if (debounce != null) {
                debounce.stop();
            }
            debounce = new javax.swing.Timer(350, event -> refreshTable());
            debounce.setRepeats(false);
            debounce.start();
        }));
        List<String> purokOptions = new ArrayList<>();
        purokOptions.add(ALL_PUROKS);
        purokOptions.addAll(puroks);
        Dropdown<String> purokBox = new Dropdown<>(purokOptions, text -> text);
        purokBox.setSelected(purokOptions.contains(purok) ? purok : ALL_PUROKS);
        purokBox.onChange(() -> {
            purok = purokBox.getSelected();
            pageNumber = 1;
            refreshTable();
        });
        Dropdown<String> damageBox = Dropdown.of(ALL_DAMAGE, "Totally damaged", "Partially damaged");
        damageBox.setSelected(damage);
        damageBox.onChange(() -> {
            damage = damageBox.getSelected();
            pageNumber = 1;
            refreshTable();
        });
        Dropdown<String> statusBox = Dropdown.of(ALL_STATUS, "Pending", "Assisted");
        statusBox.setSelected(tab == 1 ? "Pending" : tab == 2 ? "Assisted" : ALL_STATUS);
        statusBox.onChange(() -> {
            String chosen = statusBox.getSelected();
            tab = chosen.equals("Pending") ? 1 : chosen.equals("Assisted") ? 2 : 0;
            pageNumber = 1;
            reload();
        });
        Field purokCell = new Field(null, purokBox);
        Field damageCell = new Field(null, damageBox);
        Field statusCell = new Field(null, statusBox);
        for (Field cell : new Field[] { purokCell, damageCell, statusCell }) {
            cell.setPreferredSize(new java.awt.Dimension(160, 44));
        }
        return new Cols(12, 1, 0, 0, 0).put(new Field(null, box), purokCell, damageCell, statusCell);
    }

    private void refreshTable() {
        Task.run(() -> new BeneficiaryController().page(disaster, filter(), pageNumber, PAGE_SIZE, Session.user), result -> {
            households = result;
            pageNumber = result.page;
            fillTable();
        }, error -> Dialogs.error(shell, error));
    }

    private void fillTable() {
        tableHolder.removeAll();
        DataTable table = new DataTable(new String[] { "Household / head", "Address", "Members / groups", "Damage / evacuation",
            "Needs", "Status" }, 1.5, 1.4, 1.5, 1.5, 1.4, 1);
        for (BeneficiaryRepository.Row row : households.rows) {
            List<String> needs = new ArrayList<>();
            for (String category : row.needCategories) {
                needs.add(needShort(category));
            }
            String needsText = needs.isEmpty() ? "—" : String.join(" · ", needs);
            table.rowClick(() -> go(new HouseholdDetailPage(row.householdId, disaster)),
                    new String[] { row.householdId, row.head }, new String[] { row.address, row.purok },
                    new String[] { row.members + " members", groups(row) },
                    new String[] { row.damage, row.evacuationStatus }, needsText,
                    UI.left(UI.pill(row.assisted ? "Assisted" : "Pending", row.assisted ? Tone.TEAL : Tone.WARN)));
        }
        if (households.rows.isEmpty()) {
            table.empty("No households match this view.");
        }
        tableHolder.put(table, UI.gap(12), UI.pager(households.page, households.pages(), "Showing " + households.rows.size()
                + " of " + households.total + " households · Page " + households.page + " of " + households.pages(),
                () -> {
                    pageNumber = households.page - 1;
                    refreshTable();
                }, () -> {
                    pageNumber = households.page + 1;
                    refreshTable();
                }));
        tableHolder.revalidate();
        tableHolder.repaint();
    }

    static String needShort(String category) {
        switch (category) {
            case "Hygiene kits":
                return "hygiene";
            case "Shelter materials":
                return "shelter";
            default:
                return category.toLowerCase();
        }
    }
}
