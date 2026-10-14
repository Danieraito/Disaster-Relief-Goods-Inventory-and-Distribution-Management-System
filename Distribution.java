public class Distribution {
    private Beneficiaries beneficiary;
    private Inventory inventory;

    public Distribution(Beneficiaries beneficiary, Inventory inventory) {
        this.beneficiary = beneficiary;
        this.inventory = inventory;
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
