package GUI;

import GUI.Theme.Tone;
import LIB.AuditRepository;
import LIB.Audits;
import LIB.ReportTable;
import LIB.Time;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JComponent;

/** The read-only ledger of everything that changed, who did it, when and why. */
class AuditPage extends Page {
    private static final String ALL_MODULES = "All modules";
    private static final String ALL_ACTORS = "All actors";

    private String search;
    private String period = "Last 30 days";
    private String module = ALL_MODULES;
    private String actor = ALL_ACTORS;
    private List<Audits> rows = new ArrayList<>();
    private List<String> actors = new ArrayList<>();
    private Audits selected;
    private final VBox tableHolder = new VBox(0);
    private final VBox detailHolder = new VBox(0);
    private javax.swing.Timer debounce;

    AuditPage() {
        this("");
    }

    AuditPage(String search) {
        this.search = search == null ? "" : search;
    }

    @Override String module() { return "Audit Logs"; }
    @Override String crumb() { return "Audit Logs"; }

    private LocalDate from() {
        LocalDate today = Time.today();
        switch (period) {
            case "Today":
                return today;
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

    private AuditRepository.Filter filter() {
        AuditRepository.Filter filter = new AuditRepository.Filter();
        filter.barangayId = Session.workspace.getBarangayId();
        filter.includeGlobal = Session.isAdmin();
        filter.text = search;
        filter.from = from();
        filter.module = module.equals(ALL_MODULES) ? null : module;
        if (!Session.canManage()) {
            filter.actorAccountId = Session.user.getAccountId();
        }
        filter.limit = 300;
        return filter;
    }

    @Override
    void load() throws Exception {
        rows = new AuditRepository().find(filter());
        collectActors();
    }

    private void collectActors() {
        Set<String> names = new LinkedHashSet<>();
        for (Audits audit : rows) {
            if (!audit.getActorName().isEmpty()) {
                names.add(audit.getActorName());
            }
        }
        actors = new ArrayList<>(names);
    }

    @Override
    JComponent build() {
        Btn export = UI.secondary("Export audit log", this::export);
        export.setVisible(Session.user.canExportReports());
        VBox page = new VBox(20);
        page.put(UI.pageHeader("Audit logs", "Trace who changed a record, when it changed and why.", export));
        page.put(UI.alert(Tone.INFO, "Read only transaction history", "Posted records cannot be overwritten. Corrections create new "
                + "linked entries. Blocked attempts are logged without changing stock."));
        if (!Session.canManage()) {
            page.put(UI.alert(Tone.NEUTRAL, "Your own activity", "Encoders see the entries they recorded. Barangay Officials and "
                    + "Admins see the full ledger."));
        }
        Card card = new Card(14);
        VBox heading = new VBox(4);
        heading.put(UI.h2("Activity ledger"), UI.muted("All timestamps in Philippine Standard Time · Asia/Manila"));
        card.put(heading, filters(), tableHolder);
        page.put(card, detailHolder);
        fillTable();
        return page;
    }

    private JComponent filters() {
        Input box = new Input(search).placeholder("Search reference, actor or reason");
        box.getDocument().addDocumentListener(new SimpleListener(() -> {
            search = box.getText();
            if (debounce != null) {
                debounce.stop();
            }
            debounce = new javax.swing.Timer(350, event -> refreshRows());
            debounce.setRepeats(false);
            debounce.start();
        }));
        Dropdown<String> periodBox = Dropdown.of("Today", "Last 7 days", "Last 30 days", "Last 90 days", "All time");
        periodBox.setSelected(period);
        periodBox.onChange(() -> {
            period = periodBox.getSelected();
            refreshRows();
        });
        Dropdown<String> moduleBox = Dropdown.of(ALL_MODULES, AuditRepository.MODULE_INVENTORY, AuditRepository.MODULE_DISASTERS,
                AuditRepository.MODULE_BENEFICIARIES, AuditRepository.MODULE_DONATIONS, AuditRepository.MODULE_DISTRIBUTION,
                AuditRepository.MODULE_USERS);
        moduleBox.setSelected(module);
        moduleBox.onChange(() -> {
            module = moduleBox.getSelected();
            refreshRows();
        });
        List<String> actorOptions = new ArrayList<>();
        actorOptions.add(ALL_ACTORS);
        actorOptions.addAll(actors);
        Dropdown<String> actorBox = new Dropdown<>(actorOptions, text -> text);
        actorBox.setSelected(actorOptions.contains(actor) ? actor : ALL_ACTORS);
        actorBox.onChange(() -> {
            actor = actorBox.getSelected();
            fillTable();
        });
        Field[] cells = { new Field(null, periodBox), new Field(null, moduleBox), new Field(null, actorBox) };
        for (Field cell : cells) {
            cell.setPreferredSize(new java.awt.Dimension(170, 44));
        }
        return new Cols(12, 1, 0, 0, 0).put(new Field(null, box), cells[0], cells[1], cells[2]);
    }

    private void refreshRows() {
        Task.run(() -> new AuditRepository().find(filter()), result -> {
            rows = result;
            collectActors();
            selected = null;
            fillTable();
        }, error -> Dialogs.error(shell, error));
    }

    private List<Audits> visible() {
        List<Audits> out = new ArrayList<>();
        for (Audits audit : rows) {
            if (actor.equals(ALL_ACTORS) || actor.equals(audit.getActorName())) {
                out.add(audit);
            }
        }
        return out;
    }

    private void fillTable() {
        tableHolder.removeAll();
        DataTable table = new DataTable(new String[] { "Date / time", "Reference / action", "Actor / role", "Before → after",
            "Reason / result" }, 1.1, 1.7, 1.2, 1.6, 1.7);
        List<Audits> shown = visible();
        for (Audits audit : shown) {
            String reason = audit.getReason() == null || audit.getReason().isEmpty() ? audit.getResult() : audit.getReason()
                    + (audit.getResult().isEmpty() ? "" : "\n" + audit.getResult());
            String change = audit.getBeforeValue() == null && audit.getAfterValue() == null ? Fmt.pretty(audit.getMovement())
                    : Fmt.beforeAfter(audit.getBeforeValue(), audit.getAfterValue());
            table.rowClick(() -> {
                selected = audit;
                fillDetail();
            }, new String[] { Time.stamp(audit.getWhen()) }, new String[] { audit.getReference() == null ? "—" : audit.getReference(),
                actionText(audit) },
                    new String[] { audit.getActorName().isEmpty() ? "—" : audit.getActorName(), audit.getActorRole() },
                    change.isEmpty() ? "—" : change.split("\n"), reason.split("\n"));
        }
        if (shown.isEmpty()) {
            table.empty("No activity matches these filters.");
        }
        table.footer("Showing " + shown.size() + " recent events · Select a reference for full transaction detail.");
        tableHolder.put(table);
        tableHolder.revalidate();
        tableHolder.repaint();
        fillDetail();
    }

    private static String actionText(Audits audit) {
        String label = AuditRepository.label(audit.getAction());
        String summary = audit.getSummary();
        if (summary.isEmpty()) {
            return label;
        }
        return summary.toLowerCase().startsWith(label.toLowerCase()) ? summary : label + " · " + summary;
    }

    private void fillDetail() {
        detailHolder.removeAll();
        if (selected != null) {
            Audits audit = selected;
            Card card = new Card(12);
            card.put(UI.h2("Selected event · " + (audit.getReference() == null ? "—" : audit.getReference())),
                    UI.muted(AuditRepository.label(audit.getAction()) + " · " + audit.getModule()),
                    UI.fact("Summary", audit.getSummary().isEmpty() ? "—" : audit.getSummary()),
                    UI.fact("Stock movement", audit.getMovement().isEmpty() ? "No stock movement" : Fmt.pretty(audit.getMovement())),
                    UI.fact("Before → after", Fmt.beforeAfter(audit.getBeforeValue(), audit.getAfterValue()).isEmpty() ? "—"
                            : Fmt.beforeAfter(audit.getBeforeValue(), audit.getAfterValue()).replace("\n", " · ")),
                    UI.fact("Recorded by", audit.getActorName() + (audit.getActorRole().isEmpty() ? "" : " · " + audit.getActorRole())),
                    UI.fact("Timestamp", Time.longStamp(audit.getWhen())),
                    UI.fact("Reason", audit.getReason() == null || audit.getReason().isEmpty() ? "—" : audit.getReason()),
                    UI.fact("Result", audit.getResult().isEmpty() ? "—" : audit.getResult()));
            detailHolder.put(card);
        }
        detailHolder.revalidate();
        detailHolder.repaint();
    }

    private void export() {
        ReportTable table = new ReportTable("Audit log", "Date / time", "Reference", "Action", "Summary", "Actor", "Role",
                "Stock movement", "Before", "After", "Reason", "Result");
        for (Audits audit : visible()) {
            table.row(Time.stamp(audit.getWhen()), audit.getReference(), AuditRepository.label(audit.getAction()), audit.getSummary(),
                    audit.getActorName(), audit.getActorRole(), Fmt.pretty(audit.getMovement()),
                    audit.getBeforeValue() == null ? "" : audit.getBeforeValue().replace("\n", "; "),
                    audit.getAfterValue() == null ? "" : audit.getAfterValue().replace("\n", "; "),
                    audit.getReason(), audit.getResult());
        }
        table.summary("Events", String.valueOf(table.rows.size()));
        table.note = "Read-only ledger exported from BarangayCare.";
        Exports.save(shell, table, true, "Period: " + period, "Module: " + module);
    }
}
