public class Audits {
    private String auditId;
    private String action;
    private String dateTime;
    private UserAccount user;
    private Donations donation;
    private Distribution distribution;

    public Audits(String auditId, String action, String dateTime, UserAccount user,
            Donations donation, Distribution distribution) {
        this.auditId = auditId;
        this.action = action;
        this.dateTime = dateTime;
        this.user = user;
        this.donation = donation;
        this.distribution = distribution;
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
