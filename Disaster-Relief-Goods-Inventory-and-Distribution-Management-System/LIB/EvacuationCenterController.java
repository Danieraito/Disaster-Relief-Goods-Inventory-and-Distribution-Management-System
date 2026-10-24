package LIB;

public class EvacuationCenterController {
    public EvacuationCenter createEvacuationCenter(String centerId, String name, String address,
            int capacity, Barangay barangay, UserAccount creator) {
        if (creator == null || !creator.isActive() || !creator.canEnterRecords()
                || !creator.canAccessBarangay(barangay)) {
            throw new SecurityException("User cannot create an evacuation center in this barangay workspace.");
        }
        return new EvacuationCenter(centerId, name, address, capacity, barangay);
    }
}
