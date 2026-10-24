package LIB;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Beneficiaries {
    public static final String DAMAGE_TOTAL = "Totally damaged";
    public static final String DAMAGE_PARTIAL = "Partially damaged";

    private final String beneficiaryId;
    private String name;
    private String address;
    private String assistanceRequirements;
    private String damageLevel;
    private Disaster disaster;
    private final List<FamilyMember> familyMembers = new ArrayList<>();
    private FamilyMember householdHead;
    private String contactNumber = "";
    private String purok = "";
    private boolean homeless;
    private String evacuationStatus = "";
    private String evacuationLocation = "";
    private String assessmentNote = "";
    private final Map<String, Integer> needs = new LinkedHashMap<>();
    private final Map<String, Integer> released = new LinkedHashMap<>();
    private LocalDateTime registeredAt;
    private String registeredByName = "";
    private boolean assisted;

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

    public int getPregnantCount() {
        return countMembers(MemberCategory.PREGNANT);
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

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber == null ? "" : contactNumber.trim();
    }

    public String getPurok() {
        return purok;
    }

    public void setPurok(String purok) {
        this.purok = purok == null ? "" : purok.trim();
    }

    public boolean isHomeless() {
        return homeless;
    }

    public void setHomeless(boolean homeless) {
        this.homeless = homeless;
    }

    public String getEvacuationStatus() {
        return evacuationStatus;
    }

    public void setEvacuationStatus(String evacuationStatus) {
        this.evacuationStatus = evacuationStatus == null ? "" : evacuationStatus.trim();
    }

    public String getEvacuationLocation() {
        return evacuationLocation;
    }

    public void setEvacuationLocation(String evacuationLocation) {
        this.evacuationLocation = evacuationLocation == null ? "" : evacuationLocation.trim();
    }

    public String getAssessmentNote() {
        return assessmentNote;
    }

    public void setAssessmentNote(String assessmentNote) {
        this.assessmentNote = assessmentNote == null ? "" : assessmentNote.trim();
    }

    /** Requested assistance by inventory category, e.g. Food -> 2. */
    public Map<String, Integer> getNeeds() {
        return needs;
    }

    public void setNeed(String category, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Requested quantity cannot be negative.");
        }
        if (quantity == 0) {
            needs.remove(category);
        } else {
            needs.put(category, quantity);
        }
    }

    /** Quantities already released to this household by inventory category (filled from the database). */
    public Map<String, Integer> getReleased() {
        return released;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public String getRegisteredByName() {
        return registeredByName;
    }

    void setRegistered(LocalDateTime when, String by) {
        this.registeredAt = when;
        this.registeredByName = by == null ? "" : by;
    }

    public boolean isAssisted() {
        return assisted;
    }

    void setAssisted(boolean assisted) {
        this.assisted = assisted;
    }

    public boolean isVulnerable() {
        for (FamilyMember member : familyMembers) {
            if (member.isVulnerable()) {
                return true;
            }
        }
        return false;
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
