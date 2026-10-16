package LIB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Distribution implements BarangayScoped {
    private String distributionId;
    private String distributionDate;
    private Disaster disaster;
    private Beneficiaries beneficiary;
    private EvacuationCenter evacuationCenter;
    private String releaseLocation;
    private String receiptReference;
    private UserAccount releasedBy;
    private final List<TransactionItem> items = new ArrayList<>();
    private TransactionStatus status = TransactionStatus.DRAFT;

    public Distribution(String distributionId, String distributionDate, Disaster disaster,
            Beneficiaries beneficiary, String releaseLocation, String receiptReference) {
        this(distributionId, distributionDate, disaster, beneficiary, null, releaseLocation, receiptReference);
    }

    public Distribution(String distributionId, String distributionDate, Disaster disaster,
            Beneficiaries beneficiary, EvacuationCenter evacuationCenter,
            String releaseLocation, String receiptReference) {
        this.distributionId = distributionId;
        this.distributionDate = distributionDate;
        this.disaster = disaster;
        this.beneficiary = beneficiary;
        this.evacuationCenter = evacuationCenter;
        this.releaseLocation = releaseLocation;
        this.receiptReference = receiptReference;
    }

    public String getDistributionId() {
        return distributionId;
    }

    @Override
    public Barangay getBarangay() {
        return getWorkspace();
    }

    public void setDistributionId(String distributionId) {
        requireDraft();
        this.distributionId = distributionId;
    }

    public String getDistributionDate() {
        return distributionDate;
    }

    public void setDistributionDate(String distributionDate) {
        requireDraft();
        this.distributionDate = distributionDate;
    }

    public Disaster getDisaster() {
        return disaster;
    }

    public void setDisaster(Disaster disaster) {
        requireDraft();
        this.disaster = disaster;
    }

    public Beneficiaries getBeneficiary() {
        return beneficiary;
    }

    public void setBeneficiary(Beneficiaries beneficiary) {
        requireDraft();
        this.beneficiary = beneficiary;
    }

    public EvacuationCenter getEvacuationCenter() {
        return evacuationCenter;
    }

    public void setEvacuationCenter(EvacuationCenter evacuationCenter) {
        requireDraft();
        this.evacuationCenter = evacuationCenter;
    }

    public String getReleaseLocation() {
        return releaseLocation;
    }

    public void setReleaseLocation(String releaseLocation) {
        requireDraft();
        this.releaseLocation = releaseLocation;
    }

    public String getReceiptReference() {
        return receiptReference;
    }

    public void setReceiptReference(String receiptReference) {
        requireDraft();
        this.receiptReference = receiptReference;
    }

    public UserAccount getReleasedBy() {
        return releasedBy;
    }

    public List<TransactionItem> getItems() {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    public void addItem(TransactionItem item) {
        requireDraft();
        items.add(Objects.requireNonNull(item, "item"));
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Distribution submit(UserAccount submitter) {
        requirePermission(submitter, submitter != null && submitter.isActive() && submitter.canEnterRecords(),
                "User does not have permission to submit distributions.");
        validate();
        requireWorkspaceAccess(submitter);
        requireDraft();
        status = TransactionStatus.PENDING;
        return this;
    }

    public void approve(UserAccount approver) {
        requirePermission(approver, approver != null && approver.isActive() && approver.canApproveDistributions(),
                "User does not have permission to approve distributions.");
        validate();
        requireWorkspaceAccess(approver);
        requirePending();

        List<String> resolvedBatchIds = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            TransactionItem item = items.get(index);
            requireMatchingWorkspace(item.getInventory());
            resolvedBatchIds.add(resolveBatchId(item.getInventory(), item.getBatchReference()));
        }
        for (int index = 0; index < items.size(); index++) {
            TransactionItem item = items.get(index);
            String batchId = resolvedBatchIds.get(index);
            if (isFirstBatchOccurrence(item.getInventory(), batchId, index, resolvedBatchIds)) {
                item.getInventory().validateBatchRemoval(
                        batchId, totalQuantityFor(item.getInventory(), batchId, resolvedBatchIds));
            }
        }
        for (int index = 0; index < items.size(); index++) {
            TransactionItem item = items.get(index);
            item.getInventory().removeBatchStock(resolvedBatchIds.get(index), item.getQuantity());
        }

        releasedBy = approver;
        status = TransactionStatus.APPROVED;
    }

    public void reject(UserAccount approver) {
        requirePermission(approver, approver != null && approver.isActive() && approver.canApproveDistributions(),
                "User does not have permission to reject distributions.");
        requireWorkspaceAccess(approver);
        requirePending();
        status = TransactionStatus.REJECTED;
    }

    private void validate() {
        if (disaster == null) {
            throw new IllegalArgumentException("Distribution must be linked to a disaster.");
        }
        if (beneficiary == null && evacuationCenter == null
                && (releaseLocation == null || releaseLocation.trim().isEmpty())) {
            throw new IllegalArgumentException("Distribution must identify a beneficiary or release location.");
        }
        if (beneficiary != null && beneficiary.getDisaster() != disaster) {
            throw new IllegalArgumentException("Beneficiary must belong to the selected disaster.");
        }
        if (beneficiary != null && beneficiary.getHouseholdHead() == null) {
            throw new IllegalArgumentException("Household must have a designated head before receiving aid.");
        }
        if (evacuationCenter != null && !evacuationCenter.sharesBarangayWith(disaster)) {
            throw new IllegalArgumentException("Evacuation center must belong to the disaster's barangay.");
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Distribution must contain at least one item.");
        }
        if (receiptReference == null || receiptReference.trim().isEmpty()) {
            throw new IllegalArgumentException("Distribution must include a receipt reference.");
        }
        for (TransactionItem item : items) {
            requireMatchingWorkspace(item.getInventory());
        }
    }

    private int totalQuantityFor(Inventory inventory, String batchId, List<String> batchIds) {
        int total = 0;
        for (int index = 0; index < items.size(); index++) {
            TransactionItem item = items.get(index);
            if (item.getInventory() == inventory && batchIds.get(index).equals(batchId)) {
                total = Math.addExact(total, item.getQuantity());
            }
        }
        return total;
    }

    private boolean isFirstBatchOccurrence(Inventory inventory, String batchId,
            int candidateIndex, List<String> batchIds) {
        for (int index = 0; index < candidateIndex; index++) {
            TransactionItem item = items.get(index);
            if (item.getInventory() == inventory && batchIds.get(index).equals(batchId)) {
                return false;
            }
        }
        return true;
    }

    private String resolveBatchId(Inventory inventory, String requestedBatchId) {
        if (requestedBatchId != null && !requestedBatchId.trim().isEmpty()) {
            return requestedBatchId.trim();
        }
        String availableBatchId = null;
        for (InventoryBatch batch : inventory.getBatches()) {
            if (batch.getRemainingQuantity() > 0) {
                if (availableBatchId != null) {
                    throw new IllegalArgumentException(
                            "Distribution must specify a batch when stock exists in multiple batches.");
                }
                availableBatchId = batch.getBatchId();
            }
        }
        if (availableBatchId == null) {
            throw new IllegalArgumentException("Distribution inventory has no available stock batch.");
        }
        return availableBatchId;
    }

    private Barangay getWorkspace() {
        if (evacuationCenter != null) {
            return evacuationCenter.getBarangay();
        }
        return disaster == null ? null : disaster.getBarangay();
    }

    private void requireWorkspaceAccess(UserAccount account) {
        if (!account.canAccess(this)) {
            throw new SecurityException("User cannot access this distribution's barangay workspace.");
        }
    }

    private void requireMatchingWorkspace(Inventory inventory) {
        if (!sharesBarangayWith(inventory)) {
            throw new IllegalArgumentException("Distribution inventory must belong to the selected barangay.");
        }
    }

    private void requireDraft() {
        if (status != TransactionStatus.DRAFT) {
            throw new IllegalStateException("Only draft distributions can be changed.");
        }
    }

    private void requirePending() {
        if (status != TransactionStatus.PENDING) {
            throw new IllegalStateException("Only pending distributions can be approved or rejected.");
        }
    }

    private void requirePermission(UserAccount user, boolean permitted, String message) {
        if (user == null || !permitted) {
            throw new SecurityException(message);
        }
    }
}
