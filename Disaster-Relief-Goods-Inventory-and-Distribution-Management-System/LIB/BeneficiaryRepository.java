package LIB;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BeneficiaryRepository {
    /** The inventory categories households can ask for, in display order. */
    public static final String[] NEED_CATEGORIES = { "Food", "Water", "Hygiene kits", "Clothing", "Medicine",
        "Shelter materials" };

    /** One line of the household list. */
    public static final class Row {
        public String householdId;
        public String head;
        public String address;
        public String purok;
        public String damage;
        public String evacuationStatus;
        public String evacuationLocation;
        public int members;
        public int seniors;
        public int pwd;
        public int pregnant;
        public int infants;
        public boolean assisted;
        public List<String> needCategories = new ArrayList<>();

        public boolean vulnerable() {
            return seniors + pwd + pregnant + infants > 0;
        }
    }

    public static final class Page {
        public final List<Row> rows;
        public final int total;
        public final int page;
        public final int pageSize;

        Page(List<Row> rows, int total, int page, int pageSize) {
            this.rows = rows;
            this.total = total;
            this.page = page;
            this.pageSize = pageSize;
        }

        public int pages() {
            return Math.max(1, (total + pageSize - 1) / pageSize);
        }
    }

    public static final class Filter {
        /** ALL, PENDING, ASSISTED or VULNERABLE. */
        public String tab = "ALL";
        public String text;
        public String purok;
        public String damage;
    }

    // ------------------------------------------------------------------ list

    public Page findPage(String disasterId, Filter filter, int page, int pageSize) throws SQLException {
        List<Object> params = new ArrayList<>();
        String where = where(disasterId, filter, params);
        return Db.read(connection -> {
            int total = Db.intValue(connection, "SELECT COUNT(*) FROM households h" + where, params.toArray());
            int pages = Math.max(1, (total + pageSize - 1) / pageSize);
            int safePage = Math.min(Math.max(1, page), pages);
            List<Object> pagedParams = new ArrayList<>(params);
            pagedParams.add(pageSize);
            pagedParams.add((safePage - 1) * pageSize);
            List<Row> rows = Db.list(connection, "SELECT h.household_id, h.household_name, h.address, h.purok, "
                    + "h.damage_level, h.evacuation_status, h.evacuation_location, "
                    + "(SELECT COUNT(*) FROM family_members m WHERE m.household_id = h.household_id) AS members, "
                    + "(SELECT COUNT(*) FROM family_members m WHERE m.household_id = h.household_id AND m.is_senior_citizen) AS seniors, "
                    + "(SELECT COUNT(*) FROM family_members m WHERE m.household_id = h.household_id AND m.is_pwd) AS pwd, "
                    + "(SELECT COUNT(*) FROM family_members m WHERE m.household_id = h.household_id AND m.is_pregnant) AS pregnant, "
                    + "(SELECT COUNT(*) FROM family_members m WHERE m.household_id = h.household_id AND m.is_infant) AS infants, "
                    + "(SELECT GROUP_CONCAT(n.category SEPARATOR ',') FROM household_needs n WHERE n.household_id = h.household_id) AS needs, "
                    + "CASE WHEN " + DisasterRepository.ASSISTED + " THEN 1 ELSE 0 END AS assisted "
                    + "FROM households h" + where + " ORDER BY h.registered_at DESC, h.household_id DESC LIMIT ? OFFSET ?",
                    row -> {
                        Row item = new Row();
                        item.householdId = row.getString("household_id");
                        item.head = row.getString("household_name");
                        item.address = row.getString("address");
                        item.purok = Db.text(row, "purok");
                        item.damage = Db.text(row, "damage_level");
                        item.evacuationStatus = Db.text(row, "evacuation_status");
                        item.evacuationLocation = Db.text(row, "evacuation_location");
                        item.members = row.getInt("members");
                        item.seniors = row.getInt("seniors");
                        item.pwd = row.getInt("pwd");
                        item.pregnant = row.getInt("pregnant");
                        item.infants = row.getInt("infants");
                        item.assisted = row.getInt("assisted") == 1;
                        String needs = row.getString("needs");
                        if (needs != null) {
                            for (String category : NEED_CATEGORIES) {
                                if (("," + needs + ",").contains("," + category + ",")) {
                                    item.needCategories.add(category);
                                }
                            }
                        }
                        return item;
                    }, pagedParams.toArray());
            return new Page(rows, total, safePage, pageSize);
        });
    }

    /** Counts for the tabs: all, pending, assisted and vulnerable households. */
    public int[] tabCounts(String disasterId) throws SQLException {
        return Db.read(connection -> {
            int[] counts = new int[4];
            counts[0] = Db.intValue(connection, "SELECT COUNT(*) FROM households h WHERE h.disaster_id = ?", disasterId);
            counts[2] = Db.intValue(connection, "SELECT COUNT(*) FROM households h WHERE h.disaster_id = ? AND "
                    + DisasterRepository.ASSISTED, disasterId);
            counts[1] = counts[0] - counts[2];
            counts[3] = Db.intValue(connection, "SELECT COUNT(*) FROM households h WHERE h.disaster_id = ? AND EXISTS "
                    + "(SELECT 1 FROM family_members m WHERE m.household_id = h.household_id AND "
                    + "(m.is_senior_citizen OR m.is_pwd OR m.is_pregnant OR m.is_infant))", disasterId);
            return counts;
        });
    }

    public List<String> puroks(String disasterId) throws SQLException {
        return Db.read(connection -> Db.list(connection, "SELECT DISTINCT purok FROM households WHERE disaster_id = ? "
                + "AND purok IS NOT NULL AND purok <> '' ORDER BY purok", row -> row.getString(1), disasterId));
    }

    private String where(String disasterId, Filter filter, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE h.disaster_id = ?");
        params.add(disasterId);
        if (filter != null) {
            switch (filter.tab == null ? "ALL" : filter.tab) {
                case "PENDING":
                    where.append(" AND NOT ").append(DisasterRepository.ASSISTED);
                    break;
                case "ASSISTED":
                    where.append(" AND ").append(DisasterRepository.ASSISTED);
                    break;
                case "VULNERABLE":
                    where.append(" AND EXISTS (SELECT 1 FROM family_members m WHERE m.household_id = h.household_id "
                            + "AND (m.is_senior_citizen OR m.is_pwd OR m.is_pregnant OR m.is_infant))");
                    break;
                default:
                    break;
            }
            if (filter.text != null && !filter.text.trim().isEmpty()) {
                where.append(" AND (h.household_id LIKE ? OR h.household_name LIKE ? OR h.address LIKE ?)");
                String like = "%" + filter.text.trim() + "%";
                params.add(like);
                params.add(like);
                params.add(like);
            }
            if (filter.purok != null && !filter.purok.isEmpty()) {
                where.append(" AND h.purok = ?");
                params.add(filter.purok);
            }
            if (filter.damage != null && !filter.damage.isEmpty()) {
                where.append(" AND h.damage_level = ?");
                params.add(filter.damage);
            }
        }
        return where.toString();
    }

    // ------------------------------------------------------------------ detail

    /** Loads a household with its full roster, needs and what has already been released to it. */
    public Beneficiaries findById(String householdId, Disaster knownDisaster) throws SQLException {
        return Db.read(connection -> findById(connection, householdId, knownDisaster));
    }

    static Beneficiaries findById(Connection connection, String householdId, Disaster knownDisaster)
            throws SQLException {
        String[] header = Db.one(connection, "SELECT h.disaster_id, h.household_name, h.address, "
                + "h.assistance_requirements, h.damage_level, h.household_head_member_id, h.contact_number, h.purok, "
                + "h.is_homeless, h.evacuation_status, h.evacuation_location, h.assessment_note, h.registered_at, "
                + "u.name AS registered_by, CASE WHEN " + DisasterRepository.ASSISTED + " THEN '1' ELSE '0' END AS assisted "
                + "FROM households h LEFT JOIN user_accounts u ON u.account_id = h.registered_by "
                + "WHERE h.household_id = ?", row -> new String[] { row.getString("disaster_id"),
                    row.getString("household_name"), row.getString("address"), row.getString("assistance_requirements"),
                    row.getString("damage_level"), row.getString("household_head_member_id"),
                    row.getString("contact_number"), row.getString("purok"), String.valueOf(row.getBoolean("is_homeless")),
                    row.getString("evacuation_status"), row.getString("evacuation_location"),
                    row.getString("assessment_note"), String.valueOf(Db.dateTime(row, "registered_at")),
                    row.getString("registered_by"), row.getString("assisted") }, householdId);
        if (header == null) {
            return null;
        }
        Disaster disaster = knownDisaster != null && knownDisaster.getDisasterId().equals(header[0])
                ? knownDisaster : DisasterRepository.findById(connection, header[0]);
        List<FamilyMember> members = Db.list(connection, "SELECT member_id, full_name, age, age_months, sex, "
                + "is_senior_citizen, is_infant, is_pregnant, is_pwd FROM family_members WHERE household_id = ? "
                + "ORDER BY LENGTH(member_id), member_id", row -> {
                    int months = row.getInt("age_months");
                    Integer ageMonths = row.wasNull() ? null : months;
                    return new FamilyMember(row.getString("member_id"), row.getString("full_name"), row.getInt("age"),
                            ageMonths, row.getString("sex"), row.getBoolean("is_senior_citizen"),
                            row.getBoolean("is_infant"), row.getBoolean("is_pregnant"), row.getBoolean("is_pwd"));
                }, householdId);
        Beneficiaries household = new Beneficiaries(householdId, header[1], header[2], header[3], disaster, members);
        if (header[5] != null) {
            household.setHouseholdHead(header[5]);
        }
        household.setDamageLevel(header[4]);
        household.setContactNumber(header[6]);
        household.setPurok(header[7]);
        household.setHomeless("true".equals(header[8]));
        household.setEvacuationStatus(header[9]);
        household.setEvacuationLocation(header[10]);
        household.setAssessmentNote(header[11]);
        household.setRegistered(Db.one(connection, "SELECT registered_at FROM households WHERE household_id = ?",
                row -> Db.dateTime(row, "registered_at"), householdId), header[13]);
        household.setAssisted("1".equals(header[14]));
        Db.list(connection, "SELECT category, requested_quantity FROM household_needs WHERE household_id = ?", row -> {
            household.setNeed(row.getString("category"), row.getInt("requested_quantity"));
            return null;
        }, householdId);
        Db.list(connection, "SELECT i.category, SUM(di.quantity) AS released FROM distribution_items di "
                + "JOIN distributions d ON d.distribution_id = di.distribution_id "
                + "JOIN inventory_items i ON i.inventory_item_id = di.inventory_item_id "
                + "WHERE d.beneficiary_id = ? AND d.status = 'APPROVED' AND d.evacuation_center_id IS NULL "
                + "GROUP BY i.category", row -> {
                    household.getReleased().put(row.getString("category"), row.getInt("released"));
                    return null;
                }, householdId);
        return household;
    }

    /** Households of the same disaster with the same head name and/or address (for duplicate warnings). */
    public List<Row> findSimilar(String disasterId, String headName, String address) throws SQLException {
        Filter none = new Filter();
        return Db.read(connection -> Db.list(connection, "SELECT household_id, household_name, address, purok "
                + "FROM households WHERE disaster_id = ? AND (LOWER(household_name) = LOWER(?) "
                + "OR LOWER(address) = LOWER(?))", row -> {
                    Row item = new Row();
                    item.householdId = row.getString("household_id");
                    item.head = row.getString("household_name");
                    item.address = row.getString("address");
                    item.purok = Db.text(row, "purok");
                    return item;
                }, disasterId, headName.trim(), address.trim()));
    }

    // ------------------------------------------------------------------ writes

    public Beneficiaries register(Disaster disaster, String headName, String contact, String address,
            boolean homeless, String purok, List<FamilyMember> draftMembers, String damage, String evacuationStatus,
            String evacuationLocation, String note, Map<String, Integer> needs, UserAccount actor)
            throws SQLException {
        BeneficiaryController rules = new BeneficiaryController();
        return Db.transaction(connection -> {
            String householdId = IdGenerator.next(connection, "HH", true, 4);
            List<FamilyMember> members = new ArrayList<>();
            FamilyMember head = null;
            int number = 1;
            for (FamilyMember draft : draftMembers) {
                FamilyMember member = new FamilyMember(householdId + "-M" + number++, draft.getFullName(),
                        draft.getAge(), draft.getAgeMonths(), draft.getSex(), draft.isSeniorCitizen(),
                        draft.isInfant(), draft.isPregnant(), draft.isPersonWithDisability());
                members.add(member);
                if (head == null && member.getFullName().equalsIgnoreCase(headName.trim())) {
                    head = member;
                }
            }
            if (head == null) {
                throw new IllegalArgumentException("Include the household head in the family member records.");
            }
            String requirements = needsSummary(needs);
            Beneficiaries household = homeless
                    ? rules.registerHomelessBeneficiary(householdId, headName, requirements, disaster, members, actor)
                    : rules.registerBeneficiary(householdId, headName, address, requirements, disaster, members, actor);
            household.setHouseholdHead(head.getMemberId());
            household.setDamageLevel(damage);
            household.setContactNumber(contact);
            household.setPurok(purok);
            household.setHomeless(homeless);
            household.setEvacuationStatus(evacuationStatus);
            household.setEvacuationLocation(evacuationLocation);
            household.setAssessmentNote(note);
            for (Map.Entry<String, Integer> need : needs.entrySet()) {
                household.setNeed(need.getKey(), need.getValue());
            }
            if (Db.intValue(connection, "SELECT COUNT(*) FROM households WHERE disaster_id = ? AND "
                    + "LOWER(household_name) = LOWER(?) AND LOWER(address) = LOWER(?)", disaster.getDisasterId(),
                    household.getName(), household.getAddress()) > 0) {
                throw new IllegalArgumentException("A household for " + household.getName()
                        + " at this address is already registered for this disaster.");
            }
            java.time.LocalDateTime now = Time.now();
            Db.update(connection, "INSERT INTO households (household_id, disaster_id, household_name, address, "
                    + "assistance_requirements, damage_level, household_head_member_id, contact_number, purok, "
                    + "is_homeless, evacuation_status, evacuation_location, assessment_note, registered_by, "
                    + "registered_at) VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?)", householdId,
                    disaster.getDisasterId(), household.getName(), household.getAddress(), requirements, damage,
                    household.getContactNumber(), household.getPurok(), homeless, evacuationStatus,
                    evacuationLocation, note, actor.getAccountId(), now);
            for (FamilyMember member : household.getFamilyMembers()) {
                Db.update(connection, "INSERT INTO family_members (member_id, household_id, full_name, age, "
                        + "age_months, sex, is_senior_citizen, is_infant, is_pregnant, is_pwd) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", member.getMemberId(), householdId,
                        member.getFullName(), member.getAge(), member.getAgeMonths(), member.getSex(),
                        member.isSeniorCitizen(), member.isInfant(), member.isPregnant(),
                        member.isPersonWithDisability());
            }
            Db.update(connection, "UPDATE households SET household_head_member_id = ? WHERE household_id = ?",
                    household.getHouseholdHead().getMemberId(), householdId);
            for (Map.Entry<String, Integer> need : household.getNeeds().entrySet()) {
                Db.update(connection, "INSERT INTO household_needs (household_id, category, requested_quantity) "
                        + "VALUES (?, ?, ?)", householdId, need.getKey(), need.getValue());
            }
            AuditRepository.record(connection, actor, new AuditRepository.Entry("HOUSEHOLD_REGISTERED",
                    AuditRepository.MODULE_BENEFICIARIES).reference(householdId)
                    .summary(household.getName() + " household registered").movement("No stock movement")
                    .after(household.getHouseholdSize() + " members · " + requirements)
                    .reason("Registered for " + disaster.getName()).result("Awaiting assistance")
                    .household(householdId).barangay(disaster.getBarangay().getBarangayId()));
            household.setRegistered(now, actor.getName());
            return household;
        });
    }

    /** Updates contact, damage and evacuation status; the previous values go to the record changes log. */
    public void updateProfile(Beneficiaries household, String contact, String damage, String evacuationStatus,
            String reason, UserAccount actor) throws SQLException {
        Db.transaction(connection -> {
            List<String> before = new ArrayList<>();
            List<String> after = new ArrayList<>();
            if (!household.getContactNumber().equals(contact.trim())) {
                before.add("Contact: " + household.getContactNumber());
                after.add("Contact: " + contact.trim());
            }
            String oldDamage = household.getDamageLevel() == null ? "" : household.getDamageLevel();
            if (!oldDamage.equals(damage)) {
                before.add("Damage: " + oldDamage);
                after.add("Damage: " + damage);
            }
            if (!household.getEvacuationStatus().equals(evacuationStatus)) {
                before.add("Evacuation: " + household.getEvacuationStatus());
                after.add("Evacuation: " + evacuationStatus);
            }
            if (before.isEmpty()) {
                throw new IllegalArgumentException("Nothing was changed. Update a field before saving.");
            }
            Db.update(connection, "UPDATE households SET contact_number = ?, damage_level = ?, evacuation_status = ? "
                    + "WHERE household_id = ?", contact.trim(), damage, evacuationStatus, household.getBeneficiaryId());
            AuditRepository.record(connection, actor, new AuditRepository.Entry("HOUSEHOLD_UPDATED",
                    AuditRepository.MODULE_BENEFICIARIES).reference(household.getBeneficiaryId())
                    .summary(household.getName() + " household updated").movement("No stock movement")
                    .before(String.join("\n", before)).after(String.join("\n", after)).reason(reason)
                    .result("Assistance history unchanged").household(household.getBeneficiaryId())
                    .barangay(household.getDisaster().getBarangay().getBarangayId()));
            return null;
        });
    }

    static String needsSummary(Map<String, Integer> needs) {
        Map<String, String> units = new LinkedHashMap<>();
        units.put("Food", "pack");
        units.put("Water", "liter");
        units.put("Hygiene kits", "kit");
        units.put("Clothing", "bundle");
        units.put("Medicine", "box");
        units.put("Shelter materials", "piece");
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> unit : units.entrySet()) {
            Integer quantity = needs.get(unit.getKey());
            if (quantity != null && quantity > 0) {
                parts.add(quantity + " " + unit.getValue() + (quantity == 1 ? "" : "s"));
            }
        }
        return String.join(" · ", parts);
    }
}
