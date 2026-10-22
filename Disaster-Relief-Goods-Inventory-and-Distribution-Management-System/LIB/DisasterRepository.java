package LIB;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DisasterRepository {
    /** SQL condition: household h has received a confirmed household release for its disaster. */
    static final String ASSISTED = "EXISTS (SELECT 1 FROM distributions d WHERE d.beneficiary_id = h.household_id "
            + "AND d.disaster_id = h.disaster_id AND d.status = 'APPROVED' AND d.evacuation_center_id IS NULL)";

    private static final String SELECT = "SELECT d.disaster_id, d.barangay_id, d.name, d.type, d.location, "
            + "d.disaster_date, d.status, d.severity, d.affected_areas, d.description, d.evacuation_site, "
            + "d.response_lead, d.assessment_contact, d.created_at, d.closed_at, cb.name AS created_by_name "
            + "FROM disasters d LEFT JOIN user_accounts cb ON cb.account_id = d.created_by ";

    public Disaster create(String name, String type, java.time.LocalDate date, String severity, String areas,
            String location, String description, String assessmentContact, String evacuationSite,
            Barangay barangay, UserAccount actor) throws SQLException {
        DisasterController rules = new DisasterController();
        return Db.transaction(connection -> {
            String id = IdGenerator.next(connection, "DIS", true, 3);
            Disaster disaster = rules.createDisaster(id, name.trim(), type, location.trim(), date.toString(),
                    Disaster.ACTIVE, barangay, actor);
            disaster.setSeverity(severity);
            disaster.setAffectedAreas(areas);
            disaster.setDescription(description);
            disaster.setAssessmentContact(assessmentContact);
            disaster.setEvacuationSite(evacuationSite);
            disaster.setResponseLead(actor.getName());
            Db.update(connection, "INSERT INTO disasters (disaster_id, barangay_id, name, type, location, "
                    + "disaster_date, status, severity, affected_areas, description, evacuation_site, response_lead, "
                    + "assessment_contact, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, barangay.getBarangayId(), disaster.getName(), type, disaster.getLocation(), date,
                    Disaster.ACTIVE, severity, areas, description, evacuationSite, actor.getName(),
                    assessmentContact, actor.getAccountId(), Time.now());
            AuditRepository.record(connection, actor, new AuditRepository.Entry("DISASTER_CREATED",
                    AuditRepository.MODULE_DISASTERS).reference(id).summary(disaster.getName() + " · " + type)
                    .movement("No stock movement").result("Event activated for household registration")
                    .barangay(barangay.getBarangayId()));
            return disaster;
        });
    }

    public List<Disaster> findByBarangay(String barangayId) throws SQLException {
        return Db.read(connection -> load(connection, "WHERE d.barangay_id = ? ORDER BY d.disaster_date DESC, "
                + "d.disaster_id DESC", barangayId));
    }

    public Disaster findById(String disasterId) throws SQLException {
        return Db.read(connection -> findById(connection, disasterId));
    }

    static Disaster findById(Connection connection, String disasterId) throws SQLException {
        List<Disaster> found = load(connection, "WHERE d.disaster_id = ?", disasterId);
        return found.isEmpty() ? null : found.get(0);
    }

    private static List<Disaster> load(Connection connection, String where, Object... params) throws SQLException {
        Map<String, Barangay> barangays = new HashMap<>();
        return Db.list(connection, SELECT + where, row -> {
            String barangayId = row.getString("barangay_id");
            if (!barangays.containsKey(barangayId)) {
                barangays.put(barangayId, BarangayRepository.findById(connection, barangayId));
            }
            Disaster disaster = new Disaster(row.getString("disaster_id"), row.getString("name"),
                    row.getString("type"), row.getString("location"), Db.date(row, "disaster_date").toString(),
                    row.getString("status"), barangays.get(barangayId));
            disaster.setSeverity(row.getString("severity"));
            disaster.setAffectedAreas(row.getString("affected_areas"));
            disaster.setDescription(row.getString("description"));
            disaster.setEvacuationSite(row.getString("evacuation_site"));
            disaster.setResponseLead(row.getString("response_lead"));
            disaster.setAssessmentContact(row.getString("assessment_contact"));
            disaster.setCreated(Db.dateTime(row, "created_at"), row.getString("created_by_name"));
            disaster.setClosedAt(Db.dateTime(row, "closed_at"));
            return disaster;
        }, params);
    }

    public void close(Disaster disaster, String reason, UserAccount actor) throws SQLException {
        Db.transaction(connection -> {
            Db.update(connection, "UPDATE disasters SET status = ?, closed_at = ? WHERE disaster_id = ?",
                    Disaster.CLOSED, Time.now(), disaster.getDisasterId());
            AuditRepository.record(connection, actor, new AuditRepository.Entry("DISASTER_CLOSED",
                    AuditRepository.MODULE_DISASTERS).reference(disaster.getDisasterId())
                    .summary(disaster.getName()).before("Active").after("Closed").reason(reason)
                    .movement("No stock movement").result("Registration and releases are closed")
                    .barangay(disaster.getBarangay().getBarangayId()));
            return null;
        });
    }

    /** Registered, individuals and assisted counts for every disaster of a barangay (for the list screen). */
    public Map<String, int[]> summaryByDisaster(String barangayId) throws SQLException {
        return Db.read(connection -> {
            Map<String, int[]> summary = new HashMap<>();
            Db.list(connection, "SELECT h.disaster_id, COUNT(*) AS registered, SUM(CASE WHEN " + ASSISTED
                    + " THEN 1 ELSE 0 END) AS assisted, (SELECT COUNT(*) FROM family_members m JOIN households h2 "
                    + "ON h2.household_id = m.household_id WHERE h2.disaster_id = h.disaster_id) AS individuals "
                    + "FROM households h JOIN disasters d ON d.disaster_id = h.disaster_id WHERE d.barangay_id = ? "
                    + "GROUP BY h.disaster_id", row -> {
                        summary.put(row.getString("disaster_id"), new int[] { row.getInt("registered"),
                                row.getInt("assisted"), row.getInt("individuals") });
                        return null;
                    }, barangayId);
            return summary;
        });
    }

    public DisasterStats stats(String disasterId) throws SQLException {
        return Db.read(connection -> {
            DisasterStats stats = new DisasterStats();
            stats.registered = Db.intValue(connection, "SELECT COUNT(*) FROM households WHERE disaster_id = ?",
                    disasterId);
            stats.assisted = Db.intValue(connection, "SELECT COUNT(*) FROM households h WHERE h.disaster_id = ? AND "
                    + ASSISTED, disasterId);
            Db.list(connection, "SELECT COUNT(*) AS people, "
                    + "SUM(CASE WHEN m.is_senior_citizen THEN 1 ELSE 0 END) AS seniors, "
                    + "SUM(CASE WHEN m.is_pwd THEN 1 ELSE 0 END) AS pwd, "
                    + "SUM(CASE WHEN m.is_pregnant THEN 1 ELSE 0 END) AS pregnant, "
                    + "SUM(CASE WHEN m.is_infant THEN 1 ELSE 0 END) AS infants, "
                    + "SUM(CASE WHEN m.is_senior_citizen OR m.is_pwd OR m.is_pregnant OR m.is_infant THEN 1 ELSE 0 END) "
                    + "AS vulnerable FROM family_members m JOIN households h ON h.household_id = m.household_id "
                    + "WHERE h.disaster_id = ?", row -> {
                        stats.individuals = row.getInt("people");
                        stats.seniors = row.getInt("seniors");
                        stats.pwd = row.getInt("pwd");
                        stats.pregnant = row.getInt("pregnant");
                        stats.infants = row.getInt("infants");
                        stats.vulnerablePeople = row.getInt("vulnerable");
                        return null;
                    }, disasterId);
            Db.list(connection, "SELECT damage_level, evacuation_status FROM households WHERE disaster_id = ?", row -> {
                String damage = row.getString("damage_level");
                if (Beneficiaries.DAMAGE_TOTAL.equals(damage)) {
                    stats.totallyDamaged++;
                } else if (Beneficiaries.DAMAGE_PARTIAL.equals(damage)) {
                    stats.partiallyDamaged++;
                }
                if ("Evacuated".equals(row.getString("evacuation_status"))) {
                    stats.evacuated++;
                }
                return null;
            }, disasterId);
            Db.list(connection, "SELECT COALESCE(NULLIF(h.purok, ''), 'Unassigned') AS area, COUNT(*) AS registered, "
                    + "SUM(CASE WHEN " + ASSISTED + " THEN 1 ELSE 0 END) AS assisted FROM households h "
                    + "WHERE h.disaster_id = ? GROUP BY area ORDER BY area", row -> {
                        stats.areas.add(new DisasterStats.AreaRow(row.getString("area"), row.getInt("registered"),
                                row.getInt("assisted")));
                        return null;
                    }, disasterId);
            Db.list(connection, "SELECT n.category, SUM(n.requested_quantity) AS needed FROM household_needs n "
                    + "JOIN households h ON h.household_id = n.household_id WHERE h.disaster_id = ? AND NOT "
                    + ASSISTED + " GROUP BY n.category", row -> {
                        stats.pendingNeeds.put(row.getString("category"), row.getInt("needed"));
                        return null;
                    }, disasterId);
            return stats;
        });
    }
}
