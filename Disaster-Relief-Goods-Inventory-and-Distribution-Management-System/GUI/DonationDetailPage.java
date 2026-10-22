package GUI;

import GUI.Theme.Tone;
import LIB.DonationController;
import LIB.DonationRepository;
import LIB.Donations;
import LIB.PdfWriter;
import LIB.Time;
import LIB.TransactionItem;
import LIB.TransactionStatus;
import LIB.Units;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/** A stored donation: the receipt when confirmed, the review screen while it waits for approval. */
class DonationDetailPage extends Page {
    private final String donationId;
    private String flash;
    private Donations donation;
    private List<DonationRepository.Movement> movements = new ArrayList<>();
    private Card receipt;

    DonationDetailPage(String donationId, String flash) {
        this.donationId = donationId;
        this.flash = flash;
    }

    @Override String module() { return "Donations"; }
    @Override String crumb() { return "Donations / " + (donation != null && donation.getStatus() == TransactionStatus.APPROVED
            ? "Saved receipt" : "Review"); }

    @Override
    void load() throws Exception {
        DonationController controller = new DonationController();
        donation = controller.find(donationId, Session.user);
        if (donation == null) {
            throw new IllegalStateException("This donation no longer exists.");
        }
        movements = donation.getStatus() == TransactionStatus.APPROVED ? controller.movements(donation) : new ArrayList<>();
    }

    @Override
    JComponent build() {
        boolean posted = donation.getStatus() == TransactionStatus.APPROVED;
        VBox page = new VBox(20);
        Row buttons = new Row(10);
        if (posted) {
            buttons.put(UI.secondary("Download PDF", this::downloadPdf), UI.secondary("Print receipt", () -> Printing.print(shell,
                    receipt, "Donation " + donationId)));
        }
        page.put(UI.pageHeader(posted ? "Donation acknowledgment" : "Donation " + donationId, donationId
                + (posted ? " · Saved transaction and receiving slip" : " · " + statusText()), buttons));
        if (flash != null) {
            page.put(UI.alert(Tone.TEAL, "Donation saved · inventory updated", flash.replaceFirst("^Donation saved · inventory updated\\. ", "")));
        }
        if (!posted) {
            page.put(pendingBanner());
        }
        page.put(posted ? receiptView() : reviewView());
        page.put(UI.actions(UI.secondary("Back to donations", () -> go(new DonationsPage()))));
        return page;
    }

    private String statusText() {
        switch (donation.getStatus()) {
            case PENDING:
                return "Waiting for approval";
            case DRAFT:
                return "Draft";
            case REJECTED:
                return "Rejected";
            default:
                return "Confirmed";
        }
    }

    private JComponent pendingBanner() {
        switch (donation.getStatus()) {
            case PENDING: {
                if (!Session.canManage()) {
                    return UI.alert(Tone.WARN, "Waiting for approval", "A Barangay Official or Admin must confirm this donation "
                            + "before stock changes. Submitted by " + donation.getCreatedByName() + ".");
                }
                Btn approve = UI.primary("Approve & add to inventory");
                approve.addActionListener(event -> {
                    approve.setEnabled(false);
                    Task.run(() -> new DonationController().approve(donationId, Session.user), saved -> {
                        flash = "Donation saved · inventory updated. " + DonationFormPage.describe(saved)
                                + " This transaction is now in donation history and audit logs.";
                        reload();
                    }, error -> {
                        approve.setEnabled(true);
                        Dialogs.error(shell, error);
                    });
                });
                Btn reject = UI.danger("Reject");
                reject.addActionListener(event -> reject());
                return UI.alert(Tone.WARN, "Waiting for your approval", "Submitted by " + donation.getCreatedByName()
                        + ". Stock increases only when you approve.", UI.actions(reject, approve));
            }
            case DRAFT: {
                Btn submit = UI.primary(Session.canManage() ? "Review & post" : "Submit for approval");
                submit.addActionListener(event -> {
                    if (Session.canManage()) {
                        go(new DonationFormPage(donationId));
                    } else {
                        Task.run(() -> new DonationController().submitDraft(donationId, Session.user),
                                () -> go(new DonationsPage(donationId + " was submitted for approval.")),
                                error -> Dialogs.error(shell, error));
                    }
                });
                return UI.alert(Tone.INFO, "Draft", "This donation has not been submitted. No stock was changed.",
                        UI.actions(UI.secondary("Edit draft", () -> go(new DonationFormPage(donationId))), submit));
            }
            default:
                return UI.alert(Tone.DANGER, "Donation rejected", donation.getRejectionReason().isEmpty()
                        ? "This donation was rejected. No stock was added." : donation.getRejectionReason());
        }
    }

    private void reject() {
        Area reason = new Area("", 3);
        VBox form = new VBox(10);
        form.put(new Field("Reason for rejecting *", reason));
        boolean ok = Dialogs.form(shell, "Reject this donation?", "Stock stays unchanged and the reason is kept in the audit log.",
                form, "Reject donation", () -> {
                    if (reason.getText().trim().isEmpty()) {
                        return "Enter the reason for rejecting this donation.";
                    }
                    try {
                        new DonationController().reject(donationId, reason.getText(), Session.user);
                        return null;
                    } catch (Exception error) {
                        return Task.message(error);
                    }
                }, 560);
        if (ok) {
            reload();
        }
    }

    // ------------------------------------------------------------------ views

    private JComponent reviewView() {
        VBox box = new VBox(20);
        Card summary = new Card(12);
        summary.put(UI.h2("Donation summary"), UI.fact("Donor", donation.getDonor().getName()),
                UI.fact("Type", DonationsPage.typeLabel(donation.getDonor().getType())),
                UI.fact("Contact", donation.getDonor().getContactPerson() + " · " + donation.getDonor().getContactDetails()),
                UI.fact("Delivery slip", donation.getDeliveryReference().isEmpty() ? "—" : donation.getDeliveryReference()));
        Card record = new Card(12);
        record.put(UI.h2("Receiving record"), UI.fact("Received date", Time.date(LocalDate.parse(donation.getDonationDate()))),
                UI.fact("Recorded by", donation.getCreatedByName().isEmpty() ? "—" : donation.getCreatedByName()),
                UI.fact("Location", donation.getReceivingLocation()), UI.fact("Notes", donation.getNotes().isEmpty() ? "—" : donation.getNotes()));
        box.put(new Cols(22, 1, 1).put(summary, record), itemsTable("Donated items"));
        return box;
    }

    private Card itemsTable(String title) {
        DataTable table = new DataTable(new String[] { "Donated item", "Category", "Received quantity", "Expiry", "Storage" },
                1.6, 1, 1.2, 1.2, 1.2);
        for (TransactionItem line : donation.getItems()) {
            table.row(new String[] { line.getInventory().getItemName(), line.getInventory().getItemId() },
                    line.getInventory().getCategory(), Units.quantity(line.getQuantity(), line.getInventory().getUnit()),
                    line.getExpiryDate() == null || line.getExpiryDate().isEmpty() ? "Not applicable"
                            : Time.date(LocalDate.parse(line.getExpiryDate())), line.getInventory().getStorageLocation());
        }
        Card card = new Card(14);
        card.put(UI.h2(title), table);
        return card;
    }

    private JComponent receiptView() {
        receipt = new Card(16);
        receipt.pad(30, 34, 30, 34);
        String donorRep = donation.getDonorRepresentative().isEmpty() ? donation.getDonor().getContactPerson()
                : donation.getDonorRepresentative();
        Cols head = new Cols(12, 1, 0);
        VBox title = new VBox(4);
        title.put(UI.label("Barangay " + Session.workspace.getName() + " · Donation receiving slip", Theme.semibold(18), Theme.TEXT),
                UI.muted(Session.workspace.getMunicipality() + ", " + Session.workspace.getProvince() + ", Philippines"));
        head.put(title, UI.pill("Posted", Tone.TEAL));
        VBox left = new VBox(7);
        left.put(UI.fact("Receipt number", donation.getDonationId()), UI.fact("Received", Time.longStamp(donation.getApprovedAt() != null
                ? donation.getApprovedAt() : LocalDate.parse(donation.getDonationDate()).atStartOfDay())),
                UI.fact("Donor", donation.getDonor().getName()), UI.fact("Donor type", DonationsPage.typeLabel(donation.getDonor().getType())));
        VBox right = new VBox(7);
        right.put(UI.fact("Receiving officer", donation.getReceivedBy() == null ? "—" : donation.getReceivedBy().getName()),
                UI.fact("Donor representative", donorRep), UI.fact("Contact", donation.getDonor().getContactDetails()),
                UI.fact("Delivery reference", donation.getDeliveryReference().isEmpty() ? "—" : donation.getDeliveryReference()));
        receipt.put(head, new Cols(30, 1, 1).put(left, right), itemsTable("Items received").getComponent(1));
        receipt.put(UI.para("Received in good condition for barangay relief operations. This slip acknowledges goods received; it "
                + "is not a valuation or tax receipt."));
        Card donorSign = new Card(6);
        donorSign.pad(18, 22, 18, 22);
        donorSign.put(UI.h3("Donor acknowledgment"), UI.strong(donorRep + " · " + donation.getDonor().getName()),
                UI.small("Signed paper copy · filed " + (donation.getDeliveryReference().isEmpty() ? donation.getDonationId()
                        : donation.getDeliveryReference())));
        Card officerSign = new Card(6);
        officerSign.pad(18, 22, 18, 22);
        officerSign.put(UI.h3("Received & verified by"), UI.strong((donation.getReceivedBy() == null ? "—"
                : donation.getReceivedBy().getName()) + " · " + (donation.getReceivedBy() == null ? "" : donation.getReceivedBy().getRoleLabel())),
                UI.small("Recorded " + Time.longStamp(donation.getApprovedAt())));
        receipt.put(UI.gap(6), new Cols(18, 1, 1).put(donorSign, officerSign));

        DataTable moves = new DataTable(new String[] { "Item ID", "Before", "Added", "After" }, 1.2, 1, 1, 1);
        for (DonationRepository.Movement movement : movements) {
            moves.row(movement.itemId, Units.quantity(movement.before, movement.unit), "+" + Units.quantity(movement.added, movement.unit),
                    Units.quantity(movement.after, movement.unit));
        }
        Card posted = new Card(14);
        posted.put(UI.h2("Posted inventory movements"), UI.muted("Linked directly to this donation's audit record."), moves);
        VBox box = new VBox(20);
        box.put(receipt, posted);
        return box;
    }

    PdfWriter buildPdf() {
        PdfWriter pdf = new PdfWriter(false).brand("BarangayCare", Exports.workspaceLine()).title("Donation receiving slip")
                .subtitle(donation.getDonationId() + " · Posted");
        String officer = donation.getReceivedBy() == null ? "—" : donation.getReceivedBy().getName();
        List<String[]> facts = new ArrayList<>();
        facts.add(new String[] { "Receipt number", donation.getDonationId() });
        facts.add(new String[] { "Received", Time.longStamp(donation.getApprovedAt()) });
        facts.add(new String[] { "Donor", donation.getDonor().getName() });
        facts.add(new String[] { "Donor type", DonationsPage.typeLabel(donation.getDonor().getType()) });
        facts.add(new String[] { "Receiving officer", officer });
        facts.add(new String[] { "Donor representative", donation.getDonorRepresentative().isEmpty()
                ? donation.getDonor().getContactPerson() : donation.getDonorRepresentative() });
        facts.add(new String[] { "Contact", donation.getDonor().getContactDetails() });
        facts.add(new String[] { "Delivery reference", donation.getDeliveryReference().isEmpty() ? "—" : donation.getDeliveryReference() });
        pdf.keyValues(facts).gap(6);
        List<List<String>> rows = new ArrayList<>();
        for (TransactionItem line : donation.getItems()) {
            List<String> row = new ArrayList<>();
            row.add(line.getInventory().getItemName());
            row.add(line.getInventory().getCategory());
            row.add(Units.quantity(line.getQuantity(), line.getInventory().getUnit()));
            row.add(line.getExpiryDate() == null || line.getExpiryDate().isEmpty() ? "Not applicable"
                    : Time.date(LocalDate.parse(line.getExpiryDate())));
            row.add(line.getInventory().getStorageLocation());
            rows.add(row);
        }
        pdf.table(java.util.Arrays.asList("Donated item", "Category", "Received quantity", "Expiry", "Storage"), rows);
        pdf.paragraph("Received in good condition for barangay relief operations. This slip acknowledges goods received; it is "
                + "not a valuation or tax receipt.");
        pdf.signatures("Donor acknowledgment", donation.getDonor().getContactPerson() + " · " + donation.getDonor().getName(),
                "Signed paper copy", "Received & verified by", officer, "Recorded " + Time.longStamp(donation.getApprovedAt()));
        pdf.footer("Restricted barangay records - handle donor information with care");
        return pdf;
    }

    private void downloadPdf() {
        Exports.savePdf(shell, "Donation " + donation.getDonationId(), buildPdf());
    }
}
