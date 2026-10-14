package LIB;

public class Audits {
    //Methods and Behaviors to be added later after SQL.
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

    public String getAuditId() {
        return auditId;
    }

    public void setAuditId(String auditId) {
        this.auditId = auditId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDateTime() {
        return dateTime;
    }

    public void setDateTime(String dateTime) {
        this.dateTime = dateTime;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public void setBeforeValue(String beforeValue) {
        this.beforeValue = beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public void setAfterValue(String afterValue) {
        this.afterValue = afterValue;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UserAccount getUser() {
        return user;
    }

    public void setUser(UserAccount user) {
        this.user = user;
    }

    public Donations getDonation() {
        return donation;
    }

    public void setDonation(Donations donation) {
        this.donation = donation;
    }

    public Distribution getDistribution() {
        return distribution;
    }

    public void setDistribution(Distribution distribution) {
        this.distribution = distribution;
    }
}
