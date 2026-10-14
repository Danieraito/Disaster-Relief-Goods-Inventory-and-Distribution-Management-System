public class AuditLogController {
    public Audits createAuditRecord(String auditId, String action, String dateTime, UserAccount user,
            Donations donation, Distribution distribution) {
        if (user == null) {
            throw new IllegalArgumentException("Audit record must identify the user.");
        }
        return new Audits(auditId, action, dateTime, user, donation, distribution);
    }
}
