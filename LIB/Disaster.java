package LIB;

public class Disaster implements BarangayScoped {
    private String disasterId;
    private String name;
    private String type;
    private String location;
    private String date;
    private String status;
    private String severity;
    private String affectedAreas;
    private final Barangay barangay;

    public Disaster(String disasterId, String name, String type, String location, String date, String status) {
        this(disasterId, name, type, location, date, status, null);
    }

    public Disaster(String disasterId, String name, String type, String location, String date,
            String status, Barangay barangay) {
        this.disasterId = disasterId;
        this.name = name;
        this.type = type;
        this.location = location;
        this.date = date;
        this.status = status;
        this.barangay = barangay;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getAffectedAreas() {
        return affectedAreas;
    }

    public void setAffectedAreas(String affectedAreas) {
        this.affectedAreas = affectedAreas;
    }

    public String getDisasterId() {
        return disasterId;
    }

    public Barangay getBarangay() {
        return barangay;
    }

    public void setDisasterId(String disasterId) {
        this.disasterId = disasterId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /** Every status except CLOSED counts as an active response (older records use ONGOING). */
    public boolean isActive() {
        return status == null || !status.trim().equalsIgnoreCase("CLOSED");
    }
}
