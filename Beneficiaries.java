public class Beneficiaries {
    private String beneficiaryId;
    private String name;
    private String address;
    private int householdSize;
    private String assistanceRequirements;
    private Disaster disaster;

    public Beneficiaries(String beneficiaryId, String name, String address, int householdSize,
            String assistanceRequirements, Disaster disaster) {
        this.beneficiaryId = beneficiaryId;
        this.name = name;
        this.address = address;
        this.householdSize = householdSize;
        this.assistanceRequirements = assistanceRequirements;
        this.disaster = disaster;
    }

    public String getBeneficiaryId() {
        return beneficiaryId;
    }

    public void setBeneficiaryId(String beneficiaryId) {
        this.beneficiaryId = beneficiaryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getHouseholdSize() {
        return householdSize;
    }

    public void setHouseholdSize(int householdSize) {
        this.householdSize = householdSize;
    }

    public String getAssistanceRequirements() {
        return assistanceRequirements;
    }

    public void setAssistanceRequirements(String assistanceRequirements) {
        this.assistanceRequirements = assistanceRequirements;
    }

    public Disaster getDisaster() {
        return disaster;
    }

    public void setDisaster(Disaster disaster) {
        this.disaster = disaster;
    }
}
