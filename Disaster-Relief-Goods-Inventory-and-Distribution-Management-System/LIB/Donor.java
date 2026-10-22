package LIB;

public class Donor {
    public enum Type {
        INDIVIDUAL,
        ORGANIZATION,
        AGENCY
    }

    private String donorId;
    private String name;
    private Type type;
    private String contactPerson;
    private String contactDetails;

    public Donor(String donorId, String name, Type type, String contactPerson, String contactDetails) {
        this.donorId = donorId;
        this.name = name;
        this.type = type;
        this.contactPerson = contactPerson;
        this.contactDetails = contactDetails;
    }

    public String getDonorId() {
        return donorId;
    }

    public void setDonorId(String donorId) {
        this.donorId = donorId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }

    public String getContactDetails() {
        return contactDetails;
    }

    public void setContactDetails(String contactDetails) {
        this.contactDetails = contactDetails;
    }
}
