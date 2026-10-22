package GUI;

import GUI.Theme.Tone;
import LIB.CategoryRules;
import LIB.DonationController;
import LIB.DonationRepository;
import LIB.Donations;
import LIB.Donor;
import LIB.DonorRepository;
import LIB.Inventory;
import LIB.InventoryController;
import LIB.TransactionItem;
import LIB.Time;
import LIB.Units;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;

/** Record donation: donor and items, then the inventory-impact review. */
class DonationFormPage extends Page {
    private static final Inventory CREATE = new Inventory("__new__", "+ Create new item…", 0, "unit");

    private static final class Line {
        Inventory item;
        String quantity = "";
        String expiry = "";
    }

    private final String existingId;
    private List<Inventory> items = new ArrayList<>();
    private List<DonorRepository.Row> donorRows = new ArrayList<>();
    private Donor donor;
    private String dateText = Time.date(Time.today());
    private String location = "Barangay hall · Receiving desk";
    private String reference = "";
    private String representative = "";
    private String notes = "";
    private final boolean[] checks = new boolean[3];
    private final List<Line> lines = new ArrayList<>();
    private int step;
    private String problem;
    private boolean verified;
    private boolean initialized;

    DonationFormPage() {
        this(null);
    }

    DonationFormPage(String draftId) {
        this.existingId = draftId;
    }

    @Override String module() { return "Donations"; }
    @Override String crumb() { return "Donations / " + (step == 0 ? "New donation" : "Review"); }

    @Override
    void load() throws Exception {
        items = new InventoryController().list(Session.workspace, false, Session.user);
        DonationController controller = new DonationController();
        donorRows = controller.donorRows(Session.workspace);
        if (!initialized) {
            initialized = true;
            if (existingId != null) {
                Donations draft = controller.find(existingId, Session.user);
                if (draft != null) {
                    for (DonorRepository.Row row : donorRows) {
                        if (row.donor.getDonorId().equals(draft.getDonor().getDonorId())) {
                            donor = row.donor;
                        }
                    }
                    dateText = Time.date(LocalDate.parse(draft.getDonationDate()));
                    location = draft.getReceivingLocation();
                    reference = draft.getDeliveryReference();
                    representative = draft.getDonorRepresentative();
                    notes = draft.getNotes();
                    for (TransactionItem item : draft.getItems()) {
                        Line line = new Line();
                        for (Inventory known : items) {
                            if (known.getItemId().equals(item.getInventory().getItemId())) {
                                line.item = known;
                            }
                        }
                        line.quantity = String.valueOf(item.getQuantity());
                        line.expiry = item.getExpiryDate() == null || item.getExpiryDate().isEmpty() ? ""
                                : Time.date(LocalDate.parse(item.getExpiryDate()));
                        lines.add(line);
                    }
                }
            }
        }
        if (lines.isEmpty()) {
            lines.add(new Line());
        }
    }

    @Override
    JComponent build() {
        VBox page = new VBox(18);
        page.put(UI.pageHeader(step == 0 ? "Record donation" : "Review donation", step == 0
                ? "Capture incoming goods before reviewing their inventory impact."
                : "Verify the donor, received items and resulting stock before confirming.", null));
        page.put(new Stepper(new String[] { "Donor & donated items", "Review inventory impact", "Save & acknowledge" }, step));
        if (problem != null) {
            page.put(UI.alert(Tone.DANGER, "Check the donation before continuing", problem));
        }
        page.put(step == 0 ? formStep() : reviewStep());
        return page;
    }

    // ------------------------------------------------------------------ step 1

    private JComponent formStep() {
        Dropdown<Donor> donorBox = new Dropdown<>(donorList(), Donor::getName);
        donorBox.placeholder("Select a donor");
        donorBox.setSelected(donor);
        donorBox.onChange(() -> {
            donor = donorBox.getSelected();
            rebuild();
        });
        Input date = new Input(dateText);
        date.getDocument().addDocumentListener(new SimpleListener(() -> dateText = date.getText()));
        Input place = new Input(location);
        place.getDocument().addDocumentListener(new SimpleListener(() -> location = place.getText()));
        Input slip = new Input(reference).placeholder("e.g. BF-1003");
        slip.getDocument().addDocumentListener(new SimpleListener(() -> reference = slip.getText()));
        Input rep = new Input(representative);
        rep.getDocument().addDocumentListener(new SimpleListener(() -> representative = rep.getText()));
        Area note = new Area(notes, 3);
        note.getDocument().addDocumentListener(new SimpleListener(() -> notes = note.getText()));

        Card details = new Card(14);
        details.put(UI.h2("Donor & receiving details"));
        details.put(new Cols(14, 1.4, 1).put(new Field("Donor *", donorBox), new Field("Donor type", new Input(donor == null ? "—"
                : DonationsPage.typeLabel(donor.getType()))).readOnly()));
        details.put(new Field("Donor contact", new Input(donor == null ? "—" : donor.getContactPerson() + " · "
                + donor.getContactDetails())).readOnly(), UI.left(UI.link("Add a new donor instead", this::addDonor)));
        details.put(new Cols(14, 1, 1, 1.3).put(new Field("Received date *", date),
                new Field("Receiving officer *", new Input(Session.canManage() ? Session.user.getName()
                        : "Assigned when an official approves")).readOnly(), new Field("Receiving location", place)));
        details.put(new Cols(14, 1, 1).put(new Field("Delivery slip reference", slip), new Field("Donor representative", rep)));

        Card goods = new Card(12);
        VBox heading = new VBox(4);
        heading.put(UI.h2("Donated items"), UI.muted("Add multiple items. Match existing records to avoid duplicates."));
        Btn addRow = UI.secondary("Add item row", () -> {
            lines.add(new Line());
            rebuild();
        });
        goods.put(UI.titleRow(heading, addRow), lineHeader());
        for (int index = 0; index < lines.size(); index++) {
            goods.put(lineRow(index));
        }
        goods.put(UI.smallPara("Can't find the donated item? Choose “+ Create new item…” and enter its name, category, unit, "
                + "threshold and storage location. A new inventory entry is created with zero stock until the donation is confirmed."));
        goods.put(new Field("Delivery / receiving note", note));
        int total = 0;
        for (Line line : lines) {
            total += line.quantity.trim().matches("\\d{1,9}") ? Integer.parseInt(line.quantity.trim()) : 0;
        }
        Btn draft = UI.secondary("Save draft", () -> save(DonationRepository.Mode.DRAFT));
        Btn review = UI.primary("Review donation", this::toReview);
        goods.put(new Cols(14, 1, 0, 0).put(UI.smallPara(lines.size() + " item line" + (lines.size() == 1 ? "" : "s") + " · "
                + Fmt.n(total) + " recorded units across different units"), draft, review));

        Card checklist = new Card(12);
        Check[] boxes = { new Check("Quantities counted against delivery slip"), new Check("Packaging checked for damage"),
            new Check("Expiry dates verified") };
        checklist.put(UI.h2("Receiving checklist"));
        for (int index = 0; index < boxes.length; index++) {
            final int position = index;
            boxes[index].setSelected(checks[index]);
            boxes[index].addActionListener(event -> checks[position] = boxes[position].isSelected());
            checklist.put(boxes[index]);
        }
        VBox side = new VBox(20);
        side.put(checklist, UI.note(Tone.INFO, "No stock changes yet", "Continue to review. Confirming the donation adds goods "
                + "to inventory and generates a receipt."));
        VBox all = new VBox(20);
        all.put(new Cols(22, 2.4, 1).put(details, side), goods);
        return all;
    }

    private List<Donor> donorList() {
        List<Donor> donors = new ArrayList<>();
        for (DonorRepository.Row row : donorRows) {
            donors.add(row.donor);
            if (donor != null && row.donor.getDonorId().equals(donor.getDonorId())) {
                donor = row.donor;
            }
        }
        return donors;
    }

    private JComponent lineHeader() {
        Cols row = lineLayout();
        String[] titles = { "Item / match", "Category", "Quantity", "Unit", "Expiry date", "Location", "" };
        for (String title : titles) {
            row.put(UI.label(title, Theme.medium(11.5f), Theme.MUTED));
        }
        return row;
    }

    private static Cols lineLayout() {
        return new Cols(10, 2.2, 1.1, 0.9, 0.7, 1.3, 1.3, -62);
    }

    private JComponent lineRow(int index) {
        Line line = lines.get(index);
        List<Inventory> options = new ArrayList<>(items);
        options.add(CREATE);
        Dropdown<Inventory> picker = new Dropdown<>(options, item -> item == CREATE ? CREATE.getItemName()
                : item.getItemName() + " · " + item.getItemId());
        picker.placeholder("Choose item");
        picker.setSelected(line.item);
        picker.onChange(() -> {
            if (picker.getSelected() == CREATE) {
                createItem(line);
            } else {
                line.item = picker.getSelected();
                if (line.item != null && !CategoryRules.needsExpiry(line.item.getCategory())) {
                    line.expiry = "";
                }
                rebuild();
            }
        });
        Input quantity = new Input(line.quantity);
        quantity.getDocument().addDocumentListener(new SimpleListener(() -> line.quantity = quantity.getText()));
        Input expiry = new Input(line.expiry).placeholder(line.item != null && CategoryRules.needsExpiry(line.item.getCategory())
                ? "required" : "optional");
        expiry.getDocument().addDocumentListener(new SimpleListener(() -> line.expiry = expiry.getText()));
        Cols row = lineLayout();
        row.put(new Field(null, picker), UI.muted(line.item == null ? "—" : line.item.getCategory()), new Field(null, quantity),
                UI.muted(line.item == null ? "—" : line.item.getUnit()), new Field(null, expiry),
                UI.muted(line.item == null ? "—" : line.item.getStorageLocation()),
                UI.link("Remove", () -> {
                    lines.remove(index);
                    if (lines.isEmpty()) {
                        lines.add(new Line());
                    }
                    rebuild();
                }));
        return row;
    }

    private void createItem(Line line) {
        Input name = new Input("");
        Dropdown<String> category = Dropdown.of(ItemFormPage.CATEGORIES);
        Dropdown<String> unit = Dropdown.of(ItemFormPage.UNITS);
        Input threshold = new Input("0");
        Input storage = new Input("");
        VBox form = new VBox(12);
        form.put(new Cols(12, 1.4, 1).put(new Field("Item name *", name), new Field("Category *", category)),
                new Cols(12, 1, 1).put(new Field("Unit *", unit), new Field("Low-stock threshold *", threshold)),
                new Field("Storage location *", storage));
        Inventory[] created = new Inventory[1];
        boolean ok = Dialogs.form(shell, "Create new item", "The item starts with zero stock; the donation adds the goods.", form,
                "Create item", () -> {
                    try {
                        created[0] = new InventoryController().addItem(name.getText(), category.getSelected(), 0,
                                unit.getSelected().toLowerCase(), Fmt.parseCount(threshold.getText(), "Low-stock threshold"),
                                storage.getText(), null, "Created while recording a donation.", Session.workspace, Session.user);
                        return null;
                    } catch (Exception error) {
                        return Task.message(error);
                    }
                }, 620);
        if (!ok) {
            rebuild();
            return;
        }
        Task.run(() -> new InventoryController().list(Session.workspace, false, Session.user), fresh -> {
            items = fresh;
            for (Inventory item : items) {
                if (item.getItemId().equals(created[0].getItemId())) {
                    line.item = item;
                }
            }
            rebuild();
        }, error -> Dialogs.error(shell, error));
    }

    private void addDonor() {
        Input name = new Input("");
        Dropdown<String> type = Dropdown.of("Individual", "Organization", "Government agency");
        Input person = new Input("");
        Input phone = new Input("");
        Input extra = new Input("");
        VBox form = new VBox(12);
        form.put(new Field("Donor name *", name), new Cols(12, 1, 1).put(new Field("Donor type *", type),
                new Field("Contact person *", person)), new Cols(12, 1, 1).put(new Field("Contact number *", phone),
                        new Field("Email / address", extra)));
        Donor[] saved = new Donor[1];
        boolean ok = Dialogs.form(shell, "Add a new donor", "Donors can be reused across donations.", form, "Save donor", () -> {
            try {
                Donor.Type kind = type.getSelected().equals("Individual") ? Donor.Type.INDIVIDUAL
                        : type.getSelected().equals("Organization") ? Donor.Type.ORGANIZATION : Donor.Type.AGENCY;
                saved[0] = new DonationController().saveDonor(null, name.getText(), kind, person.getText(), phone.getText(),
                        extra.getText(), Session.workspace, Session.user);
                return null;
            } catch (Exception error) {
                return Task.message(error);
            }
        }, 620);
        if (ok) {
            Task.run(() -> new DonationController().donorRows(Session.workspace), rows -> {
                donorRows = rows;
                for (DonorRepository.Row row : rows) {
                    if (row.donor.getDonorId().equals(saved[0].getDonorId())) {
                        donor = row.donor;
                    }
                }
                rebuild();
            }, error -> Dialogs.error(shell, error));
        }
    }

    // ------------------------------------------------------------------ validation and saving

    private List<DonationRepository.Line> repositoryLines() {
        List<DonationRepository.Line> out = new ArrayList<>();
        for (Line line : lines) {
            if (line.item == null) {
                throw new IllegalArgumentException("Choose an item for every donation line, or remove the empty line.");
            }
            String quantity = line.quantity.trim().replace(",", "");
            if (!quantity.matches("\\d{1,9}")) {
                throw new IllegalArgumentException("Enter a whole-number quantity for " + line.item.getItemName() + ".");
            }
            out.add(new DonationRepository.Line(line.item.getItemId(), Integer.parseInt(quantity), Fmt.parseDate(line.expiry)));
        }
        return out;
    }

    private void toReview() {
        problem = null;
        try {
            LocalDate date = Fmt.parseDate(dateText);
            new DonationController().validate(donor, date, location, repositoryLines(), items);
            verified = false;
            step = 1;
        } catch (IllegalArgumentException error) {
            problem = error.getMessage();
        }
        rebuild();
    }

    private void save(DonationRepository.Mode mode) {
        problem = null;
        try {
            LocalDate date = Fmt.parseDate(dateText);
            List<DonationRepository.Line> list = repositoryLines();
            Task.run(() -> new DonationController().record(existingId, donor, Session.workspace, date, location, reference,
                    representative, notes, list, items, mode, Session.user), (Donations saved) -> {
                        if (mode == DonationRepository.Mode.POST) {
                            go(new DonationDetailPage(saved.getDonationId(), "Donation saved · inventory updated. "
                                    + describe(saved) + " This transaction is now in donation history and audit logs."));
                        } else if (mode == DonationRepository.Mode.SUBMIT) {
                            go(new DonationsPage(saved.getDonationId() + " was submitted for approval. Stock changes only after "
                                    + "a Barangay Official or Admin confirms it."));
                        } else {
                            go(new DonationsPage(saved.getDonationId() + " was saved as a draft. No stock was changed."));
                        }
                    }, error -> {
                        problem = Task.message(error);
                        step = 0;
                        rebuild();
                    });
        } catch (IllegalArgumentException error) {
            problem = error.getMessage();
            rebuild();
        }
    }

    static String describe(Donations donation) {
        List<String> parts = new ArrayList<>();
        for (TransactionItem line : donation.getItems()) {
            parts.add(Units.quantity(line.getQuantity(), line.getInventory().getUnit()) + " of "
                    + line.getInventory().getItemName().toLowerCase());
        }
        return String.join(", ", parts) + " were added.";
    }

    // ------------------------------------------------------------------ step 2

    private JComponent reviewStep() {
        LocalDate date = Fmt.parseDate(dateText);
        VBox box = new VBox(20);
        Card summary = new Card(12);
        summary.put(UI.h2("Donation summary"), UI.fact("Donor", donor.getName() + " · " + donor.getDonorId()),
                UI.fact("Type", DonationsPage.typeLabel(donor.getType())),
                UI.fact("Contact", donor.getContactPerson() + " · " + donor.getContactDetails()),
                UI.fact("Delivery slip", reference.isEmpty() ? "—" : reference));
        Card record = new Card(12);
        record.put(UI.h2("Receiving record"), UI.fact("Received date", Time.date(date)),
                UI.fact("Receiving officer", Session.canManage() ? Session.user.getName() : "Assigned when approved"),
                UI.fact("Location", location), UI.fact("Receipt reference", "Assigned on save"));
        box.put(new Cols(22, 1, 1).put(summary, record));

        Map<String, Integer> added = new LinkedHashMap<>();
        Map<String, LocalDate> expiries = new LinkedHashMap<>();
        Map<String, Inventory> byId = new LinkedHashMap<>();
        for (Line line : lines) {
            added.merge(line.item.getItemId(), Integer.parseInt(line.quantity.trim().replace(",", "")), Integer::sum);
            byId.put(line.item.getItemId(), line.item);
            LocalDate expiry = Fmt.parseDate(line.expiry);
            if (expiry != null && (expiries.get(line.item.getItemId()) == null || expiry.isBefore(expiries.get(line.item.getItemId())))) {
                expiries.put(line.item.getItemId(), expiry);
            }
        }
        DataTable table = new DataTable(new String[] { "Inventory item", "Before", "Donation", "After", "Expiry / status" },
                1.6, 1, 1, 1, 1.6);
        List<String> cleared = new ArrayList<>();
        int totalBefore = 0;
        for (Inventory item : items) {
            totalBefore += item.getQuantity();
        }
        int totalAdded = 0;
        LocalDate today = Time.today();
        for (Map.Entry<String, Integer> entry : added.entrySet()) {
            Inventory item = byId.get(entry.getKey());
            int before = item.getQuantity();
            int after = before + entry.getValue();
            totalAdded += entry.getValue();
            LocalDate expiry = expiries.get(item.getItemId());
            String status = expiry == null ? "No expiry" : Time.date(expiry);
            boolean wasLow = item.isLowStock(today);
            boolean nowLow = item.getAvailableQuantity(today) + entry.getValue() <= item.getLowStockThreshold();
            if (wasLow && !nowLow) {
                status += " · Low stock alert clears";
                cleared.add(Units.quantity(item.getAvailableQuantity(today) + entry.getValue(), item.getUnit()) + " of "
                        + item.getItemName().toLowerCase() + " will be available against a threshold of "
                        + Fmt.n(item.getLowStockThreshold()));
            } else if (nowLow) {
                status += " · Still below threshold";
            } else if (expiry != null && !expiry.isAfter(today.plusDays(StockAlerts.EXPIRING_DAYS))) {
                status += " · Expiring soon";
            } else {
                status += " · Available";
            }
            table.row(new String[] { item.getItemName(), item.getItemId() }, Units.quantity(before, item.getUnit()),
                    "+" + Units.quantity(entry.getValue(), item.getUnit()), Units.quantity(after, item.getUnit()), status);
        }
        Card impact = new Card(14);
        impact.put(UI.h2("Inventory impact"), UI.muted("Each line adds stock to the matched inventory item. Nothing is posted until confirmation."),
                table, UI.smallPara(added.size() + " existing items matched · +" + Fmt.n(totalAdded) + " recorded units"));
        if (!cleared.isEmpty()) {
            impact.put(UI.alert(Tone.TEAL, "Stock returns above threshold", String.join("; ", cleared) + ". Total recorded inventory "
                    + "increases from " + Fmt.n(totalBefore) + " to " + Fmt.n(totalBefore + totalAdded) + " mixed units."));
        } else {
            impact.put(UI.alert(Tone.INFO, "Total recorded inventory increases", "From " + Fmt.n(totalBefore) + " to "
                    + Fmt.n(totalBefore + totalAdded) + " mixed units."));
        }
        box.put(impact);

        Check verify = new Check("I verified the counted quantities, units, expiry dates and donor information.");
        verify.setSelected(verified);
        boolean approver = Session.canManage();
        Btn confirm = UI.primary(approver ? "Confirm & add to inventory" : "Submit for approval");
        confirm.setEnabled(verified);
        verify.addActionListener(event -> {
            verified = verify.isSelected();
            confirm.setEnabled(verified);
        });
        confirm.addActionListener(event -> {
            confirm.setEnabled(false);
            save(approver ? DonationRepository.Mode.POST : DonationRepository.Mode.SUBMIT);
        });
        Card actions = new Card(14);
        actions.put(verify, new Cols(14, 1, 0, 0).put(UI.smallPara(approver
                ? "Confirmation posts stock and creates a permanent donation transaction."
                : "An official must confirm this donation before stock changes."),
                UI.secondary("Back to edit", () -> {
                    step = 0;
                    rebuild();
                }), confirm));
        box.put(actions);
        return box;
    }
}
