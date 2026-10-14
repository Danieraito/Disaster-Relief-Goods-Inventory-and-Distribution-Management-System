public class Distribution {
    private String distributionId;
    private int quantity;
    private String distributionDate;
    private Beneficiaries beneficiary;
    private Inventory inventory;
    private TransactionStatus status = TransactionStatus.DRAFT;

    public Distribution(String distributionId, int quantity, String distributionDate,
            Beneficiaries beneficiary, Inventory inventory) {
        this.distributionId = distributionId;
        this.quantity = quantity;
        this.distributionDate = distributionDate;
        this.beneficiary = beneficiary;
        this.inventory = inventory;
    }

    public String getDistributionId() {
        return distributionId;
    }

    public void setDistributionId(String distributionId) {
        this.distributionId = distributionId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getDistributionDate() {
        return distributionDate;
    }

    public void setDistributionDate(String distributionDate) {
        this.distributionDate = distributionDate;
    }

    public Beneficiaries getBeneficiary() {
        return beneficiary;
    }

    public void setBeneficiary(Beneficiaries beneficiary) {
        this.beneficiary = beneficiary;
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

    public Distribution submit(UserAccount submitter) {
        requirePermission(submitter, submitter != null && submitter.canEnterRecords(),
                "User does not have permission to submit distributions.");
        validate();
        requireDraft();
        return this;
    }

    public int approve(UserAccount approver) {
        requirePermission(approver, approver != null && approver.canApproveDistributions(),
                "User does not have permission to approve distributions.");
        validate();
        requireDraft();

        inventory.removeStock(quantity);
        status = TransactionStatus.APPROVED;
        return inventory.getQuantity();
    }

    public void reject(UserAccount approver) {
        requirePermission(approver, approver != null && approver.canApproveDistributions(),
                "User does not have permission to reject distributions.");
        requireDraft();
        status = TransactionStatus.REJECTED;
    }

    private void validate() {
        if (inventory == null) {
            throw new IllegalArgumentException("Distribution must be linked to an inventory item.");
        }
        if (beneficiary == null) {
            throw new IllegalArgumentException("Distribution must be linked to a beneficiary.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Distribution quantity must be positive.");
        }
    }

    private void requireDraft() {
        if (status != TransactionStatus.DRAFT) {
            throw new IllegalStateException("Only draft distributions can be approved or rejected.");
        }
    }

    private void requirePermission(UserAccount user, boolean permitted, String message) {
        if (user == null || !permitted) {
            throw new SecurityException(message);
        }
    }
}
