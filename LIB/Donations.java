package LIB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Donations implements BarangayScoped {
    private String donationId;
    private Donor donor;
    private String donationDate;
    private String receivingLocation;
    private String deliveryReference;
    private UserAccount receivedBy;
    private final List<TransactionItem> items = new ArrayList<>();
    private TransactionStatus status = TransactionStatus.DRAFT;

    public Donations(String donationId, Donor donor, String donationDate,
            String receivingLocation, String deliveryReference) {
        this.donationId = donationId;
        this.donor = donor;
        this.donationDate = donationDate;
        this.receivingLocation = receivingLocation;
        this.deliveryReference = deliveryReference;
    }

    public String getDonationId() {
        return donationId;
    }

    @Override
    public Barangay getBarangay() {
        return items.isEmpty() ? null : items.get(0).getInventory().getBarangay();
    }

    public void setDonationId(String donationId) {
        requireDraft();
        this.donationId = donationId;
    }

    public Donor getDonor() {
        return donor;
    }

    public void setDonor(Donor donor) {
        requireDraft();
        this.donor = donor;
    }

    public String getDonationDate() {
        return donationDate;
    }

    public void setDonationDate(String donationDate) {
        requireDraft();
        this.donationDate = donationDate;
    }

    public String getReceivingLocation() {
        return receivingLocation;
    }

    public void setReceivingLocation(String receivingLocation) {
        requireDraft();
        this.receivingLocation = receivingLocation;
    }

    public String getDeliveryReference() {
        return deliveryReference;
    }

    public void setDeliveryReference(String deliveryReference) {
        requireDraft();
        this.deliveryReference = deliveryReference;
    }

    public UserAccount getReceivedBy() {
        return receivedBy;
    }

    public List<TransactionItem> getItems() {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    public void addItem(TransactionItem item) {
        requireDraft();
        items.add(Objects.requireNonNull(item, "item"));
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Donations submit(UserAccount submitter) {
        requirePermission(submitter, submitter != null && submitter.isActive() && submitter.canEnterRecords(),
                "User does not have permission to submit donations.");
        validate();
        requireWorkspaceAccess(submitter);
        requireDraft();
        status = TransactionStatus.PENDING;
        return this;
    }

    public void approve(UserAccount approver) {
        requirePermission(approver, approver != null && approver.isActive() && approver.canManageRecords(),
                "User does not have permission to approve donations.");
        validate();
        requireWorkspaceAccess(approver);
        requirePending();

        for (TransactionItem item : items) {
            if (isFirstOccurrence(item)) {
                item.getInventory().validateStockAddition(totalQuantityFor(item.getInventory()));
            }
        }
        for (int index = 0; index < items.size(); index++) {
            TransactionItem item = items.get(index);
            String batchId = resolveBatchId(item, index);
            item.getInventory().validateBatchAddition(batchId, item.getQuantity());
            for (int previousIndex = 0; previousIndex < index; previousIndex++) {
                TransactionItem previous = items.get(previousIndex);
                if (previous.getInventory() == item.getInventory()
                        && resolveBatchId(previous, previousIndex).equals(batchId)) {
                    throw new IllegalArgumentException("Donation contains duplicate batch references.");
                }
            }
        }
        for (int index = 0; index < items.size(); index++) {
            TransactionItem item = items.get(index);
            item.getInventory().addBatch(
                    resolveBatchId(item, index),
                    item.getQuantity(),
                    donationDate,
                    item.getExpiryDate());
        }

        receivedBy = approver;
        status = TransactionStatus.APPROVED;
    }

    public void reject(UserAccount approver) {
        requirePermission(approver, approver != null && approver.isActive() && approver.canManageRecords(),
                "User does not have permission to reject donations.");
        requireWorkspaceAccess(approver);
        requirePending();
        status = TransactionStatus.REJECTED;
    }

    private void validate() {
        if (donor == null) {
            throw new IllegalArgumentException("Donation must be linked to a donor.");
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Donation must contain at least one item.");
        }
        for (TransactionItem item : items) {
            if (!items.get(0).getInventory().sharesBarangayWith(item.getInventory())) {
                throw new IllegalArgumentException("A donation cannot combine inventory from different barangays.");
            }
        }
    }

    private String resolveBatchId(TransactionItem item, int lineNumber) {
        String batchReference = item.getBatchReference();
        if (batchReference == null || batchReference.trim().isEmpty()) {
            return donationId + "-" + (lineNumber + 1);
        }
        return batchReference.trim();
    }

    private int totalQuantityFor(Inventory inventory) {
        int total = 0;
        for (TransactionItem item : items) {
            if (item.getInventory() == inventory) {
                total = Math.addExact(total, item.getQuantity());
            }
        }
        return total;
    }

    private void requireWorkspaceAccess(UserAccount account) {
        if (!account.canAccess(this)) {
            throw new SecurityException("User cannot access this donation's barangay workspace.");
        }
    }

    private boolean isFirstOccurrence(TransactionItem candidate) {
        for (TransactionItem item : items) {
            if (item == candidate) {
                return true;
            }
            if (item.getInventory() == candidate.getInventory()) {
                return false;
            }
        }
        return false;
    }

    private void requireDraft() {
        if (status != TransactionStatus.DRAFT) {
            throw new IllegalStateException("Only draft donations can be changed.");
        }
    }

    private void requirePending() {
        if (status != TransactionStatus.PENDING) {
            throw new IllegalStateException("Only pending donations can be approved or rejected.");
        }
    }

    private void requirePermission(UserAccount user, boolean permitted, String message) {
        if (user == null || !permitted) {
            throw new SecurityException(message);
        }
    }
}
