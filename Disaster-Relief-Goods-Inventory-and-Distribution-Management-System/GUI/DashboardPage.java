package GUI;

import GUI.Theme.Tone;
import LIB.AuditRepository;
import LIB.Audits;
import LIB.Disaster;
import LIB.DisasterController;
import LIB.DisasterStats;
import LIB.Inventory;
import LIB.InventoryController;
import LIB.Time;
import LIB.Units;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JLabel;

class DashboardPage extends Page {
    private List<Inventory> items;
    private List<Disaster> active = new ArrayList<>();
    private Disaster featured;
    private DisasterStats stats;
    private List<Audits> recent = new ArrayList<>();

    @Override String module() { return "Dashboard"; }
    @Override String crumb() { return "Dashboard"; }

    @Override
    void load() throws Exception {
        items = new InventoryController().list(Session.workspace, false, Session.user);
        DisasterController disasters = new DisasterController();
        active = new ArrayList<>();
        for (Disaster disaster : disasters.list(Session.workspace, Session.user)) {
            if (disaster.isActive()) {
                active.add(disaster);
            }
        }
        featured = active.isEmpty() ? null : active.get(0);
        stats = featured == null ? null : disasters.stats(featured);
        AuditRepository.Filter filter = new AuditRepository.Filter();
        filter.barangayId = Session.workspace.getBarangayId();
        filter.modules = Arrays.asList(AuditRepository.MODULE_INVENTORY, AuditRepository.MODULE_DONATIONS,
                AuditRepository.MODULE_DISTRIBUTION, AuditRepository.MODULE_BENEFICIARIES);
        filter.limit = 60;
        Set<String> wanted = new LinkedHashSet<>(Arrays.asList("DISTRIBUTION_CONFIRMED", "STOCK_ADJUSTED",
                "DONATION_POSTED", "HOUSEHOLD_REGISTERED", "ITEM_CREATED", "ITEM_EDITED"));
        recent = new ArrayList<>();
        for (Audits audit : new AuditRepository().find(filter)) {
            if (wanted.contains(audit.getAction()) && recent.size() < 5) {
                recent.add(audit);
            }
        }
    }

    @Override
    JComponent build() {
        List<StockAlerts.Notice> notices = StockAlerts.of(items);
        int recorded = 0;
        for (Inventory item : items) {
            recorded += item.getQuantity();
        }
        VBox page = new VBox(20);
        page.put(UI.pageHeader("Response dashboard", "Your barangay's relief operations, at a glance.",
                UI.primary("New distribution", () -> go(new DistributionFormPage()))));
        page.put(banner(notices));
        page.put(new Cols(18, 1, 1, 1, 1).stretch().put(
                UI.stat("Total stock", Fmt.n(recorded), "Recorded units · " + items.size() + " item records", Tone.NEUTRAL),
                UI.stat("Active disasters", String.valueOf(active.size()), activeNames(), Tone.NEUTRAL),
                UI.stat("Families served", stats == null ? "—" : stats.assisted + " / " + stats.registered,
                        stats == null ? "No active disaster" : featured.getName() + " · " + stats.percentAssisted()
                        + "% assisted", Tone.TEAL),
                UI.stat("Attention required", String.valueOf(notices.size()), StockAlerts.count(notices, "LOW")
                        + " low stock · " + StockAlerts.count(notices, "EXPIRING") + " expiring · "
                        + StockAlerts.count(notices, "EXPIRED") + " expired", Tone.WARN)));
        page.put(new Cols(22, 1, 1).put(activeResponse(), alertsCard(notices)));
        page.put(recentCard());
        return page;
    }

    private JComponent banner(List<StockAlerts.Notice> notices) {
        Set<String> attention = new LinkedHashSet<>();
        List<String> lines = new ArrayList<>();
        java.time.LocalDate today = Time.today();
        for (Inventory item : items) {
            if (item.isLowStock(today)) {
                attention.add(item.getItemId());
                lines.add(item.getItemName() + " is below threshold.");
            }
            if (item.hasExpiredStock(today)) {
                attention.add(item.getItemId());
                lines.add(Units.quantity(item.getExpiredQuantity(today), item.getUnit()) + " of " + item.getItemName()
                        + " have expired and are unavailable for release.");
            }
        }
        String greeting = Fmt.greeting() + ", " + Fmt.firstName(Session.user.getName()) + ". ";
        if (attention.isEmpty()) {
            return UI.alert(Tone.TEAL, greeting + "Stock looks healthy.",
                    "Every item is above its threshold and nothing has expired.");
        }
        return UI.alert(Tone.WARN, greeting + attention.size() + (attention.size() == 1 ? " stock item needs"
                : " stock items need") + " attention.", String.join(" ", lines),
                UI.secondary("Review alerts", () -> go(new InventoryPage(1))));
    }

    private String activeNames() {
        if (active.isEmpty()) {
            return "No event is open";
        }
        List<String> names = new ArrayList<>();
        for (Disaster disaster : active) {
            names.add(disaster.getName());
        }
        return String.join(" · ", names);
    }

    private JComponent activeResponse() {
        if (featured == null) {
            return UI.section("Active response", "No disaster event is open for registration.",
                    UI.mutedPara("Open an event to register affected households and plan releases."),
                    UI.left(UI.primary("Add disaster", () -> go(new DisasterFormPage()))));
        }
        Cols title = new Cols(12, 1, 0);
        VBox heading = new VBox(4);
        heading.put(UI.h2("Active response"), UI.muted(featured.getName() + " · " + featured.getDisasterId()));
        title.put(heading, UI.pill(featured.getSeverity() + " severity", severityTone(featured.getSeverity())));
        Card card = new Card(14);
        card.put(title);
        card.put(UI.muted(Time.date(java.time.LocalDate.parse(featured.getDate())) + " · " + featured.getAffectedAreas()
                + " · " + Fmt.n(stats.individuals) + " affected individuals"));
        card.put(new Cols(10, 1, 0).put(UI.strong("Households assisted"),
                UI.muted(stats.assisted + " / " + stats.registered)));
        card.put(new Bar(stats.registered == 0 ? 0 : stats.assisted / (double) stats.registered));
        card.put(new Cols(20, 1, 1).put(big(String.valueOf(stats.pending()), "Families awaiting assistance"),
                big(String.valueOf(stats.vulnerablePeople), "People in vulnerable groups")));
        DataTable table = new DataTable(new String[] { "Area", "Families", "Assisted", "Pending" }, 1.4, 1, 1, 1);
        for (DisasterStats.AreaRow area : stats.areas) {
            table.row(area.area, String.valueOf(area.registered), String.valueOf(area.assisted),
                    String.valueOf(area.pending()));
        }
        if (stats.areas.isEmpty()) {
            table.empty("No households registered yet.");
        }
        card.put(table);
        card.put(UI.left(UI.secondary("View disaster response", () -> go(new DisasterDetailPage(featured.getDisasterId())))));
        return card;
    }

    private JComponent big(String number, String caption) {
        VBox box = new VBox(4);
        box.put(UI.label(number, Theme.semibold(28), Theme.TEXT), UI.muted(caption));
        return box;
    }

    static Tone severityTone(String severity) {
        if ("Critical".equals(severity)) {
            return Tone.DANGER;
        }
        return "High".equals(severity) || "Moderate".equals(severity) ? Tone.WARN : Tone.NEUTRAL;
    }

    private JComponent alertsCard(List<StockAlerts.Notice> notices) {
        Card card = new Card(12);
        VBox heading = new VBox(4);
        heading.put(UI.h2("Stock & expiry alerts"), UI.muted("Prioritize replenishment and safe stock handling."));
        card.put(heading);
        if (notices.isEmpty()) {
            card.put(UI.alert(Tone.TEAL, "No alerts", "Stock levels and expiry dates are all within limits."));
        }
        for (StockAlerts.Notice notice : notices) {
            card.put(UI.alert(notice.tone, notice.title, notice.message));
        }
        return card;
    }

    private JComponent recentCard() {
        DataTable table = new DataTable(new String[] { "Transaction", "Activity", "Stock movement", "Recorded by",
            "Date & time" }, 1.15, 1.5, 1.7, 1.15, 1.1);
        for (Audits audit : recent) {
            table.row(audit.getReference(), audit.getSummary(), Fmt.pretty(audit.getMovement()), audit.getActorName(),
                    Time.stamp(audit.getWhen()));
        }
        if (recent.isEmpty()) {
            table.empty("No transactions yet. Stock movements appear here as they are recorded.");
        }
        VBox heading = new VBox(4);
        heading.put(UI.h2("Recent transactions"), UI.muted("All stock movements include an officer and a timestamp."));
        Card card = new Card(14);
        card.put(UI.titleRow(heading, UI.secondary("View audit logs", () -> go(new AuditPage()))), table);
        return card;
    }
}
