public class ReportController {
    public Reports createReportRecord(String reportId, String reportName, String generatedDate,
            Inventory inventory, Disaster disaster, Distribution distribution) {
        return new Reports(reportId, reportName, generatedDate, inventory, disaster, distribution);
    }
}
