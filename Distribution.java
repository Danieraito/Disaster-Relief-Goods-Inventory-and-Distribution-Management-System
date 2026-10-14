public class Distribution {
    private String distributionId;
    private int quantity;
    private String distributionDate;
    private Beneficiaries beneficiary;
    private Inventory inventory;

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
}
