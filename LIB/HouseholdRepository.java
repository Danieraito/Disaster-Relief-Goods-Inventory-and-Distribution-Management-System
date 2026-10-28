package LIB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Saves and loads households (Beneficiaries) and their family members. */
public class HouseholdRepository {

    /** Saves a new household and its member roster with an open connection. */
    public static void insert(Connection connection, Beneficiaries household) throws SQLException {
        String sql = "INSERT INTO households (beneficiary_id, disaster_id, name, address, "
                + "assistance_requirements, damage_level, evacuation_status, faced_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, household.getBeneficiaryId());
            statement.setString(2, household.getDisaster().getDisasterId());
            statement.setString(3, household.getName());
            statement.setString(4, household.getAddress());
            statement.setString(5, household.getAssistanceRequirements());
            statement.setString(6, household.getDamageLevel());
            statement.setString(7, household.getEvacuationStatus());
            statement.setString(8, household.getFacedNumber());
            statement.executeUpdate();
        }
        insertMembers(connection, household);
    }

    /** Saves edited details and replaces the member roster (members are not referenced elsewhere). */
    public static void update(Connection connection, Beneficiaries household) throws SQLException {
        String sql = "UPDATE households SET name = ?, address = ?, assistance_requirements = ?, damage_level = ?, "
                + "evacuation_status = ?, faced_number = ? WHERE beneficiary_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, household.getName());
            statement.setString(2, household.getAddress());
            statement.setString(3, household.getAssistanceRequirements());
            statement.setString(4, household.getDamageLevel());
            statement.setString(5, household.getEvacuationStatus());
            statement.setString(6, household.getFacedNumber());
            statement.setString(7, household.getBeneficiaryId());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM family_members WHERE household_id = ?")) {
            statement.setString(1, household.getBeneficiaryId());
            statement.executeUpdate();
        }
        insertMembers(connection, household);
    }

    /** Another household of the same disaster that already uses this FACED number, or null. */
    public static String findFacedOwner(Connection connection, String disasterId, String facedNumber,
            String exceptHouseholdId) throws SQLException {
        if (facedNumber == null) {
            return null;
        }
        List<Map<String, Object>> rows = Database.query(connection,
                "SELECT beneficiary_id FROM households WHERE disaster_id = ? AND faced_number = ? "
                        + "AND beneficiary_id <> ?", disasterId, facedNumber, exceptHouseholdId == null ? "" : exceptHouseholdId);
        return rows.isEmpty() ? null : (String) rows.get(0).get("beneficiary_id");
    }

    private static void insertMembers(Connection connection, Beneficiaries household) throws SQLException {
        String memberSql = "INSERT INTO family_members (member_id, household_id, full_name, age, is_senior, "
                + "is_infant, is_pwd, is_head, is_pregnant) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        for (FamilyMember member : household.getFamilyMembers()) {
            try (PreparedStatement statement = connection.prepareStatement(memberSql)) {
                statement.setString(1, member.getMemberId());
                statement.setString(2, household.getBeneficiaryId());
                statement.setString(3, member.getFullName());
                statement.setInt(4, member.getAge());
                statement.setInt(5, member.isSeniorCitizen() ? 1 : 0);
                statement.setInt(6, member.isInfant() ? 1 : 0);
                statement.setInt(7, member.isPersonWithDisability() ? 1 : 0);
                statement.setInt(8, member == household.getHouseholdHead() ? 1 : 0);
                statement.setInt(9, member.isPregnant() ? 1 : 0);
                statement.executeUpdate();
            }
        }
    }

    /**
     * Loads a household. The disaster object is passed in so the household and a distribution
     * share the very same Disaster object (the Distribution model checks this).
     */
    public static Beneficiaries load(Connection connection, String householdId, Disaster disaster) throws SQLException {
        String sql = "SELECT beneficiary_id, disaster_id, name, address, assistance_requirements, damage_level, "
                + "evacuation_status, faced_number FROM households WHERE beneficiary_id = ?";
        String name;
        String address;
        String needs;
        String damage;
        String evacuation;
        String faced;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, householdId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                if (disaster == null || !disaster.getDisasterId().equals(rs.getString("disaster_id"))) {
                    disaster = DisasterRepository.findById(connection, rs.getString("disaster_id"));
                }
                name = rs.getString("name");
                address = rs.getString("address");
                needs = rs.getString("assistance_requirements");
                damage = rs.getString("damage_level");
                evacuation = rs.getString("evacuation_status");
                faced = rs.getString("faced_number");
            }
        }

        List<FamilyMember> members = new ArrayList<>();
        String headId = null;
        String memberSql = "SELECT member_id, full_name, age, is_senior, is_infant, is_pwd, is_head, is_pregnant "
                + "FROM family_members WHERE household_id = ? ORDER BY is_head DESC, member_id";
        try (PreparedStatement statement = connection.prepareStatement(memberSql)) {
            statement.setString(1, householdId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    members.add(new FamilyMember(rs.getString("member_id"), rs.getString("full_name"),
                            rs.getInt("age"), rs.getInt("is_senior") == 1, rs.getInt("is_infant") == 1,
                            rs.getInt("is_pwd") == 1, rs.getInt("is_pregnant") == 1));
                    if (rs.getInt("is_head") == 1) {
                        headId = rs.getString("member_id");
                    }
                }
            }
        }
        Beneficiaries household = new Beneficiaries(householdId, name, address, needs, disaster, members);
        if (headId != null) {
            household.setHouseholdHead(headId);
        }
        household.setDamageLevel(damage);
        household.setEvacuationStatus(evacuation);
        household.setFacedNumber(faced);
        return household;
    }

    /**
     * Household list for one disaster (or a whole workspace when disasterId is null), with member
     * counts and whether the household has already received an approved distribution.
     */
    public List<Map<String, Object>> listSummaries(String barangayId, String disasterId) throws SQLException {
        String sql = "SELECT h.beneficiary_id AS id, h.name AS head, h.address, h.assistance_requirements AS needs, "
                + "h.damage_level AS damage, h.evacuation_status AS evacuation, h.faced_number AS facedNumber, "
                + "h.disaster_id AS disasterId, "
                + "d.name AS disaster, d.barangay_id AS barangayId, "
                + "(SELECT COUNT(*) FROM family_members m WHERE m.household_id = h.beneficiary_id) AS members, "
                + "(SELECT COALESCE(SUM(is_senior),0) FROM family_members m WHERE m.household_id = h.beneficiary_id) AS seniors, "
                + "(SELECT COALESCE(SUM(is_infant),0) FROM family_members m WHERE m.household_id = h.beneficiary_id) AS infants, "
                + "(SELECT COALESCE(SUM(is_pwd),0) FROM family_members m WHERE m.household_id = h.beneficiary_id) AS pwd, "
                + "(SELECT COALESCE(SUM(is_pregnant),0) FROM family_members m WHERE m.household_id = h.beneficiary_id) AS pregnant, "
                + "(SELECT x.distribution_id FROM distributions x WHERE x.household_id = h.beneficiary_id "
                + "   AND x.status = 'APPROVED' ORDER BY x.distribution_id DESC LIMIT 1) AS assistedBy, "
                + "(SELECT x.distribution_id FROM distributions x WHERE x.household_id = h.beneficiary_id "
                + "   AND x.status = 'PENDING' ORDER BY x.distribution_id DESC LIMIT 1) AS pendingRelease, "
                + "(SELECT GROUP_CONCAT(x.round_no) FROM distributions x WHERE x.household_id = h.beneficiary_id "
                + "   AND x.status IN ('PENDING','APPROVED')) AS rounds "
                + "FROM households h JOIN disasters d ON d.disaster_id = h.disaster_id "
                + "WHERE (? IS NULL OR d.barangay_id = ?) AND (? IS NULL OR h.disaster_id = ?) "
                + "ORDER BY h.beneficiary_id DESC";
        return Database.query(sql, barangayId, barangayId, disasterId, disasterId);
    }

    /** Groups member rows by household id (used to show members without one query per row). */
    public Map<String, List<Map<String, Object>>> membersByHousehold(String barangayId) throws SQLException {
        String sql = "SELECT m.household_id AS householdId, m.member_id AS id, m.full_name AS name, m.age, "
                + "m.is_senior AS senior, m.is_infant AS infant, m.is_pwd AS pwd, m.is_pregnant AS pregnant, "
                + "m.is_head AS head FROM family_members m "
                + "JOIN households h ON h.beneficiary_id = m.household_id "
                + "JOIN disasters d ON d.disaster_id = h.disaster_id "
                + "WHERE (? IS NULL OR d.barangay_id = ?) ORDER BY m.is_head DESC, m.member_id";
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : Database.query(sql, barangayId, barangayId)) {
            String id = (String) row.remove("householdId");
            grouped.computeIfAbsent(id, key -> new ArrayList<>()).add(row);
        }
        return grouped;
    }
}
