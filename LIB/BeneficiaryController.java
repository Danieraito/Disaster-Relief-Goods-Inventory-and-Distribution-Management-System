package LIB;

import java.util.List;
import java.util.Objects;

public class BeneficiaryController {
    public Beneficiaries registerBeneficiary(String beneficiaryId, String name, String address,
            String assistanceRequirements, Disaster disaster, List<FamilyMember> confirmedMembers,
            UserAccount editor) {
        requireWorkspaceEditor(disaster, editor);
        return new Beneficiaries(beneficiaryId, name, address, assistanceRequirements,
                disaster, confirmedMembers);
    }

    public Beneficiaries registerHomelessBeneficiary(String beneficiaryId, String name,
            String assistanceRequirements, Disaster disaster, List<FamilyMember> confirmedMembers,
            UserAccount editor) {
        requireWorkspaceEditor(disaster, editor);
        String barangayName = disaster.getBarangay() == null
                ? null
                : disaster.getBarangay().getName();
        if (barangayName == null || barangayName.trim().isEmpty()) {
            throw new IllegalArgumentException("A barangay is required to register a homeless household.");
        }
        return new Beneficiaries(beneficiaryId, name, barangayName,
                assistanceRequirements, disaster, confirmedMembers);
    }

    public void addFamilyMember(Beneficiaries beneficiary, FamilyMember member, UserAccount editor) {
        Objects.requireNonNull(beneficiary, "beneficiary");
        requireWorkspaceEditor(beneficiary.getDisaster(), editor);
        beneficiary.addFamilyMember(member);
    }

    public void setHouseholdHead(Beneficiaries beneficiary, String memberId, UserAccount editor) {
        Objects.requireNonNull(beneficiary, "beneficiary");
        requireWorkspaceEditor(beneficiary.getDisaster(), editor);
        beneficiary.setHouseholdHead(memberId);
    }

    /** Checks that the user may change this household's details and members. */
    public void requireCanEdit(Beneficiaries beneficiary, UserAccount editor) {
        Objects.requireNonNull(beneficiary, "beneficiary");
        requireWorkspaceEditor(beneficiary.getDisaster(), editor);
    }

    public void removeFamilyMember(Beneficiaries beneficiary, String memberId, UserAccount editor) {
        requireCanEdit(beneficiary, editor);
        beneficiary.removeFamilyMember(memberId);
    }

    private void requireWorkspaceEditor(Disaster disaster, UserAccount editor) {
        if (disaster == null || editor == null || !editor.isActive()
                || !editor.canEnterRecords() || !editor.canAccessBarangay(disaster.getBarangay())) {
            throw new SecurityException("User cannot edit household records in this barangay workspace.");
        }
    }
}
