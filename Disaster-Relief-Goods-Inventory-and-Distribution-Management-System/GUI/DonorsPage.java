package GUI;

import GUI.Theme.Tone;
import LIB.DonationController;
import LIB.Donor;
import LIB.DonorRepository;
import LIB.Time;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/** Donor directory with the add / edit form. */
class DonorsPage extends Page {
    private List<DonorRepository.Row> donors = new ArrayList<>();
    private String search = "";
    private String type = "All donor types";
    private DonorRepository.Row selected;
    private boolean editing;
    private String message;
    private final VBox tableHolder = new VBox(0);

    @Override String module() { return "Donations"; }
    @Override String crumb() { return "Donations / Donors"; }

    @Override
    void load() throws Exception {
        donors = new DonationController().donorRows(Session.workspace);
        if (selected != null) {
            DonorRepository.Row refreshed = null;
            for (DonorRepository.Row row : donors) {
                if (row.donor.getDonorId().equals(selected.donor.getDonorId())) {
                    refreshed = row;
                }
            }
            selected = refreshed;
        }
        if (selected == null && !donors.isEmpty() && !editing) {
            selected = donors.get(0);
        }
    }

    @Override
    JComponent build() {
        VBox page = new VBox(20);
        page.put(UI.pageHeader("Donor management", "Maintain contact details and connect each donor to their donation history.",
                UI.secondary("Back to donations", () -> go(new DonationsPage()))));
        if (message != null) {
            page.put(UI.alert(Tone.TEAL, "Saved", message));
        }
        Card directory = new Card(14);
        VBox heading = new VBox(4);
        heading.put(UI.h2("Donor directory"), UI.muted(donors.size() + " donor records · contact details are shared by every receipt."));
        directory.put(heading, filters(), tableHolder);
        fillTable();
        page.put(new Cols(22, 1.8, 1).put(directory, side()));
        return page;
    }

    private JComponent filters() {
        Input box = new Input(search).placeholder("Search donor name or contact");
        box.getDocument().addDocumentListener(new SimpleListener(() -> {
            search = box.getText();
            fillTable();
        }));
        Dropdown<String> typeBox = Dropdown.of("All donor types", "Individual", "Organization", "Government agency");
        typeBox.setSelected(type);
        typeBox.onChange(() -> {
            type = typeBox.getSelected();
            fillTable();
        });
        Field typeCell = new Field(null, typeBox);
        typeCell.setPreferredSize(new java.awt.Dimension(190, 44));
        return new Cols(12, 1, 0).put(new Field(null, box), typeCell);
    }

    private void fillTable() {
        tableHolder.removeAll();
        DataTable table = new DataTable(new String[] { "Donor / ID", "Type", "Contact person", "Contact details", "Latest donation" },
                1.5, 1.1, 1.1, 1.6, 1.3);
        String needle = search.trim().toLowerCase();
        int shown = 0;
        for (DonorRepository.Row row : donors) {
            Donor donor = row.donor;
            String typeText = DonationsPage.typeLabel(donor.getType());
            if (!type.equals("All donor types") && !type.equals(typeText)
                    || !needle.isEmpty() && !(donor.getName() + " " + donor.getContactPerson() + " "
                            + donor.getContactDetails() + " " + donor.getDonorId()).toLowerCase().contains(needle)) {
                continue;
            }
            shown++;
            String[] details = donor.getContactDetails().split(" · ", 2);
            table.rowClick(() -> {
                selected = row;
                editing = false;
                message = null;
                rebuild();
            }, new String[] { donor.getName(), donor.getDonorId() }, typeText, donor.getContactPerson(), details,
                    row.latestDonationId == null ? "—" : new String[] { row.latestDonationId + " · "
                            + Time.shortDate(row.latestDonationDate) });
        }
        if (shown == 0) {
            table.empty("No donors match this view.");
        }
        table.footer("Showing " + shown + " of " + donors.size() + " donors · Select a donor to edit contact details or view donations.");
        tableHolder.put(table);
        tableHolder.revalidate();
        tableHolder.repaint();
    }

    private JComponent side() {
        VBox box = new VBox(20);
        box.put(formCard());
        if (selected != null && !editing) {
            Donor donor = selected.donor;
            Card card = new Card(12);
            card.put(UI.h2("Selected donor"), UI.strong(donor.getName()),
                    UI.left(UI.pill(DonationsPage.typeLabel(donor.getType()), Tone.NEUTRAL)),
                    UI.fact("Donor ID", donor.getDonorId()),
                    UI.fact("Latest receipt", selected.latestDonationId == null ? "—" : selected.latestDonationId),
                    UI.fact("Latest goods", selected.latestGoods.isEmpty() ? "—" : selected.latestGoods),
                    UI.actions(UI.secondary("Edit contact", () -> {
                        editing = true;
                        rebuild();
                    }), UI.secondary("View donations", () -> go(new DonationsPage(null, donor.getName())))),
                    UI.note(Tone.INFO, "Donation history stays linked", "Editing donor contact information does not change "
                            + "previously issued acknowledgments or inventory movements."));
            box.put(card);
        }
        return box;
    }

    private JComponent formCard() {
        Donor current = editing && selected != null ? selected.donor : null;
        String[] details = current == null ? new String[] { "", "" } : current.getContactDetails().split(" · ", 2);
        Input name = new Input(current == null ? "" : current.getName());
        Dropdown<String> type = Dropdown.of("Individual", "Organization", "Government agency");
        type.setSelected(current == null ? "Organization" : DonationsPage.typeLabel(current.getType()));
        Input person = new Input(current == null ? "" : current.getContactPerson());
        Input phone = new Input(details[0]);
        Input extra = new Input(details.length > 1 ? details[1] : "").placeholder("coop@example.org · Purok 3, Barangay");
        VBox error = new VBox(0);
        Btn save = UI.primary(current == null ? "Save donor" : "Save changes");
        save.addActionListener(event -> {
            save.setEnabled(false);
            error.removeAll();
            Donor.Type kind = type.getSelected().equals("Individual") ? Donor.Type.INDIVIDUAL
                    : type.getSelected().equals("Organization") ? Donor.Type.ORGANIZATION : Donor.Type.AGENCY;
            Task.run(() -> new DonationController().saveDonor(current == null ? null : current.getDonorId(), name.getText(), kind,
                    person.getText(), phone.getText(), extra.getText(), Session.workspace, Session.user), saved -> {
                        message = saved.getName() + " (" + saved.getDonorId() + ") " + (current == null ? "was added." : "was updated.");
                        editing = false;
                        selected = null;
                        reload();
                    }, failure -> {
                        save.setEnabled(true);
                        error.put(UI.alert(Tone.DANGER, "The donor was not saved", Task.message(failure)));
                        error.revalidate();
                        error.repaint();
                    });
        });
        Card card = new Card(12);
        card.put(UI.h2(current == null ? "Add donor" : "Edit donor"),
                UI.muted("A donor can be reused across multiple donation records."), new Field("Donor name *", name),
                new Field("Donor type *", type), new Field("Contact person *", person),
                new Field("Contact number *", phone), new Field("Email / address", extra), error,
                new Cols(10, 1, 0, 0).put(UI.smallPara(current == null ? "Creates a new donor ID · No stock changes"
                        : "Updates contact details only · No stock changes"), UI.secondary(current == null ? "Clear" : "Cancel", () -> {
                            editing = false;
                            rebuild();
                        }), save));
        return card;
    }
}
