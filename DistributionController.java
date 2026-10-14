import java.util.Objects;

public class DistributionController {
    public Distribution submitDistribution(Distribution distribution, UserAccount submitter) {
        return Objects.requireNonNull(distribution, "distribution").submit(submitter);
    }

    public int approveDistribution(Distribution distribution, UserAccount approver) {
        return Objects.requireNonNull(distribution, "distribution").approve(approver);
    }

    public void rejectDistribution(Distribution distribution, UserAccount approver) {
        Objects.requireNonNull(distribution, "distribution").reject(approver);
    }
}
