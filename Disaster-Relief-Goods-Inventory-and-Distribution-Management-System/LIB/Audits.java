package LIB;

import java.time.LocalDateTime;

public class Audits {
    private String auditId;
    private String action;
    private String dateTime;
    private String reference;
    private String beforeValue;
    private String afterValue;
    private String reason;
    private UserAccount user;
    private Donations donation;
    private Distribution distribution;
    // Fields filled when a stored row is read back for the Audit Logs screen.
    private LocalDateTime when;
    private String module = "";
    private String summary = "";
    private String movement = "";
    private String result = "";
    private String actorName = "";
    private String actorRole = "";
    private String barangayId;
    private String itemId;
    private String householdId;

    public Audits(String auditId, String action, String dateTime, UserAccount user,
            Donations donation, Distribution distribution) {
        this(auditId, action, dateTime, user, donation, distribution, null, null, null, null);
    }

    public Audits(String auditId, String action, String dateTime, UserAccount user,
            Donations donation, Distribution distribution, String reference,
            String beforeValue, String afterValue, String reason) {
        this.auditId = auditId;
        this.action = action;
        this.dateTime = dateTime;
        this.user = user;
        this.donation = donation;
        this.distribution = distribution;
        this.reference = reference;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.reason = reason;
    }

    public String getAuditId() { return auditId; }
    public void setAuditId(String auditId) { this.auditId = auditId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDateTime() { return dateTime; }
    public void setDateTime(String dateTime) { this.dateTime = dateTime; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getBeforeValue() { return beforeValue; }
    public void setBeforeValue(String beforeValue) { this.beforeValue = beforeValue; }
    public String getAfterValue() { return afterValue; }
    public void setAfterValue(String afterValue) { this.afterValue = afterValue; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public UserAccount getUser() { return user; }
    public void setUser(UserAccount user) { this.user = user; }
    public Donations getDonation() { return donation; }
    public void setDonation(Donations donation) { this.donation = donation; }
    public Distribution getDistribution() { return distribution; }
    public void setDistribution(Distribution distribution) { this.distribution = distribution; }

    public LocalDateTime getWhen() { return when; }
    public String getModule() { return module; }
    public String getSummary() { return summary; }
    public String getMovement() { return movement; }
    public String getResult() { return result; }
    public String getActorName() { return actorName; }
    public String getActorRole() { return actorRole; }
    public String getBarangayId() { return barangayId; }
    public String getItemId() { return itemId; }
    public String getHouseholdId() { return householdId; }

    void setStoredDetails(LocalDateTime when, String module, String summary, String movement, String result,
            String actorName, String actorRole, String barangayId, String itemId, String householdId) {
        this.when = when;
        this.module = module == null ? "" : module;
        this.summary = summary == null ? "" : summary;
        this.movement = movement == null ? "" : movement;
        this.result = result == null ? "" : result;
        this.actorName = actorName == null ? "" : actorName;
        this.actorRole = actorRole == null ? "" : actorRole;
        this.barangayId = barangayId;
        this.itemId = itemId;
        this.householdId = householdId;
    }
}
