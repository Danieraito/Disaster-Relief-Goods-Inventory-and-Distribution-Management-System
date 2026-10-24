package LIB;

public class Barangay {
    private final String barangayId;
    private String name;
    private String municipality;
    private String province;

    public Barangay(String barangayId, String name, String municipality, String province) {
        this.barangayId = requireText(barangayId, "barangayId");
        this.name = requireText(name, "name");
        this.municipality = requireText(municipality, "municipality");
        this.province = requireText(province, "province");
    }

    public String getBarangayId() {
        return barangayId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = requireText(name, "name");
    }

    public String getMunicipality() {
        return municipality;
    }

    public void setMunicipality(String municipality) {
        this.municipality = requireText(municipality, "municipality");
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = requireText(province, "province");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        return value.trim();
    }
}
