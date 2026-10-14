import java.util.Objects;

public class DonationController {
    public Donations submitDonation(Donations donation, UserAccount submitter) {
        return Objects.requireNonNull(donation, "donation").submit(submitter);
    }

    public int approveDonation(Donations donation, UserAccount approver) {
        return Objects.requireNonNull(donation, "donation").approve(approver);
    }

    public void rejectDonation(Donations donation, UserAccount approver) {
        Objects.requireNonNull(donation, "donation").reject(approver);
    }
}
