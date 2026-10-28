package LIB;

public class FamilyMember {
    private final String memberId;
    private String fullName;
    private int age;
    private boolean seniorCitizen;
    private boolean infant;
    private boolean personWithDisability;
    private boolean pregnant;

    public FamilyMember(String memberId, String fullName, int age, boolean seniorCitizen,
            boolean infant, boolean personWithDisability) {
        this(memberId, fullName, age, seniorCitizen, infant, personWithDisability, false);
    }

    public FamilyMember(String memberId, String fullName, int age, boolean seniorCitizen,
            boolean infant, boolean personWithDisability, boolean pregnant) {
        this.memberId = requireText(memberId, "memberId");
        this.fullName = requireText(fullName, "fullName");
        setAge(age);
        this.seniorCitizen = seniorCitizen;
        this.infant = infant;
        this.personWithDisability = personWithDisability;
        this.pregnant = pregnant;
    }

    /** Pregnant or lactating, as verified by the barangay (a vulnerable-group category). */
    public boolean isPregnant() {
        return pregnant;
    }

    public void setPregnant(boolean pregnant) {
        this.pregnant = pregnant;
    }

    public String getMemberId() {
        return memberId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = requireText(fullName, "fullName");
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        if (age < 0) {
            throw new IllegalArgumentException("Age cannot be negative.");
        }
        this.age = age;
    }

    public boolean isSeniorCitizen() {
        return seniorCitizen;
    }

    public void setSeniorCitizen(boolean seniorCitizen) {
        this.seniorCitizen = seniorCitizen;
    }

    public boolean isInfant() {
        return infant;
    }

    public void setInfant(boolean infant) {
        this.infant = infant;
    }

    public boolean isPersonWithDisability() {
        return personWithDisability;
    }

    public void setPersonWithDisability(boolean personWithDisability) {
        this.personWithDisability = personWithDisability;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        return value.trim();
    }
}
