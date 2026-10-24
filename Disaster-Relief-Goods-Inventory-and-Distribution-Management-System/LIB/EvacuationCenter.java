package LIB;

import java.util.Objects;

public class EvacuationCenter implements BarangayScoped {
    private final String centerId;
    private String name;
    private String address;
    private int capacity;
    private final Barangay barangay;

    public EvacuationCenter(String centerId, String name, String address, int capacity, Barangay barangay) {
        this.centerId = requireText(centerId, "centerId");
        this.name = requireText(name, "name");
        this.address = requireText(address, "address");
        if (capacity <= 0) {
            throw new IllegalArgumentException("Evacuation center capacity must be positive.");
        }
        this.capacity = capacity;
        this.barangay = Objects.requireNonNull(barangay, "barangay");
    }

    public String getCenterId() {
        return centerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = requireText(name, "name");
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = requireText(address, "address");
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Evacuation center capacity must be positive.");
        }
        this.capacity = capacity;
    }

    public Barangay getBarangay() {
        return barangay;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        return value.trim();
    }
}
