package LIB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Beneficiaries {
    private final String beneficiaryId;
    private String name;
    private String address;
    private String assistanceRequirements;
    private String damageLevel;
    private String evacuationStatus;
    private String facedNumber;
    private Disaster disaster;
    private final List<FamilyMember> familyMembers = new ArrayList<>();
    private FamilyMember householdHead;

    public Beneficiaries(String beneficiaryId, String name, String address,
            String assistanceRequirements, Disaster disaster, List<FamilyMember> familyMembers) {
        this.beneficiaryId = requireText(beneficiaryId, "beneficiaryId");
        this.name = requireText(name, "name");
        this.address = requireText(address, "address");
        this.assistanceRequirements = assistanceRequirements;
        this.disaster = Objects.requireNonNull(disaster, "disaster");
        if (familyMembers == null || familyMembers.isEmpty()) {
            throw new IllegalArgumentException("A household must have at least one confirmed family member.");
        }
        for (FamilyMember member : familyMembers) {
            addFamilyMember(member);
        }
    }

    public String getBeneficiaryId() {
        return beneficiaryId;
    }

    public String getName() {
        return householdHead == null ? name : householdHead.getFullName();
    }

    public void setName(String name) {
        String validName = requireText(name, "name");
        if (householdHead != null) {
            householdHead.setFullName(validName);
        }
        this.name = validName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = requireText(address, "address");
    }

    public String getAssistanceRequirements() {
        return assistanceRequirements;
    }

    public void setAssistanceRequirements(String assistanceRequirements) {
        this.assistanceRequirements = assistanceRequirements;
    }

    public String getDamageLevel() {
        return damageLevel;
    }

    public void setDamageLevel(String damageLevel) {
        this.damageLevel = damageLevel;
    }

    public String getEvacuationStatus() {
        return evacuationStatus;
    }

    public void setEvacuationStatus(String evacuationStatus) {
        this.evacuationStatus = evacuationStatus;
    }

    /** Number on the family's FACED card (Family Access Card in Emergencies and Disasters, formerly DAFAC). */
    public String getFacedNumber() {
        return facedNumber;
    }

    public void setFacedNumber(String facedNumber) {
        this.facedNumber = facedNumber == null || facedNumber.trim().isEmpty() ? null : facedNumber.trim();
    }

    public int getPregnantCount() {
        return countMembers(MemberCategory.PREGNANT);
    }

    /**
     * How urgently this household should be served; higher comes first. See priorityScore.
     */
    public int getPriorityScore() {
        return priorityScore(getSeniorCitizenCount(), getPwdCount(), getInfantCount(), getPregnantCount(),
                damageLevel, evacuationStatus, getHouseholdSize());
    }

    /**
     * The priority rule, kept in one place so lists and reports use the same numbers:
     * 3 points per senior, PWD, infant or pregnant member; 4 for a totally damaged house,
     * 2 for partially damaged; 2 if evacuated; 1 point for every 2 members (up to 5).
     */
    public static int priorityScore(int seniors, int pwd, int infants, int pregnant, String damageLevel,
            String evacuationStatus, int householdSize) {
        int score = 3 * (seniors + pwd + infants + pregnant);
        if ("Totally damaged".equalsIgnoreCase(damageLevel)) {
            score += 4;
        } else if ("Partially damaged".equalsIgnoreCase(damageLevel)) {
            score += 2;
        }
        if ("Evacuated".equalsIgnoreCase(evacuationStatus)) {
            score += 2;
        }
        score += Math.min(householdSize / 2, 5);
        return score;
    }

    /** Removes a member recorded by mistake. The household head and the last member cannot be removed. */
    public void removeFamilyMember(String memberId) {
        if (householdHead != null && householdHead.getMemberId().equals(memberId)) {
            throw new IllegalArgumentException("Choose another household head before removing this member.");
        }
        if (familyMembers.size() <= 1) {
            throw new IllegalArgumentException("A household must keep at least one confirmed family member.");
        }
        if (!familyMembers.removeIf(member -> member.getMemberId().equals(memberId))) {
            throw new IllegalArgumentException("Family member not found in this household.");
        }
    }

    public FamilyMember findMember(String memberId) {
        for (FamilyMember member : familyMembers) {
            if (member.getMemberId().equals(memberId)) {
                return member;
            }
        }
        return null;
    }

    public Disaster getDisaster() {
        return disaster;
    }

    public void setDisaster(Disaster disaster) {
        this.disaster = Objects.requireNonNull(disaster, "disaster");
    }

    public int getHouseholdSize() {
        return familyMembers.size();
    }

    public int getSeniorCitizenCount() {
        return countMembers(MemberCategory.SENIOR);
    }

    public int getInfantCount() {
        return countMembers(MemberCategory.INFANT);
    }

    public int getPwdCount() {
        return countMembers(MemberCategory.PWD);
    }

    public List<FamilyMember> getFamilyMembers() {
        return Collections.unmodifiableList(new ArrayList<>(familyMembers));
    }

    public FamilyMember getHouseholdHead() {
        return householdHead;
    }

    public void addFamilyMember(FamilyMember member) {
        Objects.requireNonNull(member, "member");
        for (FamilyMember existing : familyMembers) {
            if (existing.getMemberId().equals(member.getMemberId())) {
                throw new IllegalArgumentException("Family member ID already exists in this household.");
            }
        }
        familyMembers.add(member);
    }

    public void setHouseholdHead(String memberId) {
        for (FamilyMember member : familyMembers) {
            if (member.getMemberId().equals(memberId)) {
                householdHead = member;
                name = member.getFullName();
                return;
            }
        }
        throw new IllegalArgumentException("Household head must be a member of this household.");
    }

    private int countMembers(MemberCategory category) {
        int count = 0;
        for (FamilyMember member : familyMembers) {
            boolean matches = category == MemberCategory.SENIOR && member.isSeniorCitizen()
                    || category == MemberCategory.INFANT && member.isInfant()
                    || category == MemberCategory.PWD && member.isPersonWithDisability()
                    || category == MemberCategory.PREGNANT && member.isPregnant();
            if (matches) {
                count++;
            }
        }
        return count;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        return value.trim();
    }

    private enum MemberCategory {
        SENIOR,
        INFANT,
        PWD,
        PREGNANT
    }
}
