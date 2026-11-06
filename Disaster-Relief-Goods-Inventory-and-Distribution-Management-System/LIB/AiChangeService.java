package LIB;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Converts approved AI tool calls into reviewable proposals and applies them through app workflows. */
public final class AiChangeService {
    private static final String[] ACTIONS = {
        "inventory_create", "inventory_update", "inventory_adjust", "inventory_archive", "inventory_restore",
        "disaster_create", "disaster_close", "household_register", "household_update",
        "donation_record", "donation_update", "donation_approve", "donation_reject",
        "distribution_record", "distribution_approve", "distribution_reject"
    };

    private final Barangay barangay;
    private final UserAccount user;

    public AiChangeService(Barangay barangay, UserAccount user) {
        this.barangay = barangay;
        this.user = user;
    }

    public static AiChangeRepository.Draft prepare(String request, Map<String, Object> arguments,
            Barangay barangay, UserAccount user) throws SQLException {
        requireAccess(barangay, user);
        String action = text(arguments, "action").trim();
        if (!List.of(ACTIONS).contains(action)) {
            throw new IllegalArgumentException("The agent requested an unsupported change action.");
        }
        for (String key : arguments.keySet()) {
            if (!List.of("action", "summary", "data").contains(key)) {
                throw new IllegalArgumentException("The proposed operation contains an unsupported field.");
            }
        }
        String summary = text(arguments, "summary").trim();
        if (summary.isEmpty() || summary.length() > 255) {
            throw new IllegalArgumentException("A change summary of 1–255 characters is required.");
        }
        Map<String, Object> data = Json.asObject(arguments.get("data"));
        if (data.isEmpty()) {
            throw new IllegalArgumentException("The proposed change has no data.");
        }
        validateShape(action, data);
        Preview preview = preview(action, data, barangay, user);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("summary", summary);
        payload.put("data", data);
        return new AiChangeRepository.Draft(action, summary, request == null ? "" : request,
                Json.stringify(payload), preview.before, preview.after);
    }

    private String apply(AiChangeRepository.Proposal proposal) throws SQLException {
        requireAccess(barangay, user);
        Map<String, Object> root = Json.asObject(Json.parse(proposal.proposal));
        Map<String, Object> data = Json.asObject(root.get("data"));
        switch (proposal.action) {
            case "inventory_create" -> {
                Inventory item = new InventoryController().addItem(text(data, "name"), text(data, "category"),
                        integer(data, "opening_quantity"), text(data, "unit"), integer(data, "threshold"),
                        text(data, "storage"), nullableText(data, "expiry_date"), text(data, "note"), barangay, user);
                return item.getItemId();
            }
            case "inventory_update" -> {
                InventoryController controller = new InventoryController();
                Inventory item = required(controller.find(text(data, "item_id"), user), "Inventory item");
                InventoryRepository.EditResult result = controller.updateItem(item, text(data, "name"),
                        text(data, "category"), text(data, "unit"), integer(data, "threshold"),
                        text(data, "storage"), text(data, "reason"), user);
                return result.reference;
            }
            case "inventory_adjust" -> {
                InventoryController controller = new InventoryController();
                Inventory item = required(controller.find(text(data, "item_id"), user), "Inventory item");
                StockAdjustment adjustment = controller.adjustStock(item, text(data, "batch_id"),
                        StockAdjustment.Type.valueOf(text(data, "type")),
                        StockAdjustment.Reason.valueOf(text(data, "reason_category")),
                        integer(data, "quantity"), text(data, "evidence"), date(data, "date"), user);
                return adjustment.getAdjustmentId();
            }
            case "inventory_archive" -> {
                InventoryController controller = new InventoryController();
                Inventory item = required(controller.find(text(data, "item_id"), user), "Inventory item");
                controller.archiveItem(item, text(data, "reason"), user);
                return item.getItemId();
            }
            case "inventory_restore" -> {
                InventoryController controller = new InventoryController();
                Inventory item = required(controller.find(text(data, "item_id"), user), "Inventory item");
                controller.restoreItem(item, user);
                return item.getItemId();
            }
            case "disaster_create" -> {
                Disaster created = new DisasterController().addDisaster(text(data, "name"), text(data, "type"),
                        date(data, "date"), text(data, "severity"), text(data, "areas"), text(data, "location"),
                        text(data, "description"), text(data, "assessment_contact"), text(data, "evacuation_site"),
                        barangay, user);
                return created.getDisasterId();
            }
            case "disaster_close" -> {
                DisasterController controller = new DisasterController();
                Disaster disaster = required(controller.find(text(data, "disaster_id"), user), "Disaster");
                controller.closeDisaster(disaster, text(data, "reason"), user);
                return disaster.getDisasterId();
            }
            case "household_register" -> {
                Disaster disaster = required(new DisasterController().find(text(data, "disaster_id"), user), "Disaster");
                List<FamilyMember> members = members(data);
                Map<String, Integer> needs = needs(data);
                Beneficiaries household = new BeneficiaryController().registerHousehold(disaster,
                        text(data, "head_name"), text(data, "contact"), text(data, "address"),
                        bool(data, "homeless"), text(data, "purok"), members.size(), members,
                        text(data, "damage"), text(data, "evacuation_status"),
                        text(data, "evacuation_location"), text(data, "note"), needs, user);
                return household.getBeneficiaryId();
            }
            case "household_update" -> {
                Beneficiaries household = findHousehold(text(data, "household_id"));
                new BeneficiaryController().updateHousehold(household, text(data, "contact"),
                        text(data, "damage"), text(data, "evacuation_status"), text(data, "reason"), user);
                return household.getBeneficiaryId();
            }
            case "donation_record" -> {
                Donor donor = required(new DonorRepository().findById(text(data, "donor_id")), "Donor");
                List<Inventory> items = new InventoryController().list(barangay, false, user);
                List<DonationRepository.Line> lines = donationLines(data);
                DonationRepository.Mode mode = DonationRepository.Mode.valueOf(text(data, "mode"));
                Donations donation = new DonationController().record(null, donor, barangay, date(data, "date"),
                        text(data, "location"), text(data, "delivery_reference"),
                        text(data, "donor_representative"), text(data, "notes"), lines, items, mode, user);
                return donation.getDonationId();
            }
            case "donation_update" -> {
                Donor donor = required(new DonorRepository().findById(text(data, "donor_id")), "Donor");
                List<Inventory> items = new InventoryController().list(barangay, false, user);
                Donations donation = new DonationController().record(text(data, "donation_id"), donor, barangay,
                        date(data, "date"), text(data, "location"), text(data, "delivery_reference"),
                        text(data, "donor_representative"), text(data, "notes"), donationLines(data), items,
                        DonationRepository.Mode.valueOf(text(data, "mode")), user);
                return donation.getDonationId();
            }
            case "donation_approve" -> {
                return new DonationController().approve(text(data, "donation_id"), user).getDonationId();
            }
            case "donation_reject" -> {
                new DonationController().reject(text(data, "donation_id"), text(data, "reason"), user);
                return text(data, "donation_id");
            }
            case "distribution_record" -> {
                DistributionRepository.Request request = distributionRequest(data);
                DistributionRepository.Mode mode = DistributionRepository.Mode.valueOf(text(data, "mode"));
                Distribution distribution = new DistributionController().record(request, mode, user);
                return distribution.getDistributionId();
            }
            case "distribution_approve" -> {
                return new DistributionController().approve(text(data, "distribution_id"), user).getDistributionId();
            }
            case "distribution_reject" -> {
                new DistributionController().reject(text(data, "distribution_id"), text(data, "reason"), user);
                return text(data, "distribution_id");
            }
            default -> throw new IllegalArgumentException("Unsupported AI change action.");
        }
    }

    public String review(String proposalId) throws SQLException {
        AiChangeRepository repository = new AiChangeRepository();
        AiChangeRepository.Proposal proposal = required(repository.find(barangay, user, proposalId), "Proposal");
        repository.beginApply(barangay, user, proposalId);
        String reference;
        try {
            Map<String, Object> root = Json.asObject(Json.parse(proposal.proposal));
            if (!proposal.action.equals(text(root, "action"))) {
                throw new IllegalStateException("The saved proposal action does not match its audit record.");
            }
            AiChangeRepository.Draft refreshed = prepare(proposal.request, root, barangay, user);
            if (!proposal.before.equals(refreshed.before)) {
                throw new IllegalStateException("The record changed after this proposal was prepared. Run the agent again to review current data.");
            }
            reference = apply(proposal);
        } catch (SQLException | RuntimeException failure) {
            try {
                repository.finish(barangay, user, proposalId, AiChangeRepository.FAILED,
                        failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(), null);
            } catch (SQLException statusFailure) {
                failure.addSuppressed(statusFailure);
            }
            throw failure;
        }
        repository.finish(barangay, user, proposalId, AiChangeRepository.APPLIED,
                "Applied through the existing validated record workflow.", reference);
        return reference;
    }

    public void reject(String proposalId, String note) throws SQLException {
        new AiChangeRepository().reject(barangay, user, proposalId, note);
    }

    public static boolean highImpact(AiChangeRepository.Proposal proposal) {
        Map<String, Object> root = Json.asObject(Json.parse(proposal.proposal));
        Map<String, Object> data = Json.asObject(root.get("data"));
        return proposal.action.endsWith("_approve") || proposal.action.endsWith("_reject")
                || "inventory_adjust".equals(proposal.action)
                || (("donation_record".equals(proposal.action) || "donation_update".equals(proposal.action))
                        && "POST".equals(text(data, "mode")))
                || ("distribution_record".equals(proposal.action) && "POST".equals(text(data, "mode")))
                || "disaster_close".equals(proposal.action)
                || "inventory_archive".equals(proposal.action);
    }

    public static String actionLabel(String action) {
        return action.replace('_', ' ');
    }

    private Beneficiaries findHousehold(String householdId) throws SQLException {
        String disasterId = Db.read(connection -> Db.one(connection,
                "SELECT disaster_id FROM households WHERE household_id=?",
                row -> row.getString(1), householdId));
        if (disasterId == null) {
            throw new IllegalArgumentException("Household was not found in this workspace.");
        }
        Disaster disaster = required(new DisasterController().find(disasterId, user), "Disaster");
        return required(new BeneficiaryController().find(householdId, disaster, user), "Household");
    }

    private DistributionRepository.Request distributionRequest(Map<String, Object> data) throws SQLException {
        DistributionRepository.Request request = new DistributionRepository.Request();
        request.disaster = required(new DisasterController().find(text(data, "disaster_id"), user), "Disaster");
        request.householdId = nullableText(data, "household_id");
        request.centerId = nullableText(data, "center_id");
        request.date = date(data, "date");
        request.acknowledgment = text(data, "acknowledgment");
        request.notes = text(data, "notes");
        for (Object value : Json.asArray(data.get("lines"))) {
            Map<String, Object> line = Json.asObject(value);
            request.lines.add(new DistributionRepository.Line(text(line, "item_id"), text(line, "batch_id"),
                    integer(line, "quantity")));
        }
        return request;
    }

    private static List<DonationRepository.Line> donationLines(Map<String, Object> data) {
        List<DonationRepository.Line> lines = new ArrayList<>();
        for (Object value : Json.asArray(data.get("lines"))) {
            Map<String, Object> line = Json.asObject(value);
            lines.add(new DonationRepository.Line(text(line, "item_id"), integer(line, "quantity"),
                    nullableDate(line, "expiry_date")));
        }
        return lines;
    }

    private static List<FamilyMember> members(Map<String, Object> data) {
        List<FamilyMember> members = new ArrayList<>();
        for (Object value : Json.asArray(data.get("members"))) {
            Map<String, Object> member = Json.asObject(value);
            Integer months = nullableInteger(member, "age_months");
            members.add(new FamilyMember(UUID.randomUUID().toString(), text(member, "name"),
                    integer(member, "age"), months, text(member, "sex"), bool(member, "senior"),
                    bool(member, "infant"), bool(member, "pregnant"), bool(member, "pwd")));
        }
        return members;
    }

    private static Map<String, Integer> needs(Map<String, Object> data) {
        Map<String, Integer> needs = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : Json.asObject(data.get("needs")).entrySet()) {
            if (!(entry.getValue() instanceof Number number)) {
                throw new IllegalArgumentException("Household need quantities must be whole numbers.");
            }
            int quantity = number.intValue();
            if (quantity <= 0) {
                throw new IllegalArgumentException("Household need quantities must be greater than zero.");
            }
            needs.put(entry.getKey(), quantity);
        }
        return needs;
    }

    private static Preview preview(String action, Map<String, Object> data, Barangay barangay, UserAccount user)
            throws SQLException {
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>(data);
        switch (action) {
            case "inventory_update", "inventory_adjust" -> {
                Inventory item = required(new InventoryController().find(text(data, "item_id"), user), "Inventory item");
                before.putAll(inventoryValues(item));
                if ("inventory_update".equals(action)) {
                    after.putAll(data);
                } else {
                    InventoryBatch batch = item.findBatch(text(data, "batch_id"));
                    if (batch == null) {
                        throw new IllegalArgumentException("The requested inventory batch was not found.");
                    }
                    before.put("batch_stock", batch.getRemainingQuantity());
                    int delta = "DECREASE".equals(text(data, "type")) ? -integer(data, "quantity")
                            : integer(data, "quantity");
                    after.put("batch_stock", batch.getRemainingQuantity() + delta);
                    after.put("item_id", item.getItemId());
                    after.put("item_name", item.getItemName());
                    if (batch.getRemainingQuantity() + delta < 0) {
                        throw new IllegalArgumentException("The adjustment would make this batch negative.");
                    }
                }
            }
            case "inventory_archive", "inventory_restore" -> {
                Inventory item = required(new InventoryController().find(text(data, "item_id"), user), "Inventory item");
                before.putAll(inventoryValues(item));
                after.put("item_id", item.getItemId());
                after.put("name", item.getItemName());
                after.put("archived", "inventory_archive".equals(action));
                after.put("reason", text(data, "reason"));
            }
            case "disaster_close", "donation_update", "donation_approve", "donation_reject", "distribution_approve",
                    "distribution_reject" -> {
                String id = text(data, "disaster_id");
                String field = "status";
                if (action.startsWith("donation_")) {
                    Donations donation = required(new DonationController().find(text(data, "donation_id"), user), "Donation");
                    before.put("status", donation.getStatus().name());
                    before.put("record_id", donation.getDonationId());
                    after.put("status", action.endsWith("approve") ? "APPROVED" : action.endsWith("reject")
                            ? "REJECTED" : text(data, "mode"));
                    if ("donation_update".equals(action)) {
                        if (donation.getStatus() != TransactionStatus.DRAFT) {
                            throw new IllegalStateException("Only a draft donation can be replaced.");
                        }
                        before.put("date", donation.getDonationDate());
                        before.put("location", donation.getReceivingLocation());
                        before.put("donor", donation.getDonor().getName());
                    }
                    id = donation.getDonationId();
                    field = "donation";
                } else if (action.startsWith("distribution_")) {
                    Distribution distribution = required(new DistributionController()
                            .find(text(data, "distribution_id"), user), "Distribution");
                    before.put("status", distribution.getStatus().name());
                    before.put("record_id", distribution.getDistributionId());
                    after.put("status", action.endsWith("approve") ? "APPROVED" : "REJECTED");
                    id = distribution.getDistributionId();
                    field = "distribution";
                } else {
                    Disaster disaster = required(new DisasterController().find(id, user), "Disaster");
                    before.put("status", disaster.getStatus());
                    before.put("name", disaster.getName());
                    after.put("status", Disaster.CLOSED);
                    after.put("reason", text(data, "reason"));
                }
                if (id.isBlank() || field.isBlank()) {
                    throw new IllegalArgumentException("A valid record ID is required.");
                }
            }
            case "household_update" -> {
                Beneficiaries household = new AiChangeService(barangay, user).findHousehold(text(data, "household_id"));
                before.put("record_id", household.getBeneficiaryId());
                before.put("contact", household.getContactNumber());
                before.put("damage", household.getDamageLevel());
                before.put("evacuation_status", household.getEvacuationStatus());
                after.putAll(data);
            }
            case "household_register" -> {
                Disaster disaster = required(new DisasterController().find(text(data, "disaster_id"), user), "Disaster");
                after.put("workspace", barangay.getName());
                after.put("disaster", disaster.getName());
                after.put("member_count", Json.asArray(data.get("members")).size());
            }
            case "disaster_create" -> after.put("status", Disaster.ACTIVE);
            case "inventory_create", "donation_record", "distribution_record" -> {
                after.put("workspace", barangay.getName());
                after.put("effect", "POST".equals(text(data, "mode")) ? "Applies immediately after manager confirmation"
                        : "Creates a draft or pending record");
            }
            default -> throw new IllegalArgumentException("Unsupported AI change action.");
        }
        if ("donation_record".equals(action) || "donation_update".equals(action)) {
            List<Inventory> items = new InventoryController().list(barangay, false, user);
            Donor donor = required(new DonorRepository().findById(text(data, "donor_id")), "Donor");
            List<DonationRepository.Line> lines = donationLines(data);
            new DonationController().validate(donor, date(data, "date"), text(data, "location"), lines, items);
            if ("POST".equals(text(data, "mode"))) {
                List<Map<String, Object>> effects = stockEffects(dataLines(data, false), items, true);
                before.put("stock", stockBefore(effects));
                after.put("stock_effects", effects);
            }
        } else if ("donation_approve".equals(action)) {
            List<Inventory> items = new InventoryController().list(barangay, false, user);
            Donations donation = required(new DonationController().find(text(data, "donation_id"), user), "Donation");
            List<Map<String, Object>> effects = stockEffects(transactionLines(donation.getItems()), items, true);
            before.put("stock", stockBefore(effects));
            after.put("stock_effects", effects);
        } else if ("distribution_record".equals(action)) {
            List<Inventory> items = new InventoryController().list(barangay, false, user);
            AiChangeService service = new AiChangeService(barangay, user);
            DistributionRepository.Request request = service.distributionRequest(data);
            DistributionRepository.Problem issue = new DistributionController().check(request, user);
            if (issue != null) {
                throw new IllegalStateException(issue.message);
            }
            if ("POST".equals(text(data, "mode"))) {
                List<Map<String, Object>> effects = stockEffects(dataLines(data, true), items, false);
                before.put("stock", stockBefore(effects));
                after.put("stock_effects", effects);
            }
        } else if ("distribution_approve".equals(action)) {
            List<Inventory> items = new InventoryController().list(barangay, false, user);
            Distribution distribution = required(new DistributionController()
                    .find(text(data, "distribution_id"), user), "Distribution");
            List<Map<String, Object>> effects = stockEffects(transactionLines(distribution.getItems()), items, false);
            before.put("stock", stockBefore(effects));
            after.put("stock_effects", effects);
        }
        return new Preview(Json.stringify(before), Json.stringify(after));
    }

    private static List<Map<String, Object>> dataLines(Map<String, Object> data, boolean withBatch) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Object value : Json.asArray(data.get("lines"))) {
            Map<String, Object> line = Json.asObject(value);
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("item_id", text(line, "item_id"));
            normalized.put("quantity", integer(line, "quantity"));
            if (withBatch) {
                normalized.put("batch_id", text(line, "batch_id"));
            }
            lines.add(normalized);
        }
        return lines;
    }

    private static List<Map<String, Object>> transactionLines(List<TransactionItem> items) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (TransactionItem item : items) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("item_id", item.getInventory().getItemId());
            line.put("quantity", item.getQuantity());
            if (item.getBatchReference() != null) {
                line.put("batch_id", item.getBatchReference());
            }
            lines.add(line);
        }
        return lines;
    }

    private static List<Map<String, Object>> stockEffects(List<Map<String, Object>> lines,
            List<Inventory> items, boolean increase) {
        Map<String, Integer> totalAfter = new LinkedHashMap<>();
        Map<String, Integer> batchAfter = new LinkedHashMap<>();
        List<Map<String, Object>> effects = new ArrayList<>();
        for (Map<String, Object> line : lines) {
            String itemId = text(line, "item_id");
            Inventory item = null;
            for (Inventory candidate : items) {
                if (candidate.getItemId().equals(itemId)) {
                    item = candidate;
                    break;
                }
            }
            if (item == null) {
                throw new IllegalArgumentException("A proposed stock effect refers to an item outside this workspace.");
            }
            int quantity = integer(line, "quantity");
            int before = totalAfter.getOrDefault(itemId, item.getQuantity());
            int after = before + (increase ? quantity : -quantity);
            if (after < 0) {
                throw new IllegalArgumentException("The proposed release exceeds available inventory stock.");
            }
            totalAfter.put(itemId, after);
            Map<String, Object> effect = new LinkedHashMap<>();
            effect.put("item_id", itemId);
            effect.put("item", item.getItemName());
            effect.put("quantity", quantity);
            effect.put("item_stock_before", before);
            effect.put("item_stock_after", after);
            String batchId = text(line, "batch_id");
            if (!batchId.isEmpty()) {
                InventoryBatch batch = item.findBatch(batchId);
                if (batch == null) {
                    throw new IllegalArgumentException("A proposed release refers to a batch that does not exist.");
                }
                String key = itemId + "|" + batchId;
                int batchBefore = batchAfter.getOrDefault(key, batch.getRemainingQuantity());
                int batchRemaining = batchBefore - quantity;
                if (batchRemaining < 0) {
                    throw new IllegalArgumentException("The proposed release exceeds stock in batch " + batchId + ".");
                }
                batchAfter.put(key, batchRemaining);
                effect.put("batch_id", batchId);
                effect.put("batch_stock_before", batchBefore);
                effect.put("batch_stock_after", batchRemaining);
            }
            effects.add(effect);
        }
        return effects;
    }

    private static List<Map<String, Object>> stockBefore(List<Map<String, Object>> effects) {
        List<Map<String, Object>> current = new ArrayList<>();
        for (Map<String, Object> effect : effects) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("item_id", effect.get("item_id"));
            values.put("item", effect.get("item"));
            values.put("stock", effect.get("item_stock_before"));
            if (effect.containsKey("batch_id")) {
                values.put("batch_id", effect.get("batch_id"));
                values.put("batch_stock", effect.get("batch_stock_before"));
            }
            current.add(values);
        }
        return current;
    }

    private static Map<String, Object> inventoryValues(Inventory item) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("item_id", item.getItemId());
        values.put("name", item.getItemName());
        values.put("category", item.getCategory());
        values.put("unit", item.getUnit());
        values.put("quantity", item.getQuantity());
        values.put("threshold", item.getLowStockThreshold());
        values.put("storage", item.getStorageLocation());
        values.put("archived", item.isArchived());
        return values;
    }

    private static void validateShape(String action, Map<String, Object> data) {
        switch (action) {
            case "inventory_create" -> {
                keys(data, "name", "category", "opening_quantity", "unit", "threshold", "storage", "expiry_date", "note");
                require(data, "name", "category", "opening_quantity", "unit", "threshold", "storage");
                integer(data, "opening_quantity");
                integer(data, "threshold");
                nullableDate(data, "expiry_date");
            }
            case "inventory_update" -> {
                keys(data, "item_id", "name", "category", "unit", "threshold", "storage", "reason");
                require(data, "item_id", "name", "category", "unit", "threshold", "storage", "reason");
                integer(data, "threshold");
            }
            case "inventory_adjust" -> {
                keys(data, "item_id", "batch_id", "type", "reason_category", "quantity", "evidence", "date");
                require(data, "item_id", "batch_id", "type", "reason_category", "quantity", "evidence", "date");
                StockAdjustment.Type.valueOf(text(data, "type"));
                StockAdjustment.Reason.valueOf(text(data, "reason_category"));
                integer(data, "quantity");
                date(data, "date");
                if (integer(data, "quantity") <= 0) {
                    throw new IllegalArgumentException("Adjustment quantity must be greater than zero.");
                }
                if ("INCREASE".equals(text(data, "type"))
                        && !"CORRECTION".equals(text(data, "reason_category"))) {
                    throw new IllegalArgumentException("Only a correction can increase stock.");
                }
            }
            case "inventory_archive" -> {
                keys(data, "item_id", "reason");
                require(data, "item_id", "reason");
            }
            case "inventory_restore" -> {
                keys(data, "item_id");
                require(data, "item_id");
            }
            case "disaster_create" -> {
                keys(data, "name", "type", "date", "severity", "areas", "location", "description",
                        "assessment_contact", "evacuation_site");
                require(data, "name", "type", "date", "severity", "areas", "location");
                date(data, "date");
            }
            case "disaster_close" -> {
                keys(data, "disaster_id", "reason");
                require(data, "disaster_id", "reason");
            }
            case "household_register" -> {
                keys(data, "disaster_id", "head_name", "contact", "address", "homeless", "purok", "members",
                        "damage", "evacuation_status", "evacuation_location", "note", "needs");
                require(data, "disaster_id", "head_name", "contact", "homeless",
                        "members", "damage", "evacuation_status", "needs");
                bool(data, "homeless");
                if (!bool(data, "homeless")) {
                    require(data, "address", "purok");
                }
                for (Object value : Json.asArray(data.get("members"))) {
                    Map<String, Object> member = Json.asObject(value);
                    keys(member, "name", "age", "age_months", "sex", "senior", "infant", "pregnant", "pwd");
                    require(member, "name", "age", "sex", "senior", "infant", "pregnant", "pwd");
                    integer(member, "age");
                    nullableInteger(member, "age_months");
                    bool(member, "senior");
                    bool(member, "infant");
                    bool(member, "pregnant");
                    bool(member, "pwd");
                }
                needs(data);
            }
            case "household_update" -> {
                keys(data, "household_id", "contact", "damage", "evacuation_status", "reason");
                require(data, "household_id", "contact", "damage", "evacuation_status", "reason");
            }
            case "donation_record" -> {
                keys(data, "donor_id", "date", "location", "delivery_reference", "donor_representative", "notes",
                        "lines", "mode");
                require(data, "donor_id", "date", "location", "lines", "mode");
                date(data, "date");
                DonationRepository.Mode.valueOf(text(data, "mode"));
            }
            case "donation_update" -> {
                keys(data, "donation_id", "donor_id", "date", "location", "delivery_reference",
                        "donor_representative", "notes", "lines", "mode");
                require(data, "donation_id", "donor_id", "date", "location", "lines", "mode");
                date(data, "date");
                DonationRepository.Mode.valueOf(text(data, "mode"));
            }
            case "donation_approve" -> {
                keys(data, "donation_id");
                require(data, "donation_id");
            }
            case "donation_reject" -> {
                keys(data, "donation_id", "reason");
                require(data, "donation_id", "reason");
            }
            case "distribution_record" -> {
                keys(data, "disaster_id", "household_id", "center_id", "date", "acknowledgment", "notes",
                        "lines", "mode");
                require(data, "disaster_id", "date", "lines", "mode");
                date(data, "date");
                DistributionRepository.Mode.valueOf(text(data, "mode"));
                if (nullableText(data, "household_id") == null && nullableText(data, "center_id") == null) {
                    throw new IllegalArgumentException("Select a household or evacuation center for a distribution.");
                }
            }
            case "distribution_approve" -> {
                keys(data, "distribution_id");
                require(data, "distribution_id");
            }
            case "distribution_reject" -> {
                keys(data, "distribution_id", "reason");
                require(data, "distribution_id", "reason");
            }
            default -> throw new IllegalArgumentException("Unsupported AI change action.");
        }
        if ("household_register".equals(action) && (Json.asArray(data.get("members")).isEmpty()
                || Json.asObject(data.get("needs")).isEmpty())) {
            throw new IllegalArgumentException("A household proposal needs at least one member and one recorded need.");
        }
        if (("donation_record".equals(action) || "donation_update".equals(action)
                || "distribution_record".equals(action))
                && Json.asArray(data.get("lines")).isEmpty()) {
            throw new IllegalArgumentException("A donation or distribution proposal needs at least one item line.");
        }
        if ("donation_record".equals(action) || "donation_update".equals(action)) {
            for (Object value : Json.asArray(data.get("lines"))) {
                Map<String, Object> line = Json.asObject(value);
                keys(line, "item_id", "quantity", "expiry_date");
                require(line, "item_id", "quantity");
                integer(line, "quantity");
                nullableDate(line, "expiry_date");
            }
        }
        if ("distribution_record".equals(action)) {
            for (Object value : Json.asArray(data.get("lines"))) {
                Map<String, Object> line = Json.asObject(value);
                keys(line, "item_id", "batch_id", "quantity");
                require(line, "item_id", "batch_id", "quantity");
                integer(line, "quantity");
            }
        }
    }

    private static void require(Map<String, Object> data, String... fields) {
        for (String field : fields) {
            if (data.get(field) == null || data.get(field) instanceof String value && value.isBlank()) {
                throw new IllegalArgumentException("The proposed change is missing '" + field + "'.");
            }
        }
    }

    private static void keys(Map<String, Object> data, String... allowed) {
        for (String key : data.keySet()) {
            if (!List.of(allowed).contains(key)) {
                throw new IllegalArgumentException("The proposed change contains an unsupported field '" + key + "'.");
            }
        }
    }

    private static String text(Map<String, Object> data, String key) {
        return data.get(key) == null ? "" : String.valueOf(data.get(key)).trim();
    }

    private static String nullableText(Map<String, Object> data, String key) {
        String value = text(data, key);
        return value.isEmpty() ? null : value;
    }

    private static int integer(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue()) {
            throw new IllegalArgumentException("'" + key + "' must be a whole number.");
        }
        return number.intValue();
    }

    private static Integer nullableInteger(Map<String, Object> data, String key) {
        return data.get(key) == null ? null : integer(data, key);
    }

    private static boolean bool(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (!(value instanceof Boolean result)) {
            throw new IllegalArgumentException("'" + key + "' must be true or false.");
        }
        return result;
    }

    private static LocalDate date(Map<String, Object> data, String key) {
        try {
            return LocalDate.parse(text(data, key));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("'" + key + "' must use YYYY-MM-DD.");
        }
    }

    private static LocalDate nullableDate(Map<String, Object> data, String key) {
        return text(data, key).isEmpty() ? null : date(data, key);
    }

    private static <T> T required(T value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " was not found in this barangay workspace.");
        }
        return value;
    }

    private static void requireAccess(Barangay barangay, UserAccount user) {
        if (barangay == null || user == null || !user.isActive() || !user.canEnterRecords()
                || !user.canAccessBarangay(barangay)) {
            throw new SecurityException("You cannot propose or apply AI changes in this workspace.");
        }
    }

    private static final class Preview {
        final String before;
        final String after;

        Preview(String before, String after) {
            this.before = before;
            this.after = after;
        }
    }
}
