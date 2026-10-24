package LIB;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class DistributionController {
    private final DistributionRepository repository = new DistributionRepository();

    public Distribution createDistribution(String distributionId, String distributionDate, Disaster disaster,
            Beneficiaries beneficiary, String releaseLocation, String receiptReference, UserAccount submitter) {
        requireRecordEntryPermission(submitter);
        return new Distribution(distributionId, distributionDate, disaster,
                beneficiary, releaseLocation, receiptReference);
    }

    public Distribution createDistribution(String distributionId, String distributionDate, Disaster disaster,
            Beneficiaries beneficiary, EvacuationCenter evacuationCenter, String releaseLocation,
            String receiptReference, UserAccount submitter) {
        requireRecordEntryPermission(submitter);
        if (evacuationCenter == null || !submitter.canAccessBarangay(evacuationCenter.getBarangay())) {
            throw new SecurityException("User cannot distribute to this evacuation center.");
        }
        return new Distribution(distributionId, distributionDate, disaster, beneficiary,
                evacuationCenter, releaseLocation, receiptReference);
    }

    public void addDistributionItem(Distribution distribution, TransactionItem item, UserAccount submitter) {
        requireRecordEntryPermission(submitter);
        Objects.requireNonNull(distribution, "distribution").addItem(item);
    }

    public Distribution submitDistribution(Distribution distribution, UserAccount submitter) {
        return Objects.requireNonNull(distribution, "distribution").submit(submitter);
    }

    public void approveDistribution(Distribution distribution, UserAccount approver) {
        Objects.requireNonNull(distribution, "distribution").approve(approver);
    }

    public void rejectDistribution(Distribution distribution, UserAccount approver) {
        Objects.requireNonNull(distribution, "distribution").reject(approver);
    }

    // ----- workflows used by the Distribution screens -----

    /** Looks for a reason this release cannot go ahead (stock, expiry or duplicate assistance). */
    public DistributionRepository.Problem check(DistributionRepository.Request request, UserAccount user)
            throws SQLException {
        requireRecordEntryPermission(user);
        validateRequest(request);
        return repository.check(request);
    }

    /** Writes a blocked attempt to the audit log (no stock moves) and returns its VAL reference. */
    public String logBlocked(DistributionRepository.Problem problem, DistributionRepository.Request request,
            UserAccount user) throws SQLException {
        String context = problem.existingId != null ? "Already released under " + problem.existingId : null;
        return new AuditRepository().recordBlockedAttempt(user,
                request.disaster.getBarangay().getBarangayId(), problem.message, problem.title, context);
    }

    public Distribution record(DistributionRepository.Request request, DistributionRepository.Mode mode,
            UserAccount user) throws SQLException {
        requireRecordEntryPermission(user);
        if (mode == DistributionRepository.Mode.POST && !user.canApproveDistributions()) {
            throw new SecurityException("Encoders can save drafts or submit releases for approval only.");
        }
        validateRequest(request);
        return repository.record(request, mode, user);
    }

    public void submitDraft(String distributionId, UserAccount user) throws SQLException {
        requireRecordEntryPermission(user);
        repository.submitDraft(distributionId, user);
    }

    public Distribution approve(String distributionId, UserAccount approver) throws SQLException {
        requireApprover(approver);
        return repository.approve(distributionId, approver);
    }

    public void reject(String distributionId, String reason, UserAccount approver) throws SQLException {
        requireApprover(approver);
        repository.reject(distributionId, reason, approver);
    }

    public Distribution find(String distributionId, UserAccount user) throws SQLException {
        Distribution distribution = repository.find(distributionId);
        if (distribution != null
                && (user == null || !user.canAccessBarangay(distribution.getDisaster().getBarangay()))) {
            throw new SecurityException("User cannot open distributions of this barangay workspace.");
        }
        return distribution;
    }

    public List<DistributionRepository.Row> list(Barangay workspace, DistributionRepository.Filter filter,
            UserAccount user) throws SQLException {
        if (user == null || !user.canAccessBarangay(workspace)) {
            throw new SecurityException("User cannot open distributions of this barangay workspace.");
        }
        return repository.list(workspace.getBarangayId(), filter);
    }

    public List<DistributionRepository.Movement> movements(Distribution distribution) throws SQLException {
        return repository.movements(distribution);
    }

    private void validateRequest(DistributionRepository.Request request) {
        if (request == null || request.disaster == null) {
            throw new IllegalArgumentException("Select the disaster for this release.");
        }
        if (!request.disaster.isActive()) {
            throw new IllegalStateException("This disaster is closed. Releases can only be made for active events.");
        }
        if (request.householdId == null && request.centerId == null) {
            throw new IllegalArgumentException("Select an evacuation center or a household to receive the goods.");
        }
        if (request.date == null || request.date.isAfter(Time.today())) {
            throw new IllegalArgumentException("Enter a distribution date that is not in the future.");
        }
        if (request.lines.isEmpty()) {
            throw new IllegalArgumentException("Add at least one item to release.");
        }
        for (DistributionRepository.Line line : request.lines) {
            if (line.itemId == null || line.batchId == null) {
                throw new IllegalArgumentException("Choose an item and its stock batch for every line.");
            }
            if (line.quantity <= 0) {
                throw new IllegalArgumentException("Release quantities must be whole numbers greater than 0.");
            }
        }
    }

    private void requireApprover(UserAccount user) {
        if (user == null || !user.isActive() || !user.canApproveDistributions()) {
            throw new SecurityException("Only a Barangay Official or Admin can confirm or reject releases.");
        }
    }

    private void requireRecordEntryPermission(UserAccount user) {
        if (user == null || !user.isActive() || !user.canEnterRecords()) {
            throw new SecurityException("User does not have permission to enter distribution records.");
        }
    }
}
