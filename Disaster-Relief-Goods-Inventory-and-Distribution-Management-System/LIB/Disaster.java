package LIB;

import java.time.LocalDateTime;

public class Disaster implements BarangayScoped {
    public static final String ACTIVE = "ACTIVE";
    public static final String CLOSED = "CLOSED";

    private String disasterId;
    private String name;
    private String type;
    private String location;
    private String date;
    private String status;
    private String severity;
    private String affectedAreas;
    private String description = "";
    private String evacuationSite = "";
    private String responseLead = "";
    private String assessmentContact = "";
    private String createdByName = "";
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
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

    public boolean isActive() {
        return ACTIVE.equalsIgnoreCase(status);
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public String getEvacuationSite() {
        return evacuationSite;
    }

    public void setEvacuationSite(String evacuationSite) {
        this.evacuationSite = evacuationSite == null ? "" : evacuationSite;
    }

    public String getResponseLead() {
        return responseLead;
    }

    public void setResponseLead(String responseLead) {
        this.responseLead = responseLead == null ? "" : responseLead;
    }

    public String getAssessmentContact() {
        return assessmentContact;
    }

    public void setAssessmentContact(String assessmentContact) {
        this.assessmentContact = assessmentContact == null ? "" : assessmentContact;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    void setCreated(LocalDateTime when, String by) {
        this.createdAt = when;
        this.createdByName = by == null ? "" : by;
    }

    void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }
}
