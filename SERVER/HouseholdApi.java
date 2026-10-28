package SERVER;

import LIB.AuditRepository;
import LIB.Beneficiaries;
import LIB.BeneficiaryController;
import LIB.Database;
import LIB.Disaster;
import LIB.DisasterRepository;
import LIB.FamilyMember;
import LIB.HouseholdRepository;
import LIB.UserAccount;
import com.sun.net.httpserver.HttpExchange;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Beneficiaries screen: list households of a disaster (with their priority score), register new
 * households with their members, and edit a household's details and members later.
 */
final class HouseholdApi {
    private final HouseholdRepository repository = new HouseholdRepository();
    private final DisasterRepository disasters = new DisasterRepository();
    private final BeneficiaryController controller = new BeneficiaryController();
    private final AuditRepository audits = new AuditRepository();

    /** One member row as sent by the form. id is empty for a member added in this form. */
    private static final class MemberRow {
        String id;
        String name;
        int age;
        boolean senior;
        boolean infant;
        boolean pwd;
        boolean pregnant;
    }

    void list(HttpExchange exchange, UserAccount user) throws Exception {
        String scope = Api.scope(user);
        String disasterId = Http.query(exchange, "disaster");
        List<Map<String, Object>> households = repository.listSummaries(scope, disasterId.isEmpty() ? null : disasterId);
        Map<String, List<Map<String, Object>>> members = repository.membersByHousehold(scope);
        for (Map<String, Object> household : households) {
            household.put("memberList", members.getOrDefault((String) household.get("id"), new ArrayList<>()));
            // The priority rule lives in the Beneficiaries model so every screen uses the same numbers.
            household.put("priority", Beneficiaries.priorityScore(number(household, "seniors"), number(household, "pwd"),
                    number(household, "infants"), number(household, "pregnant"), (String) household.get("damage"),
                    (String) household.get("evacuation"), number(household, "members")));
        }
        Http.sendJson(exchange, 200, Json.obj(
                "households", households,
                "disasters", disasters.listSummaries(scope)));
    }

    private static int number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? 0 : ((Number) value).intValue();
    }

    /**
     * Reads the member rows. The form sends one value per member for each field (memberId,
     * memberName, memberAge, memberSenior, memberInfant, memberPwd, memberPregnant).
     */
    private static List<MemberRow> readMembers(Map<String, List<String>> form) {
        List<String> names = Api.all(form, "memberName");
        List<String> ages = Api.all(form, "memberAge");
        List<String> seniors = Api.all(form, "memberSenior");
        List<String> infants = Api.all(form, "memberInfant");
        List<String> pwds = Api.all(form, "memberPwd");
        List<String> pregnant = Api.all(form, "memberPregnant");
        List<String> ids = Api.all(form, "memberId");
        if (names.isEmpty()) {
            throw new ApiError(400, "Add at least one confirmed family member.");
        }
        int count = names.size();
        if (ages.size() != count || seniors.size() != count || infants.size() != count || pwds.size() != count
                || pregnant.size() != count || (!ids.isEmpty() && ids.size() != count)) {
            throw new ApiError(400, "Member details are incomplete. Please check every member row.");
        }
        List<MemberRow> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            MemberRow row = new MemberRow();
            row.id = ids.isEmpty() ? "" : ids.get(i).trim();
            row.name = names.get(i).trim();
            if (row.name.isEmpty()) {
                throw new ApiError(400, "Every member needs a full name (row " + (i + 1) + ").");
            }
            row.age = Api.wholeNumber(ages.get(i), "Age of " + row.name);
            // Age and categories are entered separately and never worked out from each other.
            row.senior = "1".equals(seniors.get(i));
            row.infant = "1".equals(infants.get(i));
            row.pwd = "1".equals(pwds.get(i));
            row.pregnant = "1".equals(pregnant.get(i));
            rows.add(row);
        }
        return rows;
    }

    private static int headIndex(Map<String, List<String>> form, int memberCount) {
        String text = Api.text(form, "headIndex");
        int index = Api.wholeNumber(text.isEmpty() ? "-1" : text, "Household head");
        if (index < 0 || index >= memberCount) {
            throw new ApiError(400, "Choose which member is the household head.");
        }
        return index;
    }

    private static String optional(Map<String, List<String>> form, String name) {
        String value = Api.text(form, name);
        return value.isEmpty() ? null : value;
    }

    /** Next free member number for a household (members are named HH-...-M1, -M2, ...). */
    private static int nextMemberNumber(Beneficiaries household) {
        int highest = 0;
        for (FamilyMember member : household.getFamilyMembers()) {
            String id = member.getMemberId();
            int at = id.lastIndexOf("-M");
            if (at >= 0) {
                try {
                    highest = Math.max(highest, Integer.parseInt(id.substring(at + 2)));
                } catch (NumberFormatException e) {
                    // older IDs such as FM-001; ignore
                }
            }
        }
        return Math.max(highest, household.getHouseholdSize()) + 1;
    }

    private static void requireUniqueFaced(Connection connection, String disasterId, String faced, String exceptId)
            throws Exception {
        String owner = HouseholdRepository.findFacedOwner(connection, disasterId, faced, exceptId);
        if (owner != null) {
            throw new ApiError(400, "FACED card " + faced + " is already used by " + owner + " in this disaster.");
        }
    }

    void register(HttpExchange exchange, UserAccount user) throws Exception {
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String disasterId = Api.required(form, "disasterId", "Disaster");
        boolean homeless = "1".equals(Api.text(form, "homeless"));
        String address = Api.text(form, "address");
        if (!homeless && address.isEmpty()) {
            throw new ApiError(400, "Address is required (tick \"No fixed address\" for a homeless household).");
        }
        String needs = Api.text(form, "needs");
        String faced = optional(form, "facedNumber");
        List<MemberRow> rows = readMembers(form);
        int headIndex = headIndex(form, rows.size());

        String id = Api.inTransaction((Connection connection) -> {
            Disaster disaster = DisasterRepository.findById(connection, disasterId);
            if (disaster == null) {
                throw new ApiError(404, "Disaster not found.");
            }
            Api.requireAccess(user, disaster.getBarangay().getBarangayId());
            if (!disaster.isActive()) {
                throw new ApiError(400, disaster.getName() + " is closed. Reopen it on the Disasters screen first.");
            }
            requireUniqueFaced(connection, disasterId, faced, null);

            String householdId = Database.nextId(connection, "households", "beneficiary_id", "HH");
            List<FamilyMember> members = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                MemberRow row = rows.get(i);
                members.add(new FamilyMember(householdId + "-M" + (i + 1), row.name, row.age,
                        row.senior, row.infant, row.pwd, row.pregnant));
            }
            String headName = rows.get(headIndex).name;
            Beneficiaries household = homeless
                    ? controller.registerHomelessBeneficiary(householdId, headName, needs, disaster, members, user)
                    : controller.registerBeneficiary(householdId, headName, address, needs, disaster, members, user);
            controller.setHouseholdHead(household, members.get(headIndex).getMemberId(), user);
            household.setDamageLevel(optional(form, "damage"));
            household.setEvacuationStatus(optional(form, "evacuation"));
            household.setFacedNumber(faced);

            HouseholdRepository.insert(connection, household);
            audits.record(connection, user, "Beneficiaries", disaster.getBarangay().getBarangayId(),
                    "Household registered", householdId, null, describe(household), disaster.getName(), null, null);
            return householdId;
        });
        Http.sendJson(exchange, 201, Json.obj("ok", true, "id", id));
    }

    /**
     * Edits a household: details, members (update, add, remove) and the household head.
     * Members left out of the form are removed; the model refuses to remove the head or the last member.
     */
    void update(HttpExchange exchange, UserAccount user) throws Exception {
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String householdId = Api.required(form, "id", "Household");
        boolean homeless = "1".equals(Api.text(form, "homeless"));
        String address = Api.text(form, "address");
        if (!homeless && address.isEmpty()) {
            throw new ApiError(400, "Address is required (tick \"No fixed address\" for a homeless household).");
        }
        String faced = optional(form, "facedNumber");
        List<MemberRow> rows = readMembers(form);
        int headIndex = headIndex(form, rows.size());

        Api.inTransaction((Connection connection) -> {
            Beneficiaries household = HouseholdRepository.load(connection, householdId, null);
            if (household == null) {
                throw new ApiError(404, "Household not found.");
            }
            Disaster disaster = household.getDisaster();
            Api.requireAccess(user, disaster.getBarangay().getBarangayId());
            controller.requireCanEdit(household, user);
            requireUniqueFaced(connection, disaster.getDisasterId(), faced, householdId);
            String before = describe(household);

            // Update existing members and add new ones.
            int next = nextMemberNumber(household);
            List<String> keep = new ArrayList<>();
            String headId = null;
            for (int i = 0; i < rows.size(); i++) {
                MemberRow row = rows.get(i);
                FamilyMember member = row.id.isEmpty() ? null : household.findMember(row.id);
                if (member == null) {
                    member = new FamilyMember(householdId + "-M" + next++, row.name, row.age,
                            row.senior, row.infant, row.pwd, row.pregnant);
                    controller.addFamilyMember(household, member, user);
                } else {
                    member.setFullName(row.name);
                    member.setAge(row.age);
                    member.setSeniorCitizen(row.senior);
                    member.setInfant(row.infant);
                    member.setPersonWithDisability(row.pwd);
                    member.setPregnant(row.pregnant);
                }
                keep.add(member.getMemberId());
                if (i == headIndex) {
                    headId = member.getMemberId();
                }
            }
            controller.setHouseholdHead(household, headId, user);
            // Remove members that were taken out of the form.
            for (FamilyMember member : household.getFamilyMembers()) {
                if (!keep.contains(member.getMemberId())) {
                    controller.removeFamilyMember(household, member.getMemberId(), user);
                }
            }
            household.setAddress(homeless ? disaster.getBarangay().getName() : address);
            household.setAssistanceRequirements(Api.text(form, "needs"));
            household.setDamageLevel(optional(form, "damage"));
            household.setEvacuationStatus(optional(form, "evacuation"));
            household.setFacedNumber(faced);

            HouseholdRepository.update(connection, household);
            audits.record(connection, user, "Beneficiaries", disaster.getBarangay().getBarangayId(),
                    "Household updated", householdId, before, describe(household),
                    Api.text(form, "reason").isEmpty() ? null : Api.text(form, "reason"), null, null);
            return null;
        });
        Http.sendJson(exchange, 200, Json.obj("ok", true));
    }

    private static String describe(Beneficiaries household) {
        int size = household.getHouseholdSize();
        return size + (size == 1 ? " member" : " members") + " · head " + household.getName()
                + (household.getFacedNumber() == null ? "" : " · FACED " + household.getFacedNumber())
                + " · priority " + household.getPriorityScore();
    }
}
