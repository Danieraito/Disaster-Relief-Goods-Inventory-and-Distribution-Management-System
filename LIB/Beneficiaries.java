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
                    || category == MemberCategory.PWD && member.isPersonWithDisability();
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
        PWD
    }
}
