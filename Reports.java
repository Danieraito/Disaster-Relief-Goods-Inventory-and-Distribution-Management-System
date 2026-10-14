public class Reports {
    private String reportId;
    private String reportName;
    private String generatedDate;
    private Inventory inventory;
    private Disaster disaster;
    private Distribution distribution;

    public Reports(String reportId, String reportName, String generatedDate,
            Inventory inventory, Disaster disaster, Distribution distribution) {
        this.reportId = reportId;
        this.reportName = reportName;
        this.generatedDate = generatedDate;
        this.inventory = inventory;
        this.disaster = disaster;
        this.distribution = distribution;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getReportName() {
        return reportName;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    public String getGeneratedDate() {
        return generatedDate;
    }

    public void setGeneratedDate(String generatedDate) {
        this.generatedDate = generatedDate;
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
