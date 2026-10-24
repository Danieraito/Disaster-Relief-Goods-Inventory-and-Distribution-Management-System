package LIB;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BeneficiaryController {
    private final BeneficiaryRepository repository = new BeneficiaryRepository();

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

    // ----- workflows used by the Beneficiaries screens -----

    /** Registers a household after checking every rule of the registration form. */
    public Beneficiaries registerHousehold(Disaster disaster, String headName, String contact, String address,
            boolean homeless, String purok, int declaredMembers, List<FamilyMember> members, String damage,
            String evacuationStatus, String evacuationLocation, String note, Map<String, Integer> needs,
            UserAccount editor) throws SQLException {
        requireWorkspaceEditor(disaster, editor);
        if (!disaster.isActive()) {
            throw new IllegalStateException("This disaster is closed. Households can only be registered for active events.");
        }
        if (headName == null || headName.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter the head of household.");
        }
        if (contact == null || contact.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a contact number for the household.");
        }
        if (!homeless && (address == null || address.trim().isEmpty())) {
            throw new IllegalArgumentException("Enter the complete address, or mark the household as homeless.");
        }
        if (!homeless && (purok == null || purok.trim().isEmpty())) {
            throw new IllegalArgumentException("Select the purok.");
        }
        if (members == null || members.isEmpty()) {
            throw new IllegalArgumentException("Add at least one confirmed family member.");
        }
        if (declaredMembers != members.size()) {
            throw new IllegalArgumentException("Number of members (" + declaredMembers
                    + ") must match the member records (" + members.size() + ").");
        }
        if (damage == null || damage.isEmpty() || evacuationStatus == null || evacuationStatus.isEmpty()) {
            throw new IllegalArgumentException("Select the house damage and evacuation status.");
        }
        if (needs == null || needs.isEmpty()) {
            throw new IllegalArgumentException("Select at least one assistance item for the household.");
        }
        return repository.register(disaster, headName, contact, homeless ? "" : address, homeless,
                homeless ? disaster.getBarangay().getName() : purok, members, damage, evacuationStatus,
                evacuationLocation, note, needs, editor);
    }

    public BeneficiaryRepository.Page page(Disaster disaster, BeneficiaryRepository.Filter filter, int page,
            int size, UserAccount user) throws SQLException {
        requireWorkspaceReader(disaster, user);
        return repository.findPage(disaster.getDisasterId(), filter, page, size);
    }

    public int[] tabCounts(Disaster disaster, UserAccount user) throws SQLException {
        requireWorkspaceReader(disaster, user);
        return repository.tabCounts(disaster.getDisasterId());
    }

    public List<String> puroks(Disaster disaster) throws SQLException {
        return repository.puroks(disaster.getDisasterId());
    }

    public Beneficiaries find(String householdId, Disaster disaster, UserAccount user) throws SQLException {
        Beneficiaries household = repository.findById(householdId, disaster);
        if (household != null) {
            requireWorkspaceReader(household.getDisaster(), user);
        }
        return household;
    }

    public List<BeneficiaryRepository.Row> findSimilar(Disaster disaster, String headName, String address)
            throws SQLException {
        return repository.findSimilar(disaster.getDisasterId(), headName, address);
    }

    public void updateHousehold(Beneficiaries household, String contact, String damage, String evacuationStatus,
            String reason, UserAccount editor) throws SQLException {
        requireWorkspaceEditor(household.getDisaster(), editor);
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter the reason for this update.");
        }
        if (contact == null || contact.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a contact number for the household.");
        }
        repository.updateProfile(household, contact, damage, evacuationStatus, reason.trim(), editor);
    }

    private void requireWorkspaceReader(Disaster disaster, UserAccount user) {
        if (disaster == null || user == null || !user.isActive() || !user.canAccessBarangay(disaster.getBarangay())) {
            throw new SecurityException("User cannot open household records in this barangay workspace.");
        }
    }

    private void requireWorkspaceEditor(Disaster disaster, UserAccount editor) {
        if (disaster == null || editor == null || !editor.isActive()
                || !editor.canEnterRecords() || !editor.canAccessBarangay(disaster.getBarangay())) {
            throw new SecurityException("User cannot edit household records in this barangay workspace.");
        }
    }
}
