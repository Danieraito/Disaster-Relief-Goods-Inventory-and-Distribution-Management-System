public class Donations {
    private String donationId;
    private String donorName;
    private int quantity;
    private String donationDate;
    private Inventory inventory;

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
}
