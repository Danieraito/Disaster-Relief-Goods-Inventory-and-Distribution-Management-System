public class Donations {
    private String donationId;
    private String donorName;
    private int quantity;
    private String donationDate;
    private Inventory inventory;
    private TransactionStatus status = TransactionStatus.DRAFT;

    public Donations(String donationId, String donorName, int quantity, String donationDate, Inventory inventory) {
        this.donationId = donationId;
        this.donorName = donorName;
        this.quantity = quantity;
        this.donationDate = donationDate;
        this.inventory = inventory;
    }

    public String getDonationId() {
        return donationId;
    }

    public void setDonationId(String donationId) {
        this.donationId = donationId;
    }

    public String getDonorName() {
        return donorName;
    }

    public void setDonorName(String donorName) {
        this.donorName = donorName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getDonationDate() {
        return donationDate;
    }

    public void setDonationDate(String donationDate) {
        this.donationDate = donationDate;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Donations submit(UserAccount submitter) {
        requirePermission(submitter, submitter != null && submitter.canEnterRecords(),
                "User does not have permission to submit donations.");
        validate();
        requireDraft();
        return this;
    }

    public int approve(UserAccount approver) {
        requirePermission(approver, approver != null && approver.canManageRecords(),
                "User does not have permission to approve donations.");
        validate();
        requireDraft();

        inventory.addStock(quantity);
        status = TransactionStatus.APPROVED;
        return inventory.getQuantity();
    }

    public void reject(UserAccount approver) {
        requirePermission(approver, approver != null && approver.canManageRecords(),
                "User does not have permission to reject donations.");
        requireDraft();
        status = TransactionStatus.REJECTED;
    }

    private void validate() {
        if (inventory == null) {
            throw new IllegalArgumentException("Donation must be linked to an inventory item.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Donation quantity must be positive.");
        }
    }

    private void requireDraft() {
        if (status != TransactionStatus.DRAFT) {
            throw new IllegalStateException("Only draft donations can be approved or rejected.");
        }
    }

    private void requirePermission(UserAccount user, boolean permitted, String message) {
        if (user == null || !permitted) {
            throw new SecurityException(message);
        }
    }
}
