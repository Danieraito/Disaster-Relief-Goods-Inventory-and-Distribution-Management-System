package LIB;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class DonationController {
    private final DonationRepository repository = new DonationRepository();
    private final DonorRepository donors = new DonorRepository();

    public Donations createDonation(String donationId, Donor donor, String donationDate,
            String receivingLocation, String deliveryReference, UserAccount submitter) {
        requireRecordEntryPermission(submitter);
        return new Donations(donationId, donor, donationDate, receivingLocation, deliveryReference);
    }

    public void addDonationItem(Donations donation, TransactionItem item, UserAccount submitter) {
        requireRecordEntryPermission(submitter);
        Objects.requireNonNull(donation, "donation").addItem(item);
    }

    public Donations submitDonation(Donations donation, UserAccount submitter) {
        return Objects.requireNonNull(donation, "donation").submit(submitter);
    }

    public void approveDonation(Donations donation, UserAccount approver) {
        Objects.requireNonNull(donation, "donation").approve(approver);
    }

    public void rejectDonation(Donations donation, UserAccount approver) {
        Objects.requireNonNull(donation, "donation").reject(approver);
    }

    // ----- workflows used by the Donations screens -----

    /** True when this category needs an expiry date on every received line. */
    public static boolean needsExpiry(String category) {
        return "Food".equals(category) || "Water".equals(category) || "Medicine".equals(category);
    }

    /** Checks every form rule for a donation before anything is saved. */
    public void validate(Donor donor, LocalDate date, String location, List<DonationRepository.Line> lines,
            List<Inventory> inventory) {
        if (donor == null) {
            throw new IllegalArgumentException("Select a donor, or add a new donor first.");
        }
        if (date == null) {
            throw new IllegalArgumentException("Enter the date received.");
        }
        if (date.isAfter(Time.today())) {
            throw new IllegalArgumentException("The received date cannot be in the future.");
        }
        if (location == null || location.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter where the goods were received.");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Add at least one donated item.");
        }
        for (DonationRepository.Line line : lines) {
            Inventory item = null;
            for (Inventory candidate : inventory) {
                if (candidate.getItemId().equals(line.itemId)) {
                    item = candidate;
                }
            }
            if (item == null) {
                throw new IllegalArgumentException("Choose an item for every donation line.");
            }
            if (line.quantity <= 0) {
                throw new IllegalArgumentException("Quantity for " + item.getItemName() + " must be greater than 0.");
            }
            if (needsExpiry(item.getCategory()) && line.expiry == null) {
                throw new IllegalArgumentException("Enter the expiry date for " + item.getItemName()
                        + ". Food, water and medicine batches need one.");
            }
            if (line.expiry != null && line.expiry.isBefore(date)) {
                throw new IllegalArgumentException(item.getItemName() + " is already expired and cannot be received.");
            }
        }
    }

    public Donations record(String existingId, Donor donor, Barangay workspace, LocalDate date, String location,
            String deliveryReference, String donorRepresentative, String notes, List<DonationRepository.Line> lines,
            List<Inventory> inventory, DonationRepository.Mode mode, UserAccount user) throws SQLException {
        requireRecordEntryPermission(user);
        if (mode == DonationRepository.Mode.POST && !user.canManageRecords()) {
            throw new SecurityException("Encoders can save drafts or submit donations for approval only.");
        }
        validate(donor, date, location, lines, inventory);
        return repository.save(existingId, donor, workspace, date, location.trim(),
                deliveryReference == null ? "" : deliveryReference.trim(), donorRepresentative, notes, lines, mode, user);
    }

    public Donations approve(String donationId, UserAccount approver) throws SQLException {
        requireApprover(approver);
        return repository.approve(donationId, approver);
    }

    public void reject(String donationId, String reason, UserAccount approver) throws SQLException {
        requireApprover(approver);
        repository.reject(donationId, reason, approver);
    }

    public void submitDraft(String donationId, UserAccount user) throws SQLException {
        requireRecordEntryPermission(user);
        repository.submitDraft(donationId, user);
    }

    public Donations find(String donationId, UserAccount user) throws SQLException {
        Donations donation = repository.find(donationId);
        if (donation != null && (user == null || !user.canAccess(donation))) {
            throw new SecurityException("User cannot open donations of this barangay workspace.");
        }
        return donation;
    }

    public List<DonationRepository.Row> list(Barangay workspace, DonationRepository.Filter filter, UserAccount user)
            throws SQLException {
        if (user == null || !user.canAccessBarangay(workspace)) {
            throw new SecurityException("User cannot open donations of this barangay workspace.");
        }
        return repository.list(workspace.getBarangayId(), filter);
    }

    public List<DonationRepository.Movement> movements(Donations donation) throws SQLException {
        return repository.movements(donation);
    }

    public List<DonorRepository.Row> donorRows(Barangay workspace) throws SQLException {
        return donors.findAll(workspace.getBarangayId());
    }

    public Donor saveDonor(String donorId, String name, Donor.Type type, String contactPerson, String phone,
            String emailOrAddress, Barangay workspace, UserAccount user) throws SQLException {
        requireRecordEntryPermission(user);
        if (name == null || name.trim().isEmpty() || type == null || contactPerson == null
                || contactPerson.trim().isEmpty() || phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("Donor name, type, contact person and contact number are required.");
        }
        String details = phone.trim() + (emailOrAddress == null || emailOrAddress.trim().isEmpty() ? ""
                : " · " + emailOrAddress.trim());
        return donors.save(new Donor(donorId, name.trim(), type, contactPerson.trim(), details), workspace, user);
    }

    private void requireApprover(UserAccount user) {
        if (user == null || !user.isActive() || !user.canManageRecords()) {
            throw new SecurityException("Only a Barangay Official or Admin can approve or reject donations.");
        }
    }

    private void requireRecordEntryPermission(UserAccount user) {
        if (user == null || !user.isActive() || !user.canEnterRecords()) {
            throw new SecurityException("User does not have permission to enter donation records.");
        }
    }
}
