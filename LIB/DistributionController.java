package LIB;

import java.util.Objects;

public class DistributionController {
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

    private void requireRecordEntryPermission(UserAccount user) {
        if (user == null || !user.isActive() || !user.canEnterRecords()) {
            throw new SecurityException("User does not have permission to enter distribution records.");
        }
    }
}
