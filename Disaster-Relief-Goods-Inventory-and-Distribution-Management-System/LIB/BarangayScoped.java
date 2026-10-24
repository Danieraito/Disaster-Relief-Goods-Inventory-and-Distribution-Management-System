package LIB;

public interface BarangayScoped {
    Barangay getBarangay();

    default boolean belongsToBarangay(Barangay barangay) {
        Barangay ownBarangay = getBarangay();
        return ownBarangay != null && barangay != null
                && ownBarangay.getBarangayId().equals(barangay.getBarangayId());
    }

    default boolean sharesBarangayWith(BarangayScoped other) {
        if (other == null) {
            return false;
        }
        Barangay ownBarangay = getBarangay();
        Barangay otherBarangay = other.getBarangay();
        if (ownBarangay == null || otherBarangay == null) {
            return ownBarangay == otherBarangay;
        }
        return ownBarangay.getBarangayId().equals(otherBarangay.getBarangayId());
    }
}
