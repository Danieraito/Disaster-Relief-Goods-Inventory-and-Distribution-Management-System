package LIB;

import java.util.Objects;

public class DonationController {
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

    private void requireRecordEntryPermission(UserAccount user) {
        if (user == null || !user.isActive() || !user.canEnterRecords()) {
            throw new SecurityException("User does not have permission to enter donation records.");
        }
    }
}
