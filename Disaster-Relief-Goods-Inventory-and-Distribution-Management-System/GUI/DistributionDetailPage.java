package GUI;

import GUI.Theme.Tone;
import LIB.Distribution;
import LIB.DistributionController;
import LIB.DistributionRepository;
import LIB.Inventory;
import LIB.InventoryController;
import LIB.PdfWriter;
import LIB.Time;
import LIB.TransactionItem;
import LIB.TransactionStatus;
import LIB.Units;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/** A stored release: the receipt when confirmed, the review while it waits for approval. */
class DistributionDetailPage extends Page {
    private final String distributionId;
    private String flash;
    private Distribution distribution;
    private List<DistributionRepository.Movement> movements = new ArrayList<>();
    private List<Inventory> items = new ArrayList<>();
    private Card receipt;

    DistributionDetailPage(String distributionId) {
        this(distributionId, null);
    }

    DistributionDetailPage(String distributionId, String flash) {
        this.distributionId = distributionId;
        this.flash = flash;
    }

    @Override String module() { return "Distribution"; }
    @Override String crumb() { return "Distribution / " + (distribution != null && distribution.getStatus() == TransactionStatus.APPROVED
            ? "Saved acknowledgment" : "Review"); }

    /** "2 packs, 12 liters, 1 kit released. Stock deducted by 15 recorded units." */
    static String summary(Distribution distribution) {
        List<String> parts = new ArrayList<>();
        int total = 0;
        for (TransactionItem line : distribution.getItems()) {
            parts.add(Units.quantity(line.getQuantity(), line.getInventory().getUnit()));
            total += line.getQuantity();
        }
        return String.join(", ", parts) + " released. Stock deducted by " + Fmt.n(total) + " recorded units.";
    }

    @Override
    void load() throws Exception {
        DistributionController controller = new DistributionController();
        distribution = controller.find(distributionId, Session.user);
        if (distribution == null) {
            throw new IllegalStateException("This release no longer exists.");
        }
        items = new InventoryController().list(Session.workspace, false, Session.user);
        movements = distribution.getStatus() == TransactionStatus.APPROVED ? controller.movements(distribution) : new ArrayList<>();
    }

    private boolean center() {
        return distribution.getEvacuationCenter() != null;
    }

    private String recipient() {
        return center() ? distribution.getEvacuationCenter().getName() : distribution.getBeneficiary() == null
                ? distribution.getReleaseLocation() : distribution.getBeneficiary().getName() + " household";
    }

    @Override
    JComponent build() {
        boolean posted = distribution.getStatus() == TransactionStatus.APPROVED;
        VBox page = new VBox(20);
        Row buttons = new Row(10);
        if (posted) {
            buttons.put(UI.secondary("Download PDF", this::downloadPdf), UI.secondary(center() ? "Print center receipt" : "Print receipt",
                    () -> Printing.print(shell, receipt, "Release " + distributionId)));
        }
        page.put(UI.pageHeader(posted ? (center() ? "Evacuation center delivery confirmed" : "Household release confirmed")
                : "Release " + distributionId, distributionId + " · " + (posted ? "Receipt and stock movement" : statusText()), buttons));
        page.put(DisasterBand.of(this, distribution.getDisaster(), java.util.Collections.singletonList(distribution.getDisaster())));
        if (posted && flash != null) {
            page.put(UI.alert(Tone.TEAL, "Goods released · " + recipient() + " receipt recorded", "Inventory deducted successfully. "
                    + (center() ? "No household assistance status was updated from this center delivery."
                            : "The household is now marked as assisted for this disaster.")));
        } else if (flash != null) {
            page.put(UI.alert(Tone.TEAL, "Saved", flash));
        }
        if (!posted) {
            page.put(statusBanner());
        }
        page.put(posted ? receiptView() : reviewView());
        page.put(UI.actions(UI.secondary("Back to distribution", () -> go(new DistributionPage()))));
        return page;
    }

    private String statusText() {
        switch (distribution.getStatus()) {
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

    private JComponent statusBanner() {
        switch (distribution.getStatus()) {
            case PENDING: {
                if (!Session.user.canApproveDistributions()) {
                    return UI.alert(Tone.WARN, "Waiting for approval", "A Barangay Official or Admin must confirm this release before "
                            + "stock is deducted. Prepared by " + distribution.getCreatedByName() + ".");
                }
                Btn approve = UI.primary("Approve & issue receipt");
                approve.addActionListener(event -> {
                    approve.setEnabled(false);
                    Task.run(() -> new DistributionController().approve(distributionId, Session.user), saved -> {
                        flash = saved.getDistributionId() + " confirmed · " + summary(saved);
                        reload();
                    }, error -> {
                        approve.setEnabled(true);
                        Dialogs.error(shell, error);
                    });
                });
                Btn reject = UI.danger("Reject");
                reject.addActionListener(event -> reject());
                return UI.alert(Tone.WARN, "Waiting for your approval", "Prepared by " + distribution.getCreatedByName()
                        + ". Stock is deducted only when you approve.", UI.actions(reject, approve));
            }
            case DRAFT: {
                Btn submit = UI.primary("Submit for approval");
                submit.addActionListener(event -> Task.run(() -> new DistributionController().submitDraft(distributionId, Session.user),
                        this::reload, error -> Dialogs.error(shell, error)));
                return UI.alert(Tone.INFO, "Draft", "This release has not been submitted. No stock was changed.", submit);
            }
            default:
                return UI.alert(Tone.DANGER, "Release rejected", distribution.getRejectionReason().isEmpty()
                        ? "This release was rejected. No stock was deducted." : distribution.getRejectionReason());
        }
    }

    private void reject() {
        Area reason = new Area("", 3);
        VBox form = new VBox(10);
        form.put(new Field("Reason for rejecting *", reason));
        boolean ok = Dialogs.form(shell, "Reject this release?", "Stock stays unchanged and the reason is kept in the audit log.",
                form, "Reject release", () -> {
                    if (reason.getText().trim().isEmpty()) {
                        return "Enter the reason for rejecting this release.";
                    }
                    try {
                        new DistributionController().reject(distributionId, reason.getText(), Session.user);
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
        Card info = new Card(12);
        info.put(UI.h2(center() ? "Evacuation center & household representative" : "Household"),
                UI.fact(center() ? "Evacuation center" : "Recipient", recipient()),
                UI.fact("Household representative", distribution.getBeneficiary() == null ? "—" : distribution.getBeneficiary().getName()),
                UI.fact("Disaster", distribution.getDisaster().getName()));
        Card record = new Card(12);
        record.put(UI.h2("Release record"), UI.fact("Distribution date", Time.date(LocalDate.parse(distribution.getDistributionDate()))),
                UI.fact("Prepared by", distribution.getCreatedByName().isEmpty() ? "—" : distribution.getCreatedByName()),
                UI.fact("Acknowledgment", distribution.getAcknowledgmentMethod().isEmpty() ? "—" : distribution.getAcknowledgmentMethod()),
                UI.fact("Reference", distribution.getReceiptReference()));
        DataTable table = new DataTable(new String[] { "Relief item", "Quantity", "Batch" }, 1.8, 1, 1.6);
        for (TransactionItem line : distribution.getItems()) {
            table.row(line.getInventory().getItemName(), Units.quantity(line.getQuantity(), line.getInventory().getUnit()),
                    line.getBatchReference());
        }
        Card lines = new Card(14);
        lines.put(UI.h2("Items"), table);
        box.put(new Cols(22, 1, 1).put(info, record), lines);
        return box;
    }

    private JComponent receiptView() {
        receipt = new Card(16);
        receipt.pad(30, 34, 30, 34);
        Cols head = new Cols(12, 1, 0);
        VBox title = new VBox(4);
        title.put(UI.label("Barangay " + Session.workspace.getName() + " · " + (center() ? "Evacuation center receipt" : "Household claim receipt"),
                Theme.semibold(18), Theme.TEXT), UI.muted("Receipt " + distribution.getReceiptReference()));
        head.put(title, UI.pill(center() ? "Center receipt recorded" : "Claim recorded", Tone.TEAL));
        String officer = distribution.getReleasedBy() == null ? "—" : distribution.getReleasedBy().getName();
        VBox left = new VBox(7);
        left.put(UI.fact("Distribution ID", distributionId), UI.fact(center() ? "Delivery location" : "Recipient", recipient()),
                UI.fact("Household representative", distribution.getBeneficiary() == null ? "—" : distribution.getBeneficiary().getName()),
                UI.fact("Barangay", "Barangay " + Session.workspace.getName()));
        VBox right = new VBox(7);
        right.put(UI.fact("Disaster", distribution.getDisaster().getName() + " · " + distribution.getDisaster().getDisasterId()),
                UI.fact("Released", Time.longStamp(distribution.getApprovedAt())), UI.fact("Releasing officer", officer),
                UI.fact("Delivery status", center() ? "Center receipt recorded" : "Claim recorded"));
        DataTable table = new DataTable(new String[] { "Relief item", "Quantity received", "Batch / expiry", "Stock before → after" },
                1.6, 1.1, 1.8, 1.6);
        for (DistributionRepository.Movement movement : movements) {
            table.row(movement.itemName, Units.quantity(movement.quantity, movement.unit), movement.batchId
                    + (movement.expiry == null ? " · No expiry" : " · " + Time.date(movement.expiry)),
                    Fmt.n(movement.before) + " → " + Fmt.n(movement.after) + " " + Units.plural(movement.after, movement.unit));
        }
        String representative = distribution.getBeneficiary() == null ? "the recipient" : distribution.getBeneficiary().getName();
        receipt.put(head, new Cols(30, 1, 1).put(left, right), table);
        receipt.put(UI.para("I acknowledge receipt of the goods listed above for " + recipient() + " under "
                + distribution.getDisaster().getName() + "."));
        Card acknowledgment = new Card(6);
        acknowledgment.pad(18, 22, 18, 22);
        acknowledgment.put(UI.h3(center() ? "Household representative acknowledgment" : "Household acknowledgment"), UI.strong(representative),
                UI.small((distribution.getAcknowledgmentMethod().isEmpty() ? "Signed paper receipt" : distribution.getAcknowledgmentMethod())
                        + " · " + distribution.getReceiptReference()), UI.small("Signature record filed " + Time.longStamp(distribution.getApprovedAt())));
        Card released = new Card(6);
        released.pad(18, 22, 18, 22);
        released.put(UI.h3("Released by"), UI.strong(officer + " · " + (distribution.getReleasedBy() == null ? ""
                : distribution.getReleasedBy().getRoleLabel())), UI.small("Goods count verified"),
                UI.small("Recorded " + Time.longStamp(distribution.getApprovedAt())));
        receipt.put(UI.gap(6), new Cols(18, 1, 1).put(acknowledgment, released));

        VBox box = new VBox(20);
        box.put(receipt);
        LocalDate today = Time.today();
        List<String> fresh = new ArrayList<>();
        List<String> existing = new ArrayList<>();
        for (Inventory item : items) {
            boolean inRelease = false;
            for (TransactionItem line : distribution.getItems()) {
                if (line.getInventory().getItemId().equals(item.getItemId())) {
                    inRelease = true;
                }
            }
            if (inRelease && item.isLowStock(today)) {
                fresh.add(item.getItemName() + ": " + Fmt.n(item.getAvailableQuantity(today)) + " available against a threshold of "
                        + Fmt.n(item.getLowStockThreshold()));
            } else if (!inRelease && item.isLowStock(today)) {
                existing.add(item.getItemName() + " has " + Units.quantity(item.getAvailableQuantity(today), item.getUnit())
                        + " usable against a threshold of " + Fmt.n(item.getLowStockThreshold()));
            }
        }
        if (fresh.isEmpty()) {
            box.put(UI.alert(Tone.TEAL, "No new low stock alerts triggered", "Items in this release are still above their thresholds."));
        } else {
            box.put(UI.alert(Tone.WARN, "Low stock alert after this release", String.join("; ", fresh) + ". Request replenishment."));
        }
        if (!existing.isEmpty()) {
            box.put(UI.alert(Tone.INFO, "Existing inventory alerts remain", String.join("; ", existing) + ". This release did not change those items."));
        }
        return box;
    }

    PdfWriter buildPdf() {
        PdfWriter pdf = new PdfWriter(false).brand("BarangayCare", Exports.workspaceLine())
                .title(center() ? "Evacuation center receipt" : "Household claim receipt")
                .subtitle("Receipt " + distribution.getReceiptReference() + " · " + distributionId);
        String officer = distribution.getReleasedBy() == null ? "—" : distribution.getReleasedBy().getName();
        List<String[]> facts = new ArrayList<>();
        facts.add(new String[] { "Distribution ID", distributionId });
        facts.add(new String[] { "Disaster", distribution.getDisaster().getName() });
        facts.add(new String[] { center() ? "Delivery location" : "Recipient", recipient() });
        facts.add(new String[] { "Household representative", distribution.getBeneficiary() == null ? "—" : distribution.getBeneficiary().getName() });
        facts.add(new String[] { "Released", Time.longStamp(distribution.getApprovedAt()) });
        facts.add(new String[] { "Releasing officer", officer });
        pdf.keyValues(facts).gap(6);
        List<List<String>> rows = new ArrayList<>();
        for (DistributionRepository.Movement movement : movements) {
            List<String> row = new ArrayList<>();
            row.add(movement.itemName);
            row.add(Units.quantity(movement.quantity, movement.unit));
            row.add(movement.batchId + (movement.expiry == null ? " - No expiry" : " - " + Time.date(movement.expiry)));
            row.add(Fmt.n(movement.before) + " -> " + Fmt.n(movement.after));
            rows.add(row);
        }
        pdf.table(java.util.Arrays.asList("Relief item", "Quantity received", "Batch / expiry", "Stock before -> after"), rows);
        pdf.paragraph("I acknowledge receipt of the goods listed above for " + recipient() + " under "
                + distribution.getDisaster().getName() + ".");
        pdf.signatures("Household representative acknowledgment", distribution.getBeneficiary() == null ? "—"
                : distribution.getBeneficiary().getName(), distribution.getReceiptReference(), "Released by", officer,
                "Recorded " + Time.longStamp(distribution.getApprovedAt()));
        pdf.footer("Restricted barangay records - handle household information with care");
        return pdf;
    }

    private void downloadPdf() {
        Exports.savePdf(shell, "Receipt " + distribution.getReceiptReference(), buildPdf());
    }
}
