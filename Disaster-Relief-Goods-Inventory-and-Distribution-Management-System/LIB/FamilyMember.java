package LIB;

import java.util.ArrayList;
import java.util.List;

public class FamilyMember {
    private final String memberId;
    private String fullName;
    private int age;
    private Integer ageMonths;
    private String sex = "";
    private boolean seniorCitizen;
    private boolean infant;
    private boolean pregnant;
    private boolean personWithDisability;

    public FamilyMember(String memberId, String fullName, int age, boolean seniorCitizen,
            boolean infant, boolean personWithDisability) {
        this.memberId = requireText(memberId, "memberId");
        this.fullName = requireText(fullName, "fullName");
        setAge(age);
        this.seniorCitizen = seniorCitizen;
        this.infant = infant;
        this.personWithDisability = personWithDisability;
    }

    public FamilyMember(String memberId, String fullName, int age, Integer ageMonths, String sex,
            boolean seniorCitizen, boolean infant, boolean pregnant, boolean personWithDisability) {
        this(memberId, fullName, age, seniorCitizen, infant, personWithDisability);
        setAgeMonths(ageMonths);
        setSex(sex);
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

    /** Infants may be recorded in months; null when the age is given in years. */
    public Integer getAgeMonths() {
        return ageMonths;
    }

    public void setAgeMonths(Integer ageMonths) {
        if (ageMonths != null && ageMonths < 0) {
            throw new IllegalArgumentException("Age in months cannot be negative.");
        }
        this.ageMonths = ageMonths;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex == null ? "" : sex.trim();
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

    public boolean isPregnant() {
        return pregnant;
    }

    public void setPregnant(boolean pregnant) {
        this.pregnant = pregnant;
    }

    public boolean isPersonWithDisability() {
        return personWithDisability;
    }

    public void setPersonWithDisability(boolean personWithDisability) {
        this.personWithDisability = personWithDisability;
    }

    public boolean isVulnerable() {
        return seniorCitizen || infant || pregnant || personWithDisability;
    }

    /** "34 years" or "6 months". */
    public String ageLabel() {
        if (ageMonths != null) {
            return ageMonths + (ageMonths == 1 ? " month" : " months");
        }
        return age + (age == 1 ? " year" : " years");
    }

    /** "None", or the categories a person entered, e.g. "Senior citizen, PWD". */
    public String categoryLabel() {
        List<String> labels = new ArrayList<>();
        if (personWithDisability) {
            labels.add("PWD");
        }
        if (seniorCitizen) {
            labels.add("Senior citizen");
        }
        if (pregnant) {
            labels.add("Pregnant");
        }
        if (infant) {
            labels.add("Infant");
        }
        return labels.isEmpty() ? "None" : String.join(", ", labels);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        return value.trim();
    }
}
