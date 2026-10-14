public class Reports {
    private Inventory inventory;
    private Disaster disaster;
    private Distribution distribution;

    public Reports(Inventory inventory, Disaster disaster, Distribution distribution) {
        this.inventory = inventory;
        this.disaster = disaster;
        this.distribution = distribution;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Disaster getDisaster() {
        return disaster;
    }

    public void setDisaster(Disaster disaster) {
        this.disaster = disaster;
    }

    public Distribution getDistribution() {
        return distribution;
    }

    public void setDistribution(Distribution distribution) {
        this.distribution = distribution;
    }
}
