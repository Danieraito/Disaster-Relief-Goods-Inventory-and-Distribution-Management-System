package GUI;

import GUI.Theme.Tone;
import LIB.Disaster;
import LIB.DisasterController;
import LIB.Time;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;

/** All disaster events of the workspace, active and closed. */
class DisastersPage extends Page {
    private static final String ALL_TYPES = "All types";
    private static final String ALL_SEVERITY = "All severity";

    private int tab;
    private String search = "";
    private String type = ALL_TYPES;
    private String severity = ALL_SEVERITY;
    private List<Disaster> disasters = new ArrayList<>();
    private Map<String, int[]> summary = new HashMap<>();
    private final VBox tableHolder = new VBox(0);

    @Override String module() { return "Disasters"; }
    @Override String crumb() { return "Disasters"; }

    @Override
    void load() throws Exception {
        DisasterController controller = new DisasterController();
        disasters = controller.list(Session.workspace, Session.user);
        summary = controller.summaries(Session.workspace);
    }

    private int[] counts(Disaster disaster) {
        int[] found = summary.get(disaster.getDisasterId());
        return found == null ? new int[] { 0, 0, 0 } : found;
    }

    private List<Disaster> byStatus(boolean active) {
        List<Disaster> out = new ArrayList<>();
        for (Disaster disaster : disasters) {
            if (disaster.isActive() == active) {
                out.add(disaster);
            }
        }
        return out;
    }

    @Override
    JComponent build() {
        List<Disaster> active = byStatus(true);
        List<Disaster> closed = byStatus(false);
        VBox page = new VBox(20);
        page.put(UI.pageHeader("Disasters", "Coordinate relief needs and assistance for each event.",
                UI.primary("Add disaster", () -> go(new DisasterFormPage()))));
        List<JComponent> stats = new ArrayList<>();
        stats.add(UI.stat("Active events", String.valueOf(active.size()), "Open for beneficiary registration", Tone.NEUTRAL));
        if (active.isEmpty()) {
            int families = 0;
            int assisted = 0;
            for (int[] numbers : summary.values()) {
                families += numbers[0];
                assisted += numbers[1];
            }
            stats.add(UI.stat("Closed events", String.valueOf(closed.size()), "Response completed", Tone.NEUTRAL));
            stats.add(UI.stat("Households recorded", Fmt.n(families), "Across all events", Tone.NEUTRAL));
            stats.add(UI.stat("Households assisted", Fmt.n(assisted), "Across all events", Tone.TEAL));
        } else {
            Disaster first = active.get(0);
            int[] firstCounts = counts(first);
            stats.add(UI.stat(first.getName() + " households", Fmt.n(firstCounts[0]), Fmt.n(firstCounts[2]) + " individuals",
                    Tone.NEUTRAL));
            stats.add(UI.stat(first.getName() + " assisted", Fmt.n(firstCounts[1]), (firstCounts[0] - firstCounts[1])
                    + " households pending", Tone.TEAL));
            if (active.size() > 1) {
                Disaster second = active.get(1);
                int[] secondCounts = counts(second);
                stats.add(UI.stat(second.getName() + " households", Fmt.n(secondCounts[0]), secondCounts[1] + " assisted · "
                        + (secondCounts[0] - secondCounts[1]) + " pending", Tone.NEUTRAL));
            } else {
                stats.add(UI.stat("Closed events", String.valueOf(closed.size()), "Response completed", Tone.NEUTRAL));
            }
        }
        page.put(new Cols(18, 1, 1, 1, 1).stretch().put(stats.toArray(new JComponent[0])));
        page.put(UI.alert(Tone.INFO, "Keep assistance tied to the correct disaster", "Households and distribution records "
                + "are linked to an event. Previous assistance is checked within that event, not across unrelated disasters."));

        Card events = new Card(14);
        VBox heading = new VBox(4);
        heading.put(UI.h2("Disaster events"), UI.muted("Select an event to review household needs, area coverage and stock requirements."));
        events.put(heading, UI.tabs(Arrays.asList("Active (" + active.size() + ")", "Closed (" + closed.size() + ")",
                "All events"), tab, index -> {
                    tab = index;
                    rebuild();
                }), filters(), tableHolder);
        refreshTable();
        page.put(events);
        if (tab != 1 && !closed.isEmpty()) {
            DataTable table = new DataTable(new String[] { "Disaster", "Type / date", "Area", "Response outcome", "Status" },
                    1.6, 1.3, 1.3, 1.6, 1);
            int shown = 0;
            for (Disaster disaster : closed) {
                if (shown++ >= 5) {
                    break;
                }
                int[] numbers = counts(disaster);
                table.rowClick(() -> go(new DisasterDetailPage(disaster.getDisasterId())),
                        new String[] { disaster.getName(), disaster.getDisasterId() },
                        new String[] { disaster.getType(), Time.date(LocalDate.parse(disaster.getDate())) },
                        disaster.getAffectedAreas(), new String[] { numbers[1] + " / " + numbers[0] + " households", "assisted" },
                        UI.left(UI.pill("Closed", Tone.NEUTRAL)));
            }
            page.put(UI.section("Recently closed events", null, table));
        }
        return page;
    }

    private JComponent filters() {
        Input box = new Input(search).placeholder("Search disaster name, ID or affected area");
        box.getDocument().addDocumentListener(new SimpleListener(() -> {
            search = box.getText();
            refreshTable();
        }));
        Dropdown<String> typeBox = Dropdown.of(ALL_TYPES, "Typhoon", "Flood", "Earthquake", "Fire", "Landslide", "Other");
        typeBox.setSelected(type);
        typeBox.onChange(() -> {
            type = typeBox.getSelected();
            refreshTable();
        });
        Dropdown<String> severityBox = Dropdown.of(ALL_SEVERITY, "Low", "Moderate", "High", "Critical");
        severityBox.setSelected(severity);
        severityBox.onChange(() -> {
            severity = severityBox.getSelected();
            refreshTable();
        });
        Field typeCell = new Field(null, typeBox);
        Field severityCell = new Field(null, severityBox);
        typeCell.setPreferredSize(new java.awt.Dimension(170, 44));
        severityCell.setPreferredSize(new java.awt.Dimension(170, 44));
        return new Cols(12, 1, 0, 0).put(new Field(null, box), typeCell, severityCell);
    }

    private void refreshTable() {
        tableHolder.removeAll();
        String needle = search.trim().toLowerCase();
        DataTable table = new DataTable(new String[] { "Disaster", "Type / date", "Affected areas", "Severity",
            "Families / progress", "Status" }, 1.8, 1.3, 1.6, 1, 1.5, 1);
        int rows = 0;
        for (Disaster disaster : disasters) {
            boolean inTab = tab == 2 || tab == 0 && disaster.isActive() || tab == 1 && !disaster.isActive();
            boolean matches = needle.isEmpty() || disaster.getName().toLowerCase().contains(needle)
                    || disaster.getDisasterId().toLowerCase().contains(needle)
                    || (disaster.getAffectedAreas() != null && disaster.getAffectedAreas().toLowerCase().contains(needle));
            if (!inTab || !matches || !type.equals(ALL_TYPES) && !type.equals(disaster.getType())
                    || !severity.equals(ALL_SEVERITY) && !severity.equals(disaster.getSeverity())) {
                continue;
            }
            rows++;
            int[] numbers = counts(disaster);
            table.rowClick(() -> go(new DisasterDetailPage(disaster.getDisasterId())),
                    new String[] { disaster.getName(), disaster.getDisasterId() },
                    new String[] { disaster.getType(), Time.date(LocalDate.parse(disaster.getDate())) },
                    disaster.getAffectedAreas(), UI.left(UI.pill(disaster.getSeverity(), DashboardPage.severityTone(disaster.getSeverity()))),
                    numbers[0] + " · " + numbers[1] + " assisted",
                    UI.left(UI.pill(disaster.isActive() ? "Active" : "Closed", disaster.isActive() ? Tone.TEAL : Tone.NEUTRAL)));
        }
        if (rows == 0) {
            table.empty("No disaster events match this view.");
        }
        table.footer(disasters.size() + " events recorded · Household counts are event specific and may include the "
                + "same family in different disasters.");
        tableHolder.put(table);
        tableHolder.revalidate();
        tableHolder.repaint();
    }
}
