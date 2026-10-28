package SERVER;

import LIB.AuditRepository;
import LIB.Barangay;
import LIB.Beneficiaries;
import LIB.Database;
import LIB.Disaster;
import LIB.DisasterRepository;
import LIB.Distribution;
import LIB.DistributionController;
import LIB.DistributionRepository;
import LIB.EvacuationCenter;
import LIB.HouseholdRepository;
import LIB.Inventory;
import LIB.InventoryBatch;
import LIB.InventoryRepository;
import LIB.ReliefPack;
import LIB.ReliefPackController;
import LIB.ReliefPackRepository;
import LIB.TransactionItem;
import LIB.UserAccount;
import com.sun.net.httpserver.HttpExchange;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Distribution screen. A release names the disaster, the round, the household (its head receives
 * the aid) and, for every item line, the exact stock batch. Only approval deducts stock.
 * Releases can be made one household at a time or as relief packs for many households at once.
 * A household gets at most one release per round; duplicates and releases with too little
 * stock are blocked and written to the audit log.
 */
final class DistributionApi {
    private final DistributionRepository repository = new DistributionRepository();
    private final ReliefPackRepository packs = new ReliefPackRepository();
    private final DistributionController controller = new DistributionController();
    private final ReliefPackController packController = new ReliefPackController();
    private final AuditRepository audits = new AuditRepository();

    /** A refused request that should still appear in the audit log (it changes no stock). */
    private static final class Blocked extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final String barangayId;
        final String action;
        final String reference;
        final String detail;

        Blocked(String message, String barangayId, String action, String reference, String detail) {
            super(message);
            this.barangayId = barangayId;
            this.action = action;
            this.reference = reference;
            this.detail = detail;
        }
    }

    /** One item line to release: which item, from which batch, how many. */
    private static final class Line {
        final Inventory item;
        final InventoryBatch batch;
        final int quantity;

        Line(Inventory item, InventoryBatch batch, int quantity) {
            this.item = item;
            this.batch = batch;
            this.quantity = quantity;
        }
    }

    // ------------------------------------------------------------------ lists
    void list(HttpExchange exchange, UserAccount user) throws Exception {
        LocalDate today = Database.today();
        List<Object> packList = new ArrayList<>();
        for (ReliefPack pack : packs.findByBarangay(Api.scope(user))) {
            packList.add(packJson(pack, today));
        }
        Http.sendJson(exchange, 200, Json.obj(
                "distributions", repository.listSummaries(Api.scope(user)),
                "packs", packList));
    }

    private static Map<String, Object> packJson(ReliefPack pack, LocalDate today) {
        List<Object> lines = new ArrayList<>();
        for (ReliefPack.Line line : pack.getLines()) {
            lines.add(Json.obj("itemId", line.getInventory().getItemId(), "name", line.getInventory().getItemName(),
                    "unit", line.getInventory().getUnit(), "quantity", line.getQuantityPerFamily(),
                    "usable", ReliefPack.usableQuantity(line.getInventory(), today)));
        }
        return Json.obj("id", pack.getPackId(), "name", pack.getName(),
                "barangayId", pack.getBarangay().getBarangayId(), "lines", lines,
                "familiesCovered", pack.familiesCovered(today));
    }

    // ------------------------------------------------------------- one household
    void record(HttpExchange exchange, UserAccount user) throws Exception {
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String disasterId = Api.required(form, "disasterId", "Disaster");
        String householdId = Api.required(form, "householdId", "Household");
        String centerId = Api.text(form, "centerId");
        String date = Api.date(Api.text(form, "date"), "Release date", false);
        String releaseLocation = Api.text(form, "releaseLocation");
        String receipt = Api.text(form, "receiptReference");
        int round = round(form);
        boolean postNow = "1".equals(Api.text(form, "post"));

        List<String> itemIds = Api.all(form, "itemId");
        List<String> batchIds = Api.all(form, "batchId");
        List<String> quantities = Api.all(form, "quantity");
        if (itemIds.isEmpty()) {
            throw new ApiError(400, "Add at least one item line.");
        }
        if (batchIds.size() != itemIds.size() || quantities.size() != itemIds.size()) {
            throw new ApiError(400, "Item lines are incomplete. Please check every line.");
        }
        LocalDate today = Database.today();

        Map<String, Object> result = guarded(user, () -> Api.inTransaction((Connection connection) -> {
            Disaster disaster = activeDisaster(connection, disasterId, user);
            String barangayId = disaster.getBarangay().getBarangayId();
            EvacuationCenter center = center(connection, centerId);

            Map<String, Inventory> loaded = new HashMap<>();
            List<Line> lines = new ArrayList<>();
            Map<String, Integer> perBatch = new HashMap<>();
            for (int i = 0; i < itemIds.size(); i++) {
                Inventory item = item(connection, loaded, itemIds.get(i), barangayId, i);
                int quantity = Api.wholeNumber(quantities.get(i), "Quantity on line " + (i + 1));
                InventoryBatch batch = pickBatch(item, batchIds.get(i).trim(), today);
                int total = perBatch.merge(batch.getBatchId(), quantity, Integer::sum);
                requireStock(item, batch, total, barangayId);
                lines.add(new Line(item, batch, quantity));
            }
            String id = createRelease(connection, disaster, householdId, center, date, releaseLocation, receipt,
                    round, lines, user, postNow, today);
            return Json.obj("ok", true, "id", id, "posted", postNow && user.canApproveDistributions());
        }));
        Http.sendJson(exchange, 201, result);
    }

    // --------------------------------------------------------- many households
    /**
     * Releases one relief pack to each chosen household in one step. For every item of the pack
     * the person picks one batch, which must hold enough for all the chosen households.
     */
    void bulk(HttpExchange exchange, UserAccount user) throws Exception {
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String disasterId = Api.required(form, "disasterId", "Disaster");
        String packId = Api.required(form, "packId", "Relief pack");
        String centerId = Api.text(form, "centerId");
        String date = Api.date(Api.text(form, "date"), "Release date", false);
        String releaseLocation = Api.text(form, "releaseLocation");
        int round = round(form);
        boolean postNow = "1".equals(Api.text(form, "post"));
        List<String> householdIds = Api.all(form, "householdId");
        List<String> batchItemIds = Api.all(form, "batchItemId");
        List<String> batchIds = Api.all(form, "batchId");
        if (householdIds.isEmpty()) {
            throw new ApiError(400, "Tick at least one household.");
        }
        if (batchItemIds.size() != batchIds.size()) {
            throw new ApiError(400, "Batch choices are incomplete.");
        }
        LocalDate today = Database.today();

        Map<String, Object> result = guarded(user, () -> Api.inTransaction((Connection connection) -> {
            Disaster disaster = activeDisaster(connection, disasterId, user);
            String barangayId = disaster.getBarangay().getBarangayId();
            EvacuationCenter center = center(connection, centerId);
            // All releases share these Inventory objects, so each approval sees the stock left by the one before.
            Map<String, Inventory> shared = new HashMap<>();
            ReliefPack pack = ReliefPackRepository.load(connection, packId, shared);
            if (pack == null || !pack.getBarangay().getBarangayId().equals(barangayId)) {
                throw new ApiError(400, "Choose a relief pack of this barangay.");
            }
            int families = householdIds.size();
            List<Line> lines = new ArrayList<>();
            for (ReliefPack.Line packLine : pack.getLines()) {
                Inventory item = packLine.getInventory();
                int index = batchItemIds.indexOf(item.getItemId());
                InventoryBatch batch = pickBatch(item, index < 0 ? "" : batchIds.get(index).trim(), today);
                requireStock(item, batch, packLine.getQuantityPerFamily() * families, barangayId);
                lines.add(new Line(item, batch, packLine.getQuantityPerFamily()));
            }
            List<String> created = new ArrayList<>();
            for (String householdId : householdIds) {
                created.add(createRelease(connection, disaster, householdId, center, date, releaseLocation, "",
                        round, lines, user, postNow, today));
            }
            audits.record(connection, user, "Distribution", barangayId, "Bulk release", pack.getName(), null,
                    families + " households × " + pack.getName() + " · round " + round,
                    created.get(0) + (created.size() > 1 ? " to " + created.get(created.size() - 1) : ""), null, null);
            return Json.obj("ok", true, "ids", created, "posted", postNow && user.canApproveDistributions());
        }));
        Http.sendJson(exchange, 201, result);
    }

    // --------------------------------------------------------------- shared steps
    private interface Step<T> {
        T run() throws Exception;
    }

    /** Runs the work; if it was blocked, records the attempt in the audit log and tells the person why. */
    private <T> T guarded(UserAccount user, Step<T> step) throws Exception {
        try {
            return step.run();
        } catch (Blocked blocked) {
            audits.recordAlone(user, "Distribution", blocked.barangayId, blocked.action, blocked.reference,
                    null, "No stock movement", blocked.detail);
            throw new ApiError(400, blocked.getMessage());
        }
    }

    private static int round(Map<String, List<String>> form) {
        String text = Api.text(form, "round");
        int round = Api.wholeNumber(text.isEmpty() ? "1" : text, "Round");
        if (round < 1) {
            throw new ApiError(400, "Round must be 1 or higher.");
        }
        return round;
    }

    private static Disaster activeDisaster(Connection connection, String disasterId, UserAccount user) throws Exception {
        Disaster disaster = DisasterRepository.findById(connection, disasterId);
        if (disaster == null) {
            throw new ApiError(404, "Disaster not found.");
        }
        Api.requireAccess(user, disaster.getBarangay().getBarangayId());
        if (!disaster.isActive()) {
            throw new ApiError(400, disaster.getName() + " is closed. Reopen it before releasing goods.");
        }
        return disaster;
    }

    private static EvacuationCenter center(Connection connection, String centerId) throws Exception {
        EvacuationCenter center = DisasterRepository.findCenterById(connection, centerId);
        if (!centerId.isEmpty() && center == null) {
            throw new ApiError(400, "Evacuation center not found.");
        }
        return center;
    }

    private static Inventory item(Connection connection, Map<String, Inventory> loaded, String itemId,
            String barangayId, int lineIndex) throws Exception {
        if (!loaded.containsKey(itemId)) {
            loaded.put(itemId, InventoryRepository.findById(connection, itemId));
        }
        Inventory item = loaded.get(itemId);
        if (item == null) {
            throw new ApiError(400, "Choose an item for line " + (lineIndex + 1) + ".");
        }
        if (!item.getBarangay().getBarangayId().equals(barangayId)) {
            throw new ApiError(400, item.getItemName() + " belongs to another barangay's inventory.");
        }
        return item;
    }

    private static void requireStock(Inventory item, InventoryBatch batch, int needed, String barangayId) {
        if (needed > batch.getRemainingQuantity()) {
            throw new Blocked("Not enough " + item.getItemName() + " in batch " + batch.getBatchId()
                    + ": needed " + needed + ", available " + batch.getRemainingQuantity() + " " + item.getUnit() + ".",
                    barangayId, "Insufficient stock blocked", item.getItemId(),
                    "Requested " + needed + " " + item.getUnit() + " · short by " + (needed - batch.getRemainingQuantity()));
        }
    }

    /**
     * The batch to release from. Expired and empty batches are never used. If the item has only
     * one usable batch it is chosen automatically; otherwise the person must pick.
     */
    private static InventoryBatch pickBatch(Inventory item, String batchId, LocalDate today) {
        List<InventoryBatch> usable = new ArrayList<>();
        for (InventoryBatch batch : item.getBatches()) {
            if (batch.getRemainingQuantity() > 0 && !batch.isExpiredOn(today)) {
                usable.add(batch);
            }
        }
        if (batchId.isEmpty()) {
            if (usable.size() == 1) {
                return usable.get(0);
            }
            if (usable.isEmpty()) {
                throw new ApiError(400, item.getItemName() + " has no usable stock (empty or expired).");
            }
            throw new ApiError(400, item.getItemName() + " has stock in " + usable.size()
                    + " batches. Select which batch to release from.");
        }
        for (InventoryBatch batch : item.getBatches()) {
            if (batch.getBatchId().equals(batchId)) {
                if (batch.isExpiredOn(today)) {
                    throw new ApiError(400, "Batch " + batchId + " of " + item.getItemName()
                            + " has expired and cannot be released.");
                }
                return batch;
            }
        }
        throw new ApiError(400, "Batch " + batchId + " does not belong to " + item.getItemName() + ".");
    }

    /**
     * Creates one household's release through the controller and model, saves it as PENDING and,
     * when asked by someone allowed to, approves it straight away (which deducts stock).
     */
    private String createRelease(Connection connection, Disaster disaster, String householdId,
            EvacuationCenter center, String date, String releaseLocation, String receipt, int round,
            List<Line> lines, UserAccount user, boolean postNow, LocalDate today) throws Exception {
        String barangayId = disaster.getBarangay().getBarangayId();
        // The household is loaded with this same Disaster object; the model checks they match.
        Beneficiaries household = HouseholdRepository.load(connection, householdId, disaster);
        if (household == null) {
            throw new ApiError(400, "Household " + householdId + " is not registered.");
        }
        String existing = DistributionRepository.findExistingRelease(connection, householdId,
                disaster.getDisasterId(), round);
        if (existing != null) {
            throw new Blocked(household.getName() + " (" + householdId + ") already has release " + existing
                    + " in round " + round + " of " + disaster.getName() + ".", barangayId,
                    "Duplicate release blocked", householdId,
                    household.getName() + " already assisted in round " + round + " · " + existing);
        }
        String distributionId = Database.nextId(connection, "distributions", "distribution_id", "DST");
        String receiptReference = receipt.isEmpty() ? "SLIP-" + distributionId : receipt;
        Distribution distribution = center == null
                ? controller.createDistribution(distributionId, date, disaster, household,
                        releaseLocation, receiptReference, user)
                : controller.createDistribution(distributionId, date, disaster, household, center,
                        releaseLocation, receiptReference, user);
        distribution.setRoundNumber(round);
        List<String> batchIds = new ArrayList<>();
        for (Line line : lines) {
            controller.addDistributionItem(distribution,
                    new TransactionItem(line.item, line.quantity, line.batch.getBatchId(), null), user);
            batchIds.add(line.batch.getBatchId());
        }
        controller.submitDistribution(distribution, user);
        DistributionRepository.insert(connection, distribution, batchIds, user);
        audits.record(connection, user, "Distribution", barangayId, "Distribution submitted", distributionId, null,
                Api.lineSummary(distribution.getItems()),
                household.getName() + " · " + disaster.getName() + " · round " + round, null, distribution);
        if (postNow && user.canApproveDistributions()) {
            release(connection, distribution, user, today, null);
        }
        return distributionId;
    }

    // --------------------------------------------------------------- decisions
    /** Approve (deduct stock) or reject a pending distribution. */
    void decide(HttpExchange exchange, UserAccount user) throws Exception {
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String distributionId = Api.required(form, "id", "Distribution");
        boolean approve = "approve".equals(Api.text(form, "decision"));
        String reason = Api.text(form, "reason");
        if (!approve && reason.isEmpty()) {
            throw new ApiError(400, "Give a reason for rejecting this release.");
        }
        LocalDate today = Database.today();

        Api.inTransaction((Connection connection) -> {
            Distribution distribution = DistributionRepository.load(connection, distributionId);
            if (distribution == null) {
                throw new ApiError(404, "Distribution not found.");
            }
            Api.requireAccess(user, distribution.getBarangay().getBarangayId());
            if (approve) {
                release(connection, distribution, user, today, reason.isEmpty() ? null : reason);
            } else {
                controller.rejectDistribution(distribution, user);
                DistributionRepository.saveDecision(connection, distribution);
                audits.record(connection, user, "Distribution", distribution.getBarangay().getBarangayId(),
                        "Distribution rejected", distributionId, "Pending", "Rejected · no stock movement",
                        reason, null, distribution);
            }
            return null;
        });
        Http.sendJson(exchange, 200, Json.obj("ok", true));
    }

    /** Approves through the model (which deducts each batch), saves the batches and writes the audit row. */
    private void release(Connection connection, Distribution distribution, UserAccount user, LocalDate today,
            String reason) throws Exception {
        for (TransactionItem line : distribution.getItems()) {
            for (InventoryBatch batch : line.getInventory().getBatches()) {
                if (batch.getBatchId().equals(line.getBatchReference()) && batch.isExpiredOn(today)) {
                    throw new ApiError(400, "Batch " + batch.getBatchId() + " of "
                            + line.getInventory().getItemName() + " has expired since this release was entered.");
                }
            }
        }
        Map<Inventory, Integer> before = Api.stockBefore(distribution.getItems());
        controller.approveDistribution(distribution, user);
        DistributionRepository.saveDecision(connection, distribution);
        String[] change = Api.stockChange(before);
        audits.record(connection, user, "Distribution", distribution.getBarangay().getBarangayId(),
                "Distribution confirmed", distribution.getDistributionId(), change[0], change[1],
                reason == null ? distribution.getBeneficiary().getName() + " · " + distribution.getDisaster().getName()
                        + " · round " + distribution.getRoundNumber() : reason, null, distribution);
    }

    // ------------------------------------------------------------ relief packs
    void createPack(HttpExchange exchange, UserAccount user) throws Exception {
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String name = Api.required(form, "name", "Pack name");
        Barangay barangay = Api.targetBarangay(user, Api.text(form, "barangayId"));
        List<String> itemIds = Api.all(form, "itemId");
        List<String> quantities = Api.all(form, "quantity");
        if (itemIds.isEmpty() || itemIds.size() != quantities.size()) {
            throw new ApiError(400, "Add at least one item with a quantity per family.");
        }
        String id = Api.inTransaction((Connection connection) -> {
            String packId = Database.nextId(connection, "relief_packs", "pack_id", "PCK");
            ReliefPack pack = packController.createPack(packId, name, barangay, user);
            Map<String, Inventory> loaded = new LinkedHashMap<>();
            for (int i = 0; i < itemIds.size(); i++) {
                Inventory item = item(connection, loaded, itemIds.get(i), barangay.getBarangayId(), i);
                packController.addItem(pack, item, Api.wholeNumber(quantities.get(i), "Quantity on line " + (i + 1)), user);
            }
            ReliefPackRepository.insert(connection, pack);
            StringBuilder contents = new StringBuilder();
            for (ReliefPack.Line line : pack.getLines()) {
                contents.append(contents.length() == 0 ? "" : ", ").append(line.getQuantityPerFamily()).append(' ')
                        .append(line.getInventory().getUnit()).append(' ').append(line.getInventory().getItemName());
            }
            audits.record(connection, user, "Distribution", barangay.getBarangayId(), "Relief pack defined", packId,
                    null, name + ": " + contents, null, null, null);
            return packId;
        });
        Http.sendJson(exchange, 201, Json.obj("ok", true, "id", id));
    }

    void deletePack(HttpExchange exchange, UserAccount user) throws Exception {
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String packId = Api.required(form, "id", "Relief pack");
        Api.inTransaction((Connection connection) -> {
            ReliefPack pack = ReliefPackRepository.load(connection, packId, null);
            if (pack == null) {
                throw new ApiError(404, "Relief pack not found.");
            }
            Api.requireAccess(user, pack.getBarangay().getBarangayId());
            packController.requireCanDelete(pack, user);
            ReliefPackRepository.delete(connection, packId);
            audits.record(connection, user, "Distribution", pack.getBarangay().getBarangayId(), "Relief pack removed",
                    packId, pack.getName(), "Removed", null, null, null);
            return null;
        });
        Http.sendJson(exchange, 200, Json.obj("ok", true));
    }
}
