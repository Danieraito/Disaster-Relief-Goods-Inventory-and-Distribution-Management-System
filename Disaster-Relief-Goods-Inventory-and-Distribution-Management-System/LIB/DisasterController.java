package LIB;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class DisasterController {
    private final DisasterRepository repository = new DisasterRepository();

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

    // ----- workflows used by the Disasters screens -----

    public Disaster addDisaster(String name, String type, LocalDate date, String severity, String areas,
            String location, String description, String assessmentContact, String evacuationSite,
            Barangay barangay, UserAccount user) throws SQLException {
        requireAccess(user, barangay);
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a disaster name.");
        }
        if (date == null) {
            throw new IllegalArgumentException("Enter the date of occurrence.");
        }
        if (date.isAfter(Time.today())) {
            throw new IllegalArgumentException("The date of occurrence cannot be in the future.");
        }
        if (areas == null || areas.trim().isEmpty()) {
            throw new IllegalArgumentException("Select at least one affected area.");
        }
        if (location == null || location.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter the location or affected addresses.");
        }
        return repository.create(name, type, date, severity, areas, location, description, assessmentContact,
                evacuationSite, barangay, user);
    }

    public List<Disaster> list(Barangay workspace, UserAccount user) throws SQLException {
        requireAccess(user, workspace);
        return repository.findByBarangay(workspace.getBarangayId());
    }

    public Disaster find(String disasterId, UserAccount user) throws SQLException {
        Disaster disaster = repository.findById(disasterId);
        if (disaster != null) {
            requireAccess(user, disaster.getBarangay());
        }
        return disaster;
    }

    public void closeDisaster(Disaster disaster, String reason, UserAccount user) throws SQLException {
        requireAccess(user, disaster.getBarangay());
        if (!user.canManageRecords()) {
            throw new SecurityException("Only a Barangay Official or Admin can close a disaster event.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a reason for closing the event.");
        }
        repository.close(disaster, reason.trim(), user);
    }

    public DisasterStats stats(Disaster disaster) throws SQLException {
        return repository.stats(disaster.getDisasterId());
    }

    public Map<String, int[]> summaries(Barangay workspace) throws SQLException {
        return repository.summaryByDisaster(workspace.getBarangayId());
    }

    private void requireAccess(UserAccount user, Barangay barangay) {
        if (user == null || !user.isActive() || !user.canAccessBarangay(barangay)) {
            throw new SecurityException("User cannot modify disasters in this barangay workspace.");
        }
    }
}
