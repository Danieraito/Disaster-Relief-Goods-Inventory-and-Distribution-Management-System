package LIB;

public class DisasterController {
    public Disaster createDisaster(String disasterId, String name, String type,
            String location, String date, String status, Barangay barangay, UserAccount user) {
        requireAccess(user, barangay);
        return new Disaster(disasterId, name, type, location, date, status, barangay);
    }

    public void updateDisasterStatus(Disaster disaster, String status, UserAccount user) {
        if (disaster == null) {
            throw new IllegalArgumentException("Disaster cannot be null.");
        }
        requireAccess(user, disaster.getBarangay());
        disaster.setStatus(status);
    }

    private void requireAccess(UserAccount user, Barangay barangay) {
        if (user == null || !user.isActive() || !user.canAccessBarangay(barangay)) {
            throw new SecurityException("User cannot modify disasters in this barangay workspace.");
        }
    }
}
