public class BeneficiaryController {
    public Beneficiaries registerBeneficiary(String beneficiaryId, String name, String address,
            int householdSize, String assistanceRequirements, Disaster disaster) {
        if (disaster == null) {
            throw new IllegalArgumentException("Beneficiary must be linked to a disaster.");
        }
        return new Beneficiaries(
                beneficiaryId, name, address, householdSize, assistanceRequirements, disaster);
    }
}
