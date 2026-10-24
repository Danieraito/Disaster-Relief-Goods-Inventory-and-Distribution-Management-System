package GUI;

import GUI.Theme.Tone;
import LIB.Beneficiaries;
import LIB.BeneficiaryController;
import LIB.BeneficiaryRepository;
import LIB.Disaster;
import LIB.DisasterController;
import LIB.FamilyMember;
import LIB.Time;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JComponent;

/** Register a household: family record, damage and needs, then verify and save. */
class RegisterHouseholdPage extends Page {
    private static final String[] STEPS = { "Household information", "Family members", "Damage & needs", "Verify & save" };
    private static final String[][] NEEDS = { { "Family food pack", "Food", "pack" }, { "Drinking water", "Water", "liter" },
        { "Hygiene kit", "Hygiene kits", "kit" }, { "Clothing bundle", "Clothing", "bundle" },
        { "Medicine", "Medicine", "box" }, { "Shelter materials", "Shelter materials", "piece" } };
    private static final int[] DEFAULT_NEEDS = { 2, 12, 1, 0, 0, 0 };

    private static final class Draft {
        String name;
        int years;
        Integer months;
        String sex;
        Set<String> categories = new LinkedHashSet<>();

        String age() {
            return months != null ? months + (months == 1 ? " month" : " months") : years + (years == 1 ? " year" : " years");
        }

        String categoryText() {
            return categories.isEmpty() ? "None" : String.join(", ", categories);
        }
    }

    private List<Disaster> disasters = new ArrayList<>();
    private Disaster disaster;
    private final List<Draft> members = new ArrayList<>();
    private int editing = -1;
    private boolean autoCount = true;
    private boolean settingCount;
    private String duplicateBlock;
    private String duplicateWarn;

    private Input head;
    private Input contact;
    private Input count;
    private Input address;
    private Dropdown<String> purok;
    private Check homeless;
    private Input memberName;
    private Input memberAge;
    private Dropdown<String> memberSex;
    private MultiSelect memberCategories;
    private Dropdown<String> damage;
    private Dropdown<String> evacuation;
    private Input evacuationLocation;
    private Area note;
    private final Check[] needChecks = new Check[NEEDS.length];
    private final Input[] needQuantities = new Input[NEEDS.length];
    private Check verified;
    private final VBox stepperHolder = new VBox(0);
    private final VBox membersHolder = new VBox(0);
    private final VBox summaryHolder = new VBox(14);
    private final VBox memberError = new VBox(0);
    private final VBox saveError = new VBox(0);
    private Wrap vulnerableNote;
    private Btn confirm;

    @Override String module() { return "Beneficiaries"; }
    @Override String crumb() { return "Beneficiaries / Registration"; }

    @Override
    void load() throws Exception {
        disasters = new DisasterController().list(Session.workspace, Session.user);
        disaster = DisasterBand.resolve(disasters);
    }

    @Override
    JComponent build() {
        VBox page = new VBox(20);
        page.put(UI.pageHeader("Register household", "Complete the family record, assess needs, then verify before saving.", null));
        if (disaster == null) {
            page.put(DisasterBand.empty(this, "Households can only be registered for an open event."));
            return page;
        }
        page.put(DisasterBand.of(this, disaster, disasters));
        if (!disaster.isActive()) {
            page.put(UI.alert(Tone.WARN, "This disaster is closed", "Choose an active disaster to register households.",
                    UI.secondary("Change disaster", () -> DisasterBand.choose(this, disasters))));
            return page;
        }
        createInputs();
        page.put(stepperHolder, householdCard(), membersCard(), new Cols(22, 1, 1).put(damageCard(), needsCard()), verifyCard());
        refresh();
        return page;
    }

    // ------------------------------------------------------------------ inputs

    private void createInputs() {
        head = new Input("");
        contact = new Input("").placeholder("0917 000 0000");
        count = new Input("");
        address = new Input("").placeholder("House number, street, barangay");
        List<String> puroks = new ArrayList<>();
        for (int number = 1; number <= 7; number++) {
            puroks.add("Purok " + number);
        }
        purok = new Dropdown<>(puroks, text -> text).placeholder("Select purok");
        purok.noSelection();
        homeless = new Check("Household has no fixed address (homeless)");
        memberName = new Input("");
        memberAge = new Input("").placeholder("34 or 6 months");
        memberSex = Dropdown.of("Female", "Male");
        memberCategories = new MultiSelect(Arrays.asList("PWD", "Senior citizen", "Pregnant", "Infant"), "None");
        damage = Dropdown.of(Beneficiaries.DAMAGE_PARTIAL, Beneficiaries.DAMAGE_TOTAL);
        damage.noSelection();
        damage.placeholder("Select damage");
        evacuation = Dropdown.of("Evacuated", "At home");
        evacuation.noSelection();
        evacuation.placeholder("Select status");
        evacuationLocation = new Input(disaster.getEvacuationSite());
        note = new Area("", 3);
        verified = new Check("");
        for (int index = 0; index < NEEDS.length; index++) {
            needChecks[index] = new Check(NEEDS[index][0]);
            needChecks[index].setSelected(DEFAULT_NEEDS[index] > 0);
            needQuantities[index] = new Input(DEFAULT_NEEDS[index] > 0 ? String.valueOf(DEFAULT_NEEDS[index]) : "");
            needChecks[index].addActionListener(event -> refresh());
            needQuantities[index].getDocument().addDocumentListener(new SimpleListener(this::refresh));
        }
        Runnable change = this::refresh;
        for (Input input : new Input[] { head, contact, address, evacuationLocation }) {
            input.getDocument().addDocumentListener(new SimpleListener(change));
        }
        count.getDocument().addDocumentListener(new SimpleListener(() -> {
            if (count.isFocusOwner()) {
                autoCount = false;
            }
            refresh();
        }));
        note.getDocument().addDocumentListener(new SimpleListener(change));
        purok.onChange(change);
        damage.onChange(change);
        evacuation.onChange(() -> {
            if ("At home".equals(evacuation.getSelected())) {
                evacuationLocation.setText("");
            } else if (evacuationLocation.getText().isEmpty()) {
                evacuationLocation.setText(disaster.getEvacuationSite());
            }
            refresh();
        });
        homeless.addActionListener(event -> {
            address.setEnabled(!homeless.isSelected());
            purok.setEnabled(!homeless.isSelected());
            refresh();
            checkDuplicates();
        });
        verified.addActionListener(event -> refresh());
        FocusAdapter duplicateCheck = new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                checkDuplicates();
            }
        };
        head.addFocusListener(duplicateCheck);
        address.addFocusListener(duplicateCheck);
    }

    private JComponent householdCard() {
        Card card = new Card(14);
        card.put(UI.h2("Household information"), UI.muted("The household ID (HH-year-number) is assigned permanently when you save."));
        card.put(new Cols(14, 1.3, 1, 1).put(new Field("Head of household *", head),
                new Field("Contact number *", contact, "Verify the number with the household."),
                new Field("Number of members *", count, "Must match the member records below.")));
        card.put(new Cols(14, 2, 1).put(new Field("Complete address *", address), new Field("Purok *", purok)), homeless);
        return card;
    }

    private JComponent membersCard() {
        Card card = new Card(14);
        card.put(UI.titleRow(headingBlock("Family member records",
                "Include the household head. Special categories support accessible assistance planning."), null));
        card.put(membersHolder);
        Btn add = UI.secondary("Add family member");
        add.addActionListener(event -> saveMember());
        Btn cancel = UI.link("Cancel edit", () -> {
            editing = -1;
            clearEditor();
            refresh();
        });
        card.put(new Cols(14, 1.4, 0.6, 0.8, 1.1).put(new Field("Member name *", memberName), new Field("Age *", memberAge),
                new Field("Sex *", memberSex), new Field("Special category", memberCategories)));
        card.put(UI.smallPara("Available categories: None · PWD · Senior citizen · Pregnant · Infant. Select all applicable "
                + "categories; infants may use age in months."), memberError, UI.actions(add, cancel));
        return card;
    }

    private static JComponent headingBlock(String title, String subtitle) {
        VBox box = new VBox(4);
        box.put(UI.h2(title), UI.muted(subtitle));
        return box;
    }

    private JComponent damageCard() {
        Card card = new Card(14);
        card.put(UI.h2("Damage & evacuation"), new Field("House damage *", damage, "Totally damaged or partially damaged"),
                new Cols(14, 1, 1.3).put(new Field("Evacuation status *", evacuation), new Field("Evacuation location", evacuationLocation)),
                new Field("Assessment note", note));
        return card;
    }

    private JComponent needsCard() {
        Card card = new Card(12);
        card.put(UI.h2("Assistance needed"), UI.muted("Tick what the household requested and enter quantities in each item's inventory unit."));
        for (int index = 0; index < NEEDS.length; index++) {
            Field quantity = new Field(null, needQuantities[index]);
            quantity.setPreferredSize(new java.awt.Dimension(96, 42));
            card.put(new Cols(12, 1, 0, -70).put(needChecks[index], quantity, UI.muted(NEEDS[index][2] + "(s)")));
        }
        vulnerableNote = new Wrap("", Theme.regular(11.5f), Theme.MUTED);
        card.put(vulnerableNote);
        return card;
    }

    private JComponent verifyCard() {
        Card card = new Card(14);
        card.put(UI.h2("Verify household registration"), UI.muted("Review this summary before creating the household record."),
                summaryHolder);
        confirm = UI.primary("Confirm & register household");
        confirm.addActionListener(event -> save());
        card.put(saveError, new Cols(14, 1, 0, 0).put(UI.smallPara("Recorded by " + Session.user.getName() + " · "
                + Session.user.getRoleLabel() + " · " + Time.date(Time.today())),
                UI.secondary("Cancel", () -> go(new BeneficiariesPage())), confirm));
        return card;
    }

    // ------------------------------------------------------------------ members

    private static int[] parseAge(String text) {
        String value = text.trim().toLowerCase();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Enter the member's age.");
        }
        Matcher match = Pattern.compile("^(\\d{1,3})\\s*(y|yr|yrs|year|years|m|mo|mos|month|months)?$").matcher(value);
        if (!match.matches()) {
            throw new IllegalArgumentException("Enter the age like 34, or 6 months for an infant.");
        }
        int number = Integer.parseInt(match.group(1));
        String unit = match.group(2);
        if (unit != null && unit.startsWith("m")) {
            if (number > 35) {
                throw new IllegalArgumentException("Use years for anyone older than 2.");
            }
            return new int[] { 0, number };
        }
        if (number > 120) {
            throw new IllegalArgumentException("Check the age; it is too high.");
        }
        return new int[] { number, -1 };
    }

    private void saveMember() {
        memberError.removeAll();
        try {
            String name = memberName.getText().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Enter the member's name.");
            }
            int[] age = parseAge(memberAge.getText());
            Set<String> categories = memberCategories.getSelected();
            boolean under2 = age[1] >= 0 || age[0] < 2;
            if (categories.contains("Senior citizen") && (age[1] >= 0 || age[0] < 60)) {
                throw new IllegalArgumentException("Senior citizens are 60 years old or older.");
            }
            if (categories.contains("Infant") && !under2) {
                throw new IllegalArgumentException("Infants are under 2 years old. Use age in months, such as 6 months.");
            }
            if (categories.contains("Pregnant") && (!"Female".equals(memberSex.getSelected()) || age[1] >= 0 || age[0] < 10)) {
                throw new IllegalArgumentException("A pregnant member must be a female aged 10 or older.");
            }
            Draft draft = new Draft();
            draft.name = name;
            draft.years = age[0];
            draft.months = age[1] >= 0 ? age[1] : null;
            draft.sex = memberSex.getSelected();
            draft.categories = categories;
            for (int index = 0; index < members.size(); index++) {
                if (index != editing && members.get(index).name.equalsIgnoreCase(name)) {
                    throw new IllegalArgumentException(name + " is already in the member list. Use a different name or edit that row.");
                }
            }
            if (editing >= 0) {
                members.set(editing, draft);
            } else {
                members.add(draft);
            }
            editing = -1;
            clearEditor();
        } catch (IllegalArgumentException error) {
            memberError.put(UI.alert(Tone.DANGER, "This member was not added", error.getMessage()));
        }
        memberError.revalidate();
        memberError.repaint();
        refresh();
    }

    private void clearEditor() {
        memberName.setText("");
        memberAge.setText("");
        memberSex.setSelected("Female");
        memberCategories.setSelected(new LinkedHashSet<>());
        memberError.removeAll();
    }

    private void editMember(int index) {
        Draft draft = members.get(index);
        editing = index;
        memberName.setText(draft.name);
        memberAge.setText(draft.age());
        memberSex.setSelected(draft.sex);
        memberCategories.setSelected(draft.categories);
        refresh();
    }

    // ------------------------------------------------------------------ live state

    private int vulnerable(String category) {
        int total = 0;
        for (Draft draft : members) {
            if (draft.categories.contains(category)) {
                total++;
            }
        }
        return total;
    }

    private Map<String, Integer> needs(List<String> problems) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < NEEDS.length; index++) {
            if (!needChecks[index].isSelected()) {
                continue;
            }
            String text = needQuantities[index].getText().trim();
            if (!text.matches("\\d{1,6}") || Integer.parseInt(text) <= 0) {
                if (problems != null) {
                    problems.add("Enter a quantity above 0 for " + NEEDS[index][0].toLowerCase() + ".");
                }
                continue;
            }
            result.put(NEEDS[index][1], Integer.parseInt(text));
        }
        return result;
    }

    private String needsText(Map<String, Integer> needs) {
        List<String> parts = new ArrayList<>();
        for (String[] need : NEEDS) {
            Integer quantity = needs.get(need[1]);
            if (quantity != null) {
                parts.add(quantity + " " + need[2] + (quantity == 1 ? "" : "s"));
            }
        }
        return parts.isEmpty() ? "—" : String.join(" · ", parts);
    }

    private boolean headInMembers() {
        for (Draft draft : members) {
            if (draft.name.equalsIgnoreCase(head.getText().trim())) {
                return true;
            }
        }
        return false;
    }

    private void refresh() {
        if (stepperHolder == null || summaryHolder == null) {
            return;
        }
        if (autoCount && !members.isEmpty() && !count.isFocusOwner() && !settingCount
                && !count.getText().equals(String.valueOf(members.size()))) {
            // changing a document from inside its own change notification is illegal, so do it afterwards
            settingCount = true;
            javax.swing.SwingUtilities.invokeLater(() -> {
                count.setText(String.valueOf(members.size()));
                settingCount = false;
            });
        }
        List<String> problems = new ArrayList<>();
        boolean step0 = !head.getText().trim().isEmpty() && !contact.getText().trim().isEmpty()
                && count.getText().trim().matches("\\d+") && (homeless.isSelected()
                || !address.getText().trim().isEmpty() && purok.getSelected() != null);
        boolean step1 = !members.isEmpty() && count.getText().trim().equals(String.valueOf(members.size())) && headInMembers();
        Map<String, Integer> needs = needs(problems);
        problems.clear();
        boolean step2 = damage.getSelected() != null && evacuation.getSelected() != null && !needs(problems).isEmpty()
                && problems.isEmpty();
        problems.clear();
        if (head.getText().trim().isEmpty()) {
            problems.add("Enter the head of household.");
        }
        if (contact.getText().trim().isEmpty()) {
            problems.add("Enter a contact number.");
        }
        if (!homeless.isSelected() && address.getText().trim().isEmpty()) {
            problems.add("Enter the complete address, or mark the household as homeless.");
        }
        if (!homeless.isSelected() && purok.getSelected() == null) {
            problems.add("Select the purok.");
        }
        if (members.isEmpty()) {
            problems.add("Add the family members, including the household head.");
        } else if (!count.getText().trim().equals(String.valueOf(members.size()))) {
            problems.add("Number of members (" + (count.getText().trim().isEmpty() ? "blank" : count.getText().trim())
                    + ") must match the " + members.size() + " member record" + (members.size() == 1 ? "" : "s") + ".");
        } else if (!headInMembers() && !head.getText().trim().isEmpty()) {
            problems.add("The household head must appear in the family member records.");
        }
        if (damage.getSelected() == null) {
            problems.add("Select the house damage.");
        }
        if (evacuation.getSelected() == null) {
            problems.add("Select the evacuation status.");
        }
        if (needs(null).isEmpty() && problems.stream().noneMatch(text -> text.startsWith("Enter a quantity"))) {
            problems.add("Select at least one assistance item.");
        }
        needs(problems);
        int done = !step0 ? 0 : !step1 ? 1 : !step2 ? 2 : 3;

        stepperHolder.removeAll();
        stepperHolder.put(new Stepper(STEPS, done));
        stepperHolder.revalidate();
        stepperHolder.repaint();

        membersHolder.removeAll();
        DataTable table = new DataTable(new String[] { "Name", "Age", "Sex", "Special category", "Action" }, 1.6, 1, 1, 1.5, 1.2);
        for (int index = 0; index < members.size(); index++) {
            Draft draft = members.get(index);
            final int position = index;
            boolean isHead = draft.name.equalsIgnoreCase(head.getText().trim());
            table.row(isHead ? new String[] { draft.name, "Household head" } : new String[] { draft.name },
                    draft.age(), draft.sex, draft.categoryText(),
                    new Row(12).put(UI.link("Edit", () -> editMember(position)), UI.link("Remove", () -> {
                        members.remove(position);
                        if (editing == position) {
                            editing = -1;
                            clearEditor();
                        }
                        refresh();
                    })));
        }
        if (members.isEmpty()) {
            table.empty("No family members yet. Add the household head first.");
        }
        membersHolder.put(table, UI.gap(12));
        membersHolder.revalidate();
        membersHolder.repaint();

        int seniors = vulnerable("Senior citizen");
        int pregnant = vulnerable("Pregnant");
        int infants = vulnerable("Infant");
        int disabled = vulnerable("PWD");
        List<String> groups = new ArrayList<>();
        if (seniors > 0) {
            groups.add(seniors + " senior citizen" + (seniors == 1 ? "" : "s"));
        }
        if (disabled > 0) {
            groups.add(disabled + " PWD");
        }
        if (pregnant > 0) {
            groups.add(pregnant + " pregnant member" + (pregnant == 1 ? "" : "s"));
        }
        if (infants > 0) {
            groups.add(infants + " infant" + (infants == 1 ? "" : "s"));
        }
        if (vulnerableNote != null) {
            vulnerableNote.setText(groups.isEmpty() ? "Vulnerable members: none recorded yet."
                    : "Vulnerable members: " + String.join(", ", groups) + ".");
        }

        summaryHolder.removeAll();
        VBox left = new VBox(6);
        left.put(UI.fact("Household", (head.getText().trim().isEmpty() ? "—" : head.getText().trim()) + " · " + members.size()
                + " members"), UI.fact("Disaster", disaster.getName() + " · " + disaster.getDisasterId()),
                UI.fact("Address", homeless.isSelected() ? "No fixed address · Barangay " + Session.workspace.getName()
                        : (address.getText().trim().isEmpty() ? "—" : address.getText().trim()) + " · "
                        + (purok.getSelected() == null ? "—" : purok.getSelected())));
        VBox right = new VBox(6);
        right.put(UI.fact("Damage / evacuation", (damage.getSelected() == null ? "—" : damage.getSelected()) + " · "
                + (evacuation.getSelected() == null ? "—" : evacuation.getSelected())),
                UI.fact("Assistance", needsText(needs)), UI.fact("Initial status", "Pending assistance"));
        summaryHolder.put(new Cols(30, 1, 1).put(left, right));
        if (duplicateBlock != null) {
            summaryHolder.put(UI.alert(Tone.DANGER, "This household is already registered", duplicateBlock));
        } else if (!problems.isEmpty()) {
            summaryHolder.put(UI.alert(Tone.WARN, "Not ready to register · " + members.size() + " of "
                    + (count.getText().trim().isEmpty() ? "?" : count.getText().trim()) + " members recorded",
                    problems.get(0) + (problems.size() > 1 ? " (" + (problems.size() - 1) + " more to complete)" : "")));
        } else {
            summaryHolder.put(UI.alert(duplicateWarn != null ? Tone.WARN : Tone.TEAL, "Ready to register · " + members.size()
                    + " of " + members.size() + " members recorded", duplicateWarn != null ? duplicateWarn
                    : "No matching household was found for this disaster. Saving assigns the household ID and updates event "
                    + "summaries; inventory does not change."));
        }
        String headName = head.getText().trim().isEmpty() ? "the household" : head.getText().trim();
        verified.setText("Household information and needs were verified with " + headName
                + ". Collection is for barangay relief assistance.");
        summaryHolder.put(verified);
        summaryHolder.revalidate();
        summaryHolder.repaint();
        confirm.setEnabled(problems.isEmpty() && duplicateBlock == null && verified.isSelected());
    }

    private void checkDuplicates() {
        String name = head.getText().trim();
        String where = homeless.isSelected() ? Session.workspace.getName() : address.getText().trim();
        if (name.isEmpty() || where.isEmpty() || disaster == null) {
            duplicateBlock = null;
            duplicateWarn = null;
            refresh();
            return;
        }
        Task.run(() -> new BeneficiaryController().findSimilar(disaster, name, where), (List<BeneficiaryRepository.Row> found) -> {
            duplicateBlock = null;
            duplicateWarn = null;
            for (BeneficiaryRepository.Row row : found) {
                boolean sameName = row.head.equalsIgnoreCase(name);
                boolean sameAddress = row.address.equalsIgnoreCase(where);
                if (sameName && sameAddress) {
                    duplicateBlock = row.householdId + " (" + row.head + ", " + row.address + ") is already registered for "
                            + disaster.getName() + ". Open that record instead of registering it again.";
                    break;
                }
                if (duplicateWarn == null) {
                    duplicateWarn = "A similar household exists: " + row.householdId + " (" + row.head + ", " + row.address
                            + "). Review it before saving; this may be a different family.";
                }
            }
            refresh();
        }, error -> { });
    }

    private void save() {
        saveError.removeAll();
        confirm.setEnabled(false);
        List<FamilyMember> list = new ArrayList<>();
        int number = 1;
        for (Draft draft : members) {
            list.add(new FamilyMember("t" + number++, draft.name, draft.years, draft.months, draft.sex,
                    draft.categories.contains("Senior citizen"), draft.categories.contains("Infant"),
                    draft.categories.contains("Pregnant"), draft.categories.contains("PWD")));
        }
        Map<String, Integer> needs = needs(null);
        int declared = Integer.parseInt(count.getText().trim());
        Task.run(() -> new BeneficiaryController().registerHousehold(disaster, head.getText(), contact.getText(),
                address.getText(), homeless.isSelected(), purok.getSelected(), declared, list, damage.getSelected(),
                evacuation.getSelected(), evacuationLocation.getText(), note.getText(), needs, Session.user), household -> {
                    go(new HouseholdDetailPage(household.getBeneficiaryId(), disaster, "Household registered · unique ID assigned. "
                            + household.getBeneficiaryId() + " was registered on " + Time.longStamp(Time.now())
                            + ". The family is awaiting assistance for " + disaster.getName() + "."));
                }, error -> {
                    saveError.put(UI.alert(Tone.DANGER, "The household was not saved", Task.message(error)));
                    saveError.revalidate();
                    saveError.repaint();
                    refresh();
                });
    }
}
