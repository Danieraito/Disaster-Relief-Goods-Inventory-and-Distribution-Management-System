package GUI;

import GUI.Theme.Tone;
import LIB.Beneficiaries;
import LIB.BeneficiaryController;
import LIB.BeneficiaryRepository;
import LIB.Disaster;
import LIB.DisasterController;
import LIB.Distribution;
import LIB.DistributionController;
import LIB.DistributionRepository;
import LIB.EvacuationCenter;
import LIB.EvacuationCenterRepository;
import LIB.Inventory;
import LIB.InventoryBatch;
import LIB.InventoryController;
import LIB.Time;
import LIB.Units;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;

/** New distribution: choose the recipient and items, review the stock deduction, then confirm. */
class DistributionFormPage extends Page {
    private static final String[] ACKNOWLEDGMENTS = { "Signed paper center receipt · file on confirmation",
        "Signed paper claim stub · file on confirmation", "Signed digital acknowledgment" };
    private static final String[][] NEED_ITEMS = { { "Food", "pack" }, { "Water", "liter" }, { "Hygiene kits", "kit" },
        { "Clothing", "bundle" }, { "Medicine", "box" }, { "Shelter materials", "piece" } };

    private static final class Line {
        Inventory item;
        String batchId;
        String quantity = "";
        Pill status;
        Wrap after;
    }

    private final String presetHouseholdId;
    private List<Disaster> disasters = new ArrayList<>();
    private Disaster disaster;
    private List<Inventory> items = new ArrayList<>();
    private List<EvacuationCenter> centers = new ArrayList<>();
    private List<BeneficiaryRepository.Row> households = new ArrayList<>();
    private int mode;
    private EvacuationCenter center;
    private BeneficiaryRepository.Row representative;
    private final List<Line> lines = new ArrayList<>();
    private String dateText = Time.date(Time.today());
    private String acknowledgment = ACKNOWLEDGMENTS[0];
    private String notes = "";
    private int step;
    private DistributionRepository.Problem problem;
    private String blockedReference;
    private String message;
    private boolean verified;
    private boolean initialized;
    private DistributionRepository.Problem liveProblem;
    private boolean liveReady;
    private final VBox checksHolder = new VBox(8);
    private javax.swing.Timer debounce;

    DistributionFormPage() {
        this(null);
    }

    DistributionFormPage(String householdId) {
        this.presetHouseholdId = householdId;
    }

    @Override String module() { return "Distribution"; }
    @Override String crumb() { return "Distribution / " + (step == 0 ? "New release" : "Review"); }

    // ------------------------------------------------------------------ loading

    @Override
    void load() throws Exception {
        disasters = new DisasterController().list(Session.workspace, Session.user);
        disaster = DisasterBand.resolve(disasters);
        items = new InventoryController().list(Session.workspace, false, Session.user);
        centers = new EvacuationCenterRepository().findByBarangay(Session.workspace);
        if (disaster == null || !disaster.isActive()) {
            return;
        }
        BeneficiaryRepository.Filter filter = new BeneficiaryRepository.Filter();
        households = new BeneficiaryController().page(disaster, filter, 1, 500, Session.user).rows;
        if (!initialized) {
            initialized = true;
            if (presetHouseholdId != null) {
                mode = 1;
                acknowledgment = ACKNOWLEDGMENTS[1];
                for (BeneficiaryRepository.Row row : households) {
                    if (row.householdId.equals(presetHouseholdId)) {
                        representative = row;
                    }
                }
                Beneficiaries household = new BeneficiaryController().find(presetHouseholdId, disaster, Session.user);
                if (household != null) {
                    prefillFromNeeds(household);
                }
            }
        }
        if (center == null && centers.size() == 1) {
            center = centers.get(0);
        }
        if (lines.isEmpty()) {
            lines.add(new Line());
        }
    }

    private void prefillFromNeeds(Beneficiaries household) {
        LocalDate today = Time.today();
        for (String[] need : NEED_ITEMS) {
            Integer requested = household.getNeeds().get(need[0]);
            if (requested == null) {
                continue;
            }
            int remaining = requested - household.getReleased().getOrDefault(need[0], 0);
            if (remaining <= 0) {
                continue;
            }
            Inventory best = null;
            for (Inventory item : items) {
                if (item.getCategory().equals(need[0]) && item.getAvailableQuantity(today) > 0
                        && (best == null || item.getAvailableQuantity(today) > best.getAvailableQuantity(today))) {
                    best = item;
                }
            }
            if (best != null) {
                Line line = new Line();
                line.item = best;
                InventoryBatch batch = defaultBatch(best, today);
                line.batchId = batch == null ? null : batch.getBatchId();
                line.quantity = String.valueOf(batch == null ? remaining : Math.min(remaining, batch.getRemainingQuantity()));
                lines.add(line);
            }
        }
    }

    /** The usable batch that expires first (a suggestion the officer can change). */
    static InventoryBatch defaultBatch(Inventory item, LocalDate asOf) {
        InventoryBatch best = null;
        for (InventoryBatch batch : item.getBatches()) {
            if (batch.getRemainingQuantity() > 0 && !batch.isExpired(asOf) && (best == null
                    || batch.getExpiryLocalDate() != null && (best.getExpiryLocalDate() == null
                    || batch.getExpiryLocalDate().isBefore(best.getExpiryLocalDate())))) {
                best = batch;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ build

    @Override
    JComponent build() {
        VBox page = new VBox(18);
        String kind = mode == 0 ? "evacuation center" : "household";
        page.put(UI.pageHeader(step == 0 ? "New " + kind + " distribution" : "Review " + kind + " release",
                step == 0 ? "Select " + (mode == 0 ? "an evacuation center" : "a household") + ", verify prior assistance and "
                        + "allocate available items." : "Confirm the recipient, quantities and stock deductions before releasing goods.", null));
        if (disaster == null) {
            page.put(DisasterBand.empty(this, "Releases are always recorded against an event."));
            return page;
        }
        page.put(DisasterBand.of(this, disasters, disaster));
        if (!disaster.isActive()) {
            page.put(UI.alert(Tone.WARN, "This disaster is closed", "Choose an active disaster to release goods.",
                    UI.secondary("Change disaster", () -> DisasterBand.choose(this, disasters))));
            return page;
        }
        page.put(new Stepper(new String[] { "Recipient & items", "Review release", "Confirm & acknowledge" }, step));
        if (message != null) {
            page.put(UI.alert(Tone.DANGER, "This release could not be saved", message));
        }
        if (problem != null) {
            page.put(UI.alert(Tone.DANGER, problem.title, problem.message + (blockedReference == null ? ""
                    : " The blocked attempt was logged as " + blockedReference + ".")));
        }
        if (step == 0) {
            if (problem != null) {
                page.put(blockedCard());
            }
            page.put(formStep());
        } else {
            page.put(reviewStep());
        }
        return page;
    }

    // ------------------------------------------------------------------ step 1

    private JComponent formStep() {
        VBox box = new VBox(20);
        if (presetHouseholdId == null) {
            box.put(UI.tabs(java.util.Arrays.asList("Evacuation center release", "Household release"), mode, index -> {
                mode = index;
                acknowledgment = ACKNOWLEDGMENTS[index];
                problem = null;
                message = null;
                rebuild();
            }));
        }
        box.put(new Cols(22, 1.6, 1).put(recipientCard(), checksCard()), itemsCard(), releaseCard());
        scheduleCheck();
        return box;
    }

    private JComponent recipientCard() {
        Card card = new Card(14);
        List<BeneficiaryRepository.Row> people = households;
        Dropdown<BeneficiaryRepository.Row> person = new Dropdown<>(people, row -> row.head + " · " + row.householdId
                + (mode == 1 && row.assisted ? " · assisted" : ""));
        person.placeholder("Select a household");
        person.setSelected(representative);
        person.onChange(() -> {
            representative = person.getSelected();
            scheduleCheck();
        });
        if (mode == 0) {
            Dropdown<EvacuationCenter> centerBox = new Dropdown<>(centers, EvacuationCenter::getName);
            centerBox.placeholder("Select an evacuation center");
            centerBox.setSelected(center);
            centerBox.onChange(() -> {
                center = centerBox.getSelected();
                scheduleCheck();
            });
            card.put(UI.h2("Evacuation center"), new Field("Select evacuation center *", centerBox),
                    UI.left(UI.link("Add evacuation center", this::addCenter)),
                    new Field("Household representative receiving on behalf of the center *", person),
                    UI.fact("Barangay", "Barangay " + Session.workspace.getName()));
        } else {
            card.put(UI.h2("Household"), new Field("Select household *", person),
                    UI.fact("Barangay", "Barangay " + Session.workspace.getName()),
                    UI.smallPara("Releasing to a household marks it as assisted once the release is confirmed."));
        }
        return card;
    }

    private JComponent checksCard() {
        Card card = new Card(10);
        card.put(UI.h2("Availability checks"), checksHolder,
                UI.smallPara("Expired or quarantined goods cannot be selected. Availability is checked again when the release is "
                        + "confirmed."));
        refreshChecks();
        return card;
    }

    private void refreshChecks() {
        checksHolder.removeAll();
        boolean recipient = mode == 0 ? center != null && representative != null : representative != null;
        checksHolder.put(checkRow(recipient ? 1 : -1, mode == 0 ? "Evacuation center and representative selected"
                : "Household selected"));
        if (!liveReady) {
            checksHolder.put(checkRow(-1, "Requested items are available"), checkRow(-1, "No duplicate release found"));
        } else {
            boolean stockProblem = liveProblem != null && (liveProblem.kind == DistributionRepository.Problem.Kind.STOCK
                    || liveProblem.kind == DistributionRepository.Problem.Kind.EXPIRED
                    || liveProblem.kind == DistributionRepository.Problem.Kind.OTHER);
            boolean duplicate = liveProblem != null && liveProblem.kind == DistributionRepository.Problem.Kind.DUPLICATE;
            checksHolder.put(checkRow(stockProblem ? 0 : 1, stockProblem ? liveProblem.message : "Requested items are available"),
                    checkRow(duplicate ? 0 : 1, duplicate ? "Already released under " + liveProblem.existingId
                            : "No duplicate release found for this request"));
        }
        checksHolder.revalidate();
        checksHolder.repaint();
    }

    private JComponent checkRow(int state, String text) {
        Tone tone = state == 1 ? Tone.TEAL : state == 0 ? Tone.DANGER : Tone.NEUTRAL;
        Cols row = new Cols(10, -18, 1);
        row.put(UI.label(state == 1 ? "✓" : state == 0 ? "✗" : "…", Theme.bold(13), tone.text()),
                new Wrap(text, Theme.regular(12.5f), state == -1 ? Theme.MUTED : tone.text()).maxWidth(600));
        return row;
    }

    private JComponent itemsCard() {
        Card card = new Card(12);
        VBox heading = new VBox(4);
        heading.put(UI.h2("Items to release"), UI.muted("Choose the stock batch for each line. The batch that expires first is suggested."));
        card.put(UI.titleRow(heading, UI.secondary("Add item", () -> {
            lines.add(new Line());
            rebuild();
        })));
        Cols header = lineLayout();
        for (String title : new String[] { "Item", "Stock batch", "Available", "Release quantity", "Stock after", "Validation", "" }) {
            header.put(UI.label(title, Theme.medium(11.5f), Theme.MUTED));
        }
        card.put(header);
        for (int index = 0; index < lines.size(); index++) {
            card.put(lineRow(index));
        }
        return card;
    }

    private static Cols lineLayout() {
        return new Cols(10, 1.6, 2.7, 0.9, 0.9, 0.9, 1, -62);
    }

    private JComponent lineRow(int index) {
        Line line = lines.get(index);
        LocalDate today = Time.today();
        List<Inventory> choices = new ArrayList<>();
        for (Inventory item : items) {
            if (item.getAvailableQuantity(today) > 0 || item == line.item) {
                choices.add(item);
            }
        }
        Dropdown<Inventory> itemBox = new Dropdown<>(choices, item -> item.getItemName() + " · " + item.getItemId());
        itemBox.placeholder("Choose item");
        itemBox.setSelected(line.item);
        itemBox.onChange(() -> {
            line.item = itemBox.getSelected();
            InventoryBatch batch = line.item == null ? null : defaultBatch(line.item, today);
            line.batchId = batch == null ? null : batch.getBatchId();
            rebuild();
        });
        List<InventoryBatch> batches = new ArrayList<>();
        if (line.item != null) {
            for (InventoryBatch batch : line.item.getBatches()) {
                if (batch.getRemainingQuantity() > 0 && !batch.isExpired(today)) {
                    batches.add(batch);
                }
            }
        }
        Dropdown<InventoryBatch> batchBox = new Dropdown<>(batches, batch -> batch.getBatchId() + " · "
                + Units.quantity(batch.getRemainingQuantity(), line.item.getUnit())
                + (batch.getExpiryLocalDate() == null ? "" : " · expires " + Time.shortDate(batch.getExpiryLocalDate())));
        batchBox.placeholder("Choose batch");
        batchBox.setSelected(line.item == null || line.batchId == null ? null : line.item.findBatch(line.batchId));
        batchBox.onChange(() -> {
            line.batchId = batchBox.getSelected() == null ? null : batchBox.getSelected().getBatchId();
            updateLine(line);
            scheduleCheck();
        });
        Input quantity = new Input(line.quantity);
        quantity.getDocument().addDocumentListener(new SimpleListener(() -> {
            line.quantity = quantity.getText();
            updateLine(line);
            scheduleCheck();
        }));
        line.after = new Wrap("—", Theme.regular(13), Theme.TEXT);
        line.status = UI.pill("—", Tone.NEUTRAL);
        updateLine(line);
        Cols row = lineLayout();
        row.put(new Field(null, itemBox), new Field(null, batchBox), UI.body(line.item == null ? "—"
                : Units.quantity(line.item.getAvailableQuantity(today), line.item.getUnit())), new Field(null, quantity),
                line.after, UI.left(line.status), UI.link("Remove", () -> {
                    lines.remove(index);
                    if (lines.isEmpty()) {
                        lines.add(new Line());
                    }
                    rebuild();
                }));
        return row;
    }

    private void updateLine(Line line) {
        if (line.after == null || line.status == null) {
            return;
        }
        if (line.item == null) {
            line.after.setText("—");
            line.status.set("—", Tone.NEUTRAL);
            return;
        }
        int amount = number(line.quantity);
        InventoryBatch batch = line.batchId == null ? null : line.item.findBatch(line.batchId);
        int batchLeft = batch == null ? 0 : batch.getRemainingQuantity();
        line.after.setText(amount <= 0 ? "—" : Units.quantity(Math.max(0, line.item.getAvailableQuantity(Time.today()) - amount),
                line.item.getUnit()));
        if (amount <= 0 || batch == null) {
            line.status.set(amount <= 0 ? "Enter quantity" : "Choose batch", Tone.NEUTRAL);
        } else if (amount > batchLeft) {
            line.status.set("Blocked", Tone.DANGER);
        } else {
            line.status.set("Available", Tone.TEAL);
        }
    }

    private static int number(String text) {
        String value = text == null ? "" : text.trim().replace(",", "");
        return value.matches("\\d{1,9}") ? Integer.parseInt(value) : 0;
    }

    private JComponent releaseCard() {
        Input date = new Input(dateText);
        date.getDocument().addDocumentListener(new SimpleListener(() -> {
            dateText = date.getText();
            scheduleCheck();
        }));
        Dropdown<String> method = Dropdown.of(ACKNOWLEDGMENTS);
        method.setSelected(acknowledgment);
        method.onChange(() -> acknowledgment = method.getSelected());
        Area note = new Area(notes, 2);
        note.getDocument().addDocumentListener(new SimpleListener(() -> notes = note.getText()));
        Btn draft = UI.secondary("Save draft", () -> save(DistributionRepository.Mode.DRAFT));
        Btn review = UI.primary("Review distribution", this::toReview);
        Card card = new Card(14);
        card.put(UI.h2("Release information"), new Cols(14, 1, 1, 1.6).put(new Field("Distribution date *", date),
                new Field("Releasing officer *", new Input(Session.canManage() ? Session.user.getName()
                        : "Assigned when an official approves")).readOnly(), new Field("Acknowledgment method *", method)),
                new Field("Notes", note), new Cols(14, 1, 0, 0).put(UI.smallPara("Stock is not deducted until the reviewed "
                        + "release is confirmed."), draft, review));
        return card;
    }

    private void scheduleCheck() {
        if (debounce != null) {
            debounce.stop();
        }
        debounce = new javax.swing.Timer(450, event -> runLiveCheck());
        debounce.setRepeats(false);
        debounce.start();
        refreshChecks();
    }

    private void runLiveCheck() {
        DistributionRepository.Request request;
        try {
            request = buildRequest();
        } catch (IllegalArgumentException error) {
            liveReady = false;
            refreshChecks();
            return;
        }
        Task.run(() -> new DistributionController().check(request, Session.user), (DistributionRepository.Problem result) -> {
            liveProblem = result;
            liveReady = true;
            refreshChecks();
        }, error -> {
            liveReady = false;
            refreshChecks();
        });
    }

    private void addCenter() {
        Input name = new Input("");
        Input address = new Input("");
        Input capacity = new Input("");
        VBox form = new VBox(12);
        form.put(new Field("Center name *", name), new Field("Address *", address), new Field("Capacity (people) *", capacity));
        EvacuationCenter[] created = new EvacuationCenter[1];
        boolean ok = Dialogs.form(shell, "Add evacuation center", "Centers are shared by every disaster of this barangay.", form,
                "Save center", () -> {
                    try {
                        created[0] = new EvacuationCenterRepository().create(name.getText(), address.getText(),
                                Fmt.parseCount(capacity.getText(), "Capacity"), Session.workspace, Session.user);
                        return null;
                    } catch (Exception error) {
                        return Task.message(error);
                    }
                }, 560);
        if (ok) {
            Task.run(() -> new EvacuationCenterRepository().findByBarangay(Session.workspace), fresh -> {
                centers = fresh;
                for (EvacuationCenter candidate : fresh) {
                    if (candidate.getCenterId().equals(created[0].getCenterId())) {
                        center = candidate;
                    }
                }
                rebuild();
            }, error -> Dialogs.error(shell, error));
        }
    }

    // ------------------------------------------------------------------ requests

    private DistributionRepository.Request buildRequest() {
        DistributionRepository.Request request = new DistributionRepository.Request();
        request.disaster = disaster;
        if (mode == 0) {
            if (center == null) {
                throw new IllegalArgumentException("Select the evacuation center.");
            }
            if (representative == null) {
                throw new IllegalArgumentException("Select the household representative who receives on behalf of the center.");
            }
            request.centerId = center.getCenterId();
        } else if (representative == null) {
            throw new IllegalArgumentException("Select the household that receives the goods.");
        }
        request.householdId = representative.householdId;
        request.date = Fmt.parseDate(dateText);
        if (request.date == null) {
            throw new IllegalArgumentException("Enter the distribution date.");
        }
        request.acknowledgment = acknowledgment;
        request.notes = notes;
        for (Line line : lines) {
            if (line.item == null || line.batchId == null) {
                throw new IllegalArgumentException("Choose an item and its stock batch for every line.");
            }
            int amount = number(line.quantity);
            if (amount <= 0) {
                throw new IllegalArgumentException("Enter a release quantity above 0 for " + line.item.getItemName() + ".");
            }
            request.lines.add(new DistributionRepository.Line(line.item.getItemId(), line.batchId, amount));
        }
        if (request.lines.isEmpty()) {
            throw new IllegalArgumentException("Add at least one item to release.");
        }
        return request;
    }

    private void toReview() {
        problem = null;
        blockedReference = null;
        message = null;
        DistributionRepository.Request request;
        try {
            request = buildRequest();
        } catch (IllegalArgumentException error) {
            message = error.getMessage();
            rebuild();
            return;
        }
        Task.run(() -> {
            DistributionController controller = new DistributionController();
            DistributionRepository.Problem found = controller.check(request, Session.user);
            String reference = found == null ? null : controller.logBlocked(found, request, Session.user);
            return new Object[] { found, reference };
        }, result -> {
            problem = (DistributionRepository.Problem) result[0];
            blockedReference = (String) result[1];
            if (problem == null) {
                verified = false;
                step = 1;
            }
            rebuild();
        }, error -> {
            message = Task.message(error);
            rebuild();
        });
    }

    private JComponent blockedCard() {
        Card card = new Card(14);
        if (problem.kind == DistributionRepository.Problem.Kind.DUPLICATE) {
            DataTable table = new DataTable(new String[] { "Assistance", "Already released", "New request", "Validation" }, 1.6, 1, 1, 1);
            for (String[] row : problem.rows) {
                table.row(row[0], row[1], row[2], UI.left(UI.pill(row[4], "Duplicate".equals(row[4]) ? Tone.DANGER : Tone.TEAL)));
            }
            card.put(UI.h2("Conflicting release record"), UI.fact("Existing release", problem.existingId),
                    UI.small(problem.existingDetail), table,
                    UI.note(Tone.WARN, "New unmet need? Review first.", "Verify updated needs and create a separate request with a "
                            + "different household representative. Do not create a second release to bypass this block."),
                    UI.actions(UI.secondary("Open the original release", () -> go(new DistributionDetailPage(problem.existingId)))));
        } else {
            DataTable table = new DataTable(new String[] { "Item", "Requested", "Available", "Shortfall", "Result" }, 1.6, 1, 1, 1, 1);
            for (String[] row : problem.rows) {
                table.row(row[0], row[1], row[2], row[3], UI.left(UI.pill(row[4], "Blocked".equals(row[4]) ? Tone.DANGER : Tone.TEAL)));
            }
            card.put(UI.h2("Correct the quantity"), table, UI.note(Tone.INFO, "Verify the entry first",
                    "Check whether the quantity was an entry error before requesting more stock."),
                    UI.actions(UI.secondary("Record incoming donation", () -> go(new DonationFormPage())),
                            UI.secondary("View stock", () -> go(new InventoryPage()))));
        }
        return card;
    }

    // ------------------------------------------------------------------ step 2

    private JComponent reviewStep() {
        DistributionRepository.Request request = buildRequest();
        VBox box = new VBox(20);
        Card recipient = new Card(12);
        recipient.put(UI.h2(mode == 0 ? "Evacuation center & household representative" : "Household"));
        if (mode == 0) {
            recipient.put(UI.fact("Evacuation center", center.getName()), UI.fact("Barangay", "Barangay " + Session.workspace.getName()),
                    UI.fact("Household representative", representative.head), UI.fact("Prior assistance", "No duplicate release for this request"));
        } else {
            recipient.put(UI.fact("Household", representative.head + " · " + representative.householdId),
                    UI.fact("Address", representative.address + (representative.purok.isEmpty() ? "" : " · " + representative.purok)),
                    UI.fact("Prior assistance", "No duplicate release for these items"));
        }
        Card record = new Card(12);
        record.put(UI.h2("Release record"), UI.fact("Distribution date", Time.date(request.date)),
                UI.fact("Releasing officer", Session.canManage() ? Session.user.getName() : "Assigned when approved"),
                UI.fact("Acknowledgment", acknowledgment), UI.fact("Reference", "Assigned on save"));
        box.put(new Cols(22, 1, 1).put(recipient, record));

        LocalDate today = Time.today();
        DataTable table = new DataTable(new String[] { "Item / expiry", "Available before", "Release", "Available after",
            "Threshold / outcome" }, 1.8, 1, 1, 1, 1.5);
        Map<String, Integer> out = new LinkedHashMap<>();
        List<String> low = new ArrayList<>();
        int totalOut = 0;
        for (Line line : lines) {
            int amount = number(line.quantity);
            out.merge(line.item.getItemId(), amount, Integer::sum);
            totalOut += amount;
        }
        int sequence = 0;
        Map<String, Integer> running = new LinkedHashMap<>();
        for (Line line : lines) {
            int amount = number(line.quantity);
            int before = running.getOrDefault(line.item.getItemId(), line.item.getAvailableQuantity(today));
            int after = before - amount;
            running.put(line.item.getItemId(), after);
            InventoryBatch batch = line.item.findBatch(line.batchId);
            boolean below = after <= line.item.getLowStockThreshold();
            if (below) {
                low.add(line.item.getItemName() + " (" + Units.quantity(after, line.item.getUnit()) + " left, threshold "
                        + Fmt.n(line.item.getLowStockThreshold()) + ")");
            }
            table.row(new String[] { line.item.getItemName(), (batch == null ? "" : batch.getBatchId()) + (batch == null
                    || batch.getExpiryLocalDate() == null ? " · No expiry" : " · " + Time.date(batch.getExpiryLocalDate())) },
                    Units.quantity(before, line.item.getUnit()), "−" + Units.quantity(amount, line.item.getUnit()),
                    Units.quantity(after, line.item.getUnit()), Fmt.n(line.item.getLowStockThreshold())
                            + " · " + (below ? "Below threshold" : "Above threshold"));
            sequence++;
        }
        Card impact = new Card(14);
        impact.put(UI.h2("Stock deduction preview"), UI.muted("Stock availability and duplicate checks are revalidated on confirmation."),
                table, UI.smallPara(sequence + " item lines · " + Fmt.n(totalOut) + " recorded units to deduct"));
        int totalBefore = 0;
        for (Inventory item : items) {
            totalBefore += item.getQuantity();
        }
        boolean approver = Session.user.canApproveDistributions();
        impact.put(UI.alert(Tone.INFO, approver ? "Confirmation updates inventory and issues a receipt" : "Submitting sends this release for approval",
                "Stock total changes from " + Fmt.n(totalBefore) + " to " + Fmt.n(totalBefore - totalOut) + " mixed units."
                + (mode == 0 ? " The household representative is not marked as assisted by a center delivery."
                        : " The household is marked as assisted when the release is confirmed.")));
        if (!low.isEmpty()) {
            impact.put(UI.alert(Tone.WARN, "This release leaves stock at or below its threshold", String.join("; ", low)
                    + ". A warning will also appear on the saved acknowledgment."));
        }
        box.put(impact);

        Check verify = new Check("I verified the " + (mode == 0 ? "center, household representative, " : "household, ")
                + "quantities and signed receipt. The goods are ready for release.");
        verify.setSelected(verified);
        Btn confirm = UI.primary(approver ? (mode == 0 ? "Confirm & issue center receipt" : "Confirm & issue claim receipt")
                : "Submit for approval");
        confirm.setEnabled(verified);
        verify.addActionListener(event -> {
            verified = verify.isSelected();
            confirm.setEnabled(verified);
        });
        confirm.addActionListener(event -> {
            confirm.setEnabled(false);
            save(approver ? DistributionRepository.Mode.POST : DistributionRepository.Mode.SUBMIT);
        });
        Card actions = new Card(14);
        actions.put(verify, new Cols(14, 1, 0, 0).put(UI.smallPara("Permanent stock movement · use a logged correction for any later error."),
                UI.secondary(mode == 0 ? "Back to center release" : "Back to release", () -> {
                    step = 0;
                    rebuild();
                }), confirm));
        box.put(actions);
        return box;
    }

    // ------------------------------------------------------------------ saving

    private void save(DistributionRepository.Mode saveMode) {
        message = null;
        problem = null;
        DistributionRepository.Request request;
        try {
            request = buildRequest();
        } catch (IllegalArgumentException error) {
            message = error.getMessage();
            step = 0;
            rebuild();
            return;
        }
        Task.run(() -> new DistributionController().record(request, saveMode, Session.user), (Distribution saved) -> {
            if (saveMode == DistributionRepository.Mode.POST) {
                go(new DistributionDetailPage(saved.getDistributionId(), saved.getDistributionId() + " confirmed · "
                        + DistributionDetailPage.summary(saved)));
            } else if (saveMode == DistributionRepository.Mode.SUBMIT) {
                go(new DistributionPage(saved.getDistributionId() + " was submitted for approval. Stock changes only after an "
                        + "official confirms it."));
            } else {
                go(new DistributionPage(saved.getDistributionId() + " was saved as a draft. No stock was changed."));
            }
        }, error -> {
            if (error instanceof DistributionRepository.BlockedException) {
                problem = ((DistributionRepository.BlockedException) error).problem;
            } else {
                message = Task.message(error);
            }
            step = 0;
            rebuild();
        });
    }
}
