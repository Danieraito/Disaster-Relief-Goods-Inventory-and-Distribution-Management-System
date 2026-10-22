package GUI;

import GUI.Theme.Tone;
import LIB.AuditRepository;
import LIB.Audits;
import LIB.Beneficiaries;
import LIB.BeneficiaryController;
import LIB.Disaster;
import LIB.DisasterController;
import LIB.DistributionController;
import LIB.DistributionRepository;
import LIB.FamilyMember;
import LIB.Inventory;
import LIB.InventoryController;
import LIB.Time;
import LIB.Units;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;

/** One household: profile, needs, family, assistance history and record changes. */
class HouseholdDetailPage extends Page {
    private static final String[][] NEEDS = { { "Food", "Family food pack", "pack" }, { "Water", "Drinking water", "liter" },
        { "Hygiene kits", "Hygiene kit", "kit" }, { "Clothing", "Clothing bundle", "bundle" }, { "Medicine", "Medicine", "box" },
        { "Shelter materials", "Shelter materials", "piece" } };

    private final String householdId;
    private final String flash;
    private Disaster disaster;
    private List<Disaster> disasters = new ArrayList<>();
    private Beneficiaries household;
    private final Map<String, String> units = new HashMap<>();
    private List<DistributionRepository.Row> releases = new ArrayList<>();
    private List<Audits> changes = new ArrayList<>();
    private int tab;
    private String message;

    HouseholdDetailPage(String householdId, Disaster disaster) {
        this(householdId, disaster, null);
    }

    HouseholdDetailPage(String householdId, Disaster disaster, String flash) {
        this.householdId = householdId;
        this.disaster = disaster;
        this.flash = flash;
        this.message = flash;
    }

    @Override String module() { return "Beneficiaries"; }
    @Override String crumb() { return "Beneficiaries / Household detail"; }

    @Override
    void load() throws Exception {
        household = new BeneficiaryController().find(householdId, disaster, Session.user);
        if (household == null) {
            throw new IllegalStateException("This household no longer exists.");
        }
        disaster = household.getDisaster();
        Session.disasterId = disaster.getDisasterId();
        disasters = new DisasterController().list(Session.workspace, Session.user);
        units.clear();
        for (Inventory item : new InventoryController().list(Session.workspace, false, Session.user)) {
            units.putIfAbsent(item.getCategory(), item.getUnit());
        }
        DistributionRepository.Filter filter = new DistributionRepository.Filter();
        filter.householdId = householdId;
        filter.limit = 50;
        releases = new DistributionController().list(Session.workspace, filter, Session.user);
        changes = new AuditRepository().findFor("household_id", householdId, null);
    }

    private String unit(String category, String fallback) {
        return units.getOrDefault(category, fallback);
    }

    @Override
    JComponent build() {
        VBox page = new VBox(20);
        Btn prepare = UI.primary("Prepare distribution", () -> go(new DistributionFormPage(householdId)));
        prepare.setVisible(disaster.isActive());
        page.put(UI.pageHeader(household.getName() + " household", household.getBeneficiaryId() + " · "
                + household.getHouseholdSize() + " family members · " + (household.getPurok().isEmpty() ? "No purok"
                : household.getPurok()), prepare));
        page.put(DisasterBand.of(this, disaster, disasters));
        if (message != null) {
            page.put(UI.alert(Tone.TEAL, flash != null && message == flash ? "Household registered · unique ID assigned" : "Saved",
                    flash != null && message == flash ? message.replaceFirst("^Household registered · unique ID assigned\\. ", "") : message));
        }
        page.put(UI.tabs(Arrays.asList("Household & needs", "Family members", "Assistance history", "Record changes"), tab,
                index -> {
                    tab = index;
                    message = null;
                    rebuild();
                }));
        if (tab == 0) {
            page.put(new Cols(22, 1, 1).put(profileCard(), needsCard()));
            page.put(membersCard());
            page.put(updateCard());
        } else if (tab == 1) {
            page.put(membersCard());
        } else if (tab == 2) {
            page.put(historyCard());
        } else {
            page.put(changesCard());
        }
        return page;
    }

    private JComponent profileCard() {
        Card card = new Card(12);
        Cols title = new Cols(12, 1, 0);
        title.put(UI.h2("Household profile"), UI.pill(household.isAssisted() ? "Assisted" : "Pending assistance",
                household.isAssisted() ? Tone.TEAL : Tone.WARN));
        card.put(title, UI.fact("Household head", household.getName()),
                UI.fact("Address", household.getAddress() + (household.getPurok().isEmpty() ? "" : " · " + household.getPurok())),
                UI.fact("Contact", household.getContactNumber().isEmpty() ? "—" : household.getContactNumber()),
                UI.fact("House damage", household.getDamageLevel() == null ? "—" : household.getDamageLevel()),
                UI.fact("Evacuation", household.getEvacuationStatus() + (household.getEvacuationLocation().isEmpty() ? ""
                        : " · " + household.getEvacuationLocation())));
        if (!household.getAssessmentNote().isEmpty()) {
            card.put(UI.fact("Assessment", household.getAssessmentNote()));
        }
        return card;
    }

    private JComponent needsCard() {
        DataTable table = new DataTable(new String[] { "Item", "Requested", "Released", "Remaining" }, 1.6, 1, 1, 1);
        int rows = 0;
        for (String[] need : NEEDS) {
            Integer requested = household.getNeeds().get(need[0]);
            if (requested == null) {
                continue;
            }
            rows++;
            int released = household.getReleased().getOrDefault(need[0], 0);
            String unit = unit(need[0], need[2]);
            table.row(need[1], Units.quantity(requested, unit), String.valueOf(released),
                    Units.quantity(Math.max(0, requested - released), unit));
        }
        if (rows == 0) {
            table.empty("No assistance items were requested.");
        }
        Card card = new Card(14);
        card.put(UI.h2("Assistance needs"), table, UI.smallPara(household.getReleased().isEmpty()
                ? "No prior assistance is recorded for this household in " + disaster.getName() + "."
                : "Released quantities come from confirmed household releases in " + disaster.getName() + "."));
        return card;
    }

    private JComponent membersCard() {
        DataTable table = new DataTable(new String[] { "Name", "Age", "Sex", "Special category" }, 1.8, 1, 1, 1.6);
        for (FamilyMember member : household.getFamilyMembers()) {
            boolean head = household.getHouseholdHead() != null
                    && household.getHouseholdHead().getMemberId().equals(member.getMemberId());
            table.row(head ? new String[] { member.getFullName(), "Household head" } : new String[] { member.getFullName() },
                    member.ageLabel(), member.getSex().isEmpty() ? "—" : member.getSex(), member.categoryLabel());
        }
        Card card = new Card(14);
        VBox heading = new VBox(4);
        heading.put(UI.h2("Family members"), UI.muted(household.getHouseholdSize() + " individuals · vulnerable labels are shown in full."));
        card.put(heading, table);
        return card;
    }

    private JComponent updateCard() {
        Card card = new Card(14);
        VBox heading = new VBox(4);
        heading.put(UI.h2("Review / update household"), UI.muted("Changing a profile does not erase assistance history."));
        card.put(heading);
        if (!disaster.isActive()) {
            card.put(UI.mutedPara("This disaster is closed, so the household profile is read only."));
            return card;
        }
        Input contact = new Input(household.getContactNumber());
        Dropdown<String> damage = Dropdown.of(Beneficiaries.DAMAGE_PARTIAL, Beneficiaries.DAMAGE_TOTAL);
        damage.setSelected(household.getDamageLevel());
        Dropdown<String> evacuation = Dropdown.of("Evacuated", "At home");
        evacuation.setSelected(household.getEvacuationStatus().isEmpty() ? "At home" : household.getEvacuationStatus());
        Area reason = new Area("", 2);
        VBox error = new VBox(0);
        Btn save = UI.primary("Save household update");
        save.addActionListener(event -> {
            save.setEnabled(false);
            error.removeAll();
            Task.run(() -> new BeneficiaryController().updateHousehold(household, contact.getText(), damage.getSelected(),
                    evacuation.getSelected(), reason.getText(), Session.user), () -> {
                        message = "Household updated · the change was logged with your name, date and time.";
                        reload();
                    }, failure -> {
                        save.setEnabled(true);
                        error.put(UI.alert(Tone.DANGER, "The update was not saved", Task.message(failure)));
                        error.revalidate();
                        error.repaint();
                    });
        });
        card.put(new Cols(14, 1, 1, 1).put(new Field("Contact number", contact), new Field("House damage", damage),
                new Field("Evacuation status", evacuation)), new Field("Reason for update *", reason), error,
                new Cols(14, 1, 0, 0).put(UI.smallPara("Changes are recorded with your name, date and time."),
                        UI.secondary("Discard changes", this::reload), save));
        return card;
    }

    private JComponent historyCard() {
        DataTable table = new DataTable(new String[] { "Release / time", "Recipient", "Items released", "Releasing officer",
            "Status" }, 1.3, 1.5, 1.8, 1.2, 1);
        for (DistributionRepository.Row row : releases) {
            table.rowClick(() -> go(new DistributionDetailPage(row.distributionId)),
                    new String[] { row.distributionId, Time.stamp(row.recordedAt) }, row.recipient(),
                    String.join("\n", row.goods).split("\n"), row.officer.isEmpty() ? "—" : row.officer,
                    UI.left(DistributionPage.statusPill(row.status)));
        }
        if (releases.isEmpty()) {
            table.empty("No assistance has been released to this household in " + disaster.getName() + " yet.");
        }
        return UI.section("Assistance history", "Releases made to this household for " + disaster.getName() + ".", table);
    }

    private JComponent changesCard() {
        DataTable table = new DataTable(new String[] { "Date / time", "Change", "Before → after", "Reason", "Recorded by" },
                1.2, 1.4, 1.6, 1.5, 1.2);
        for (Audits audit : changes) {
            table.row(Time.stamp(audit.getWhen()), AuditRepository.label(audit.getAction()),
                    Fmt.beforeAfter(audit.getBeforeValue(), audit.getAfterValue()).split("\n"),
                    audit.getReason() == null ? "—" : audit.getReason(), audit.getActorName());
        }
        if (changes.isEmpty()) {
            table.empty("No record changes yet.");
        }
        return UI.section("Record changes", "Every registration and update is kept in the audit log.", table);
    }
}
