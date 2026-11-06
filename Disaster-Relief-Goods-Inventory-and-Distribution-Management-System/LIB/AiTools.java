package LIB;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Approved, read-only functions the AI agent may call. None of these write inventory,
 * approve donations, or release goods.
 */
public final class AiTools {
    public static final int EXPIRING_DAYS = 30;

    public static final class Call {
        public final String id;
        public final String name;
        public final String arguments;

        public Call(String id, String name, String arguments) {
            this.id = id == null || id.isBlank() ? name + "-" + System.nanoTime() : id;
            this.name = name == null ? "" : name;
            this.arguments = arguments == null || arguments.isBlank() ? "{}" : arguments;
        }
    }

    public static final class Spec {
        public final String name;
        public final String description;
        public final Map<String, Object> parameters;

        Spec(String name, String description, Map<String, Object> parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }
    }

    private final Barangay barangay;
    private final UserAccount user;

    public AiTools(Barangay barangay, UserAccount user) {
        this.barangay = barangay;
        this.user = user;
    }

    public static List<Spec> catalog() {
        List<Spec> tools = new ArrayList<>();
        tools.add(spec("get_workspace_snapshot",
                "Barangay-level counts: stock items, low/expired stock, disasters, pending workflows, household aggregates.",
                Map.of()));
        tools.add(spec("get_stock_alerts",
                "Live low-stock, expired-batch, and expiring-soon notices from current inventory.", Map.of()));
        tools.add(spec("get_inventory",
                "Usable and expired quantities for inventory items. Optional filter: all, low, expired, expiring.",
                props("filter", "string", "all, low, expired, or expiring")));
        tools.add(spec("get_disasters",
                "Disaster events with status, severity, and aggregate household/assistance stats. No personal names.",
                Map.of()));
        tools.add(spec("get_pending_workflow",
                "Pending donations and distributions as counts and de-identified lines (no household names).",
                Map.of()));
        tools.add(spec("get_donors", "Donor IDs, names, and types previously used in this barangay; no contact details.",
                Map.of()));
        tools.add(spec("get_evacuation_centers",
                "Evacuation centre names and listed capacities. Occupancy is not recorded.", Map.of()));
        tools.add(spec("get_memory",
                "Previous agent assessments, decisions, and open follow-up recommendations for this workspace.",
                Map.of()));
        tools.add(spec("get_open_follow_ups", "Unresolved agent recommendations still marked open.", Map.of()));
        tools.add(spec("get_historical_trends",
                "Approved monthly distribution totals by item category and unit for the past 12 months. "
                        + "Use only for observed trends; do not infer future demand.",
                Map.of()));
        tools.add(spec("simulate_scenario",
                "Run the local disaster simulation engine against recorded stock and household-need categories.",
                props(new String[] { "disaster_type", "severity", "days" },
                        new String[] { "string", "string", "number" },
                        new String[] { "Hazard type, e.g. Flood or Typhoon",
                                "LOW, MODERATE, HIGH, or CRITICAL",
                                "Planning horizon in days; omit for the severity default" })));
        Map<String, Object> proposalProperties = new LinkedHashMap<>();
        proposalProperties.put("action", Map.of("type", "string", "enum", List.of(
                "inventory_create", "inventory_update", "inventory_adjust", "inventory_archive", "inventory_restore",
                "disaster_create", "disaster_close", "household_register", "household_update",
                "donation_record", "donation_update", "donation_approve", "donation_reject",
                "distribution_record", "distribution_approve", "distribution_reject"), "description",
                "One of inventory_create, inventory_update, inventory_adjust, inventory_archive, inventory_restore, "
                        + "disaster_create, disaster_close, household_register, household_update, "
                        + "donation_record, donation_update, donation_approve, donation_reject, "
                        + "distribution_record, distribution_approve, distribution_reject"));
        proposalProperties.put("summary", Map.of("type", "string", "description", "Short human-readable change summary"));
        Map<String, Object> dataSchema = new LinkedHashMap<>();
        dataSchema.put("type", "object");
        dataSchema.put("additionalProperties", true);
        dataSchema.put("description", "Action-specific fields listed in the propose_change tool description. "
                + "Use only values explicitly supplied by the user or verified by a read tool; never guess IDs or personal data.");
        proposalProperties.put("data", dataSchema);
        Map<String, Object> proposalSchema = new LinkedHashMap<>();
        proposalSchema.put("type", "object");
        proposalSchema.put("properties", Map.of(
                "action", proposalProperties.get("action"),
                "summary", proposalProperties.get("summary"),
                "data", proposalProperties.get("data")));
        proposalSchema.put("required", List.of("action", "summary", "data"));
        tools.add(new Spec("propose_change",
                "Stage one change for human review; this tool NEVER writes application records. "
                        + "Available data fields: inventory_create {name,category,opening_quantity,unit,threshold,storage,expiry_date?,note?}; "
                        + "inventory_update {item_id,name,category,unit,threshold,storage,reason}; "
                        + "inventory_adjust {item_id,batch_id,type:INCREASE|DECREASE,reason_category:DAMAGE|SPOILAGE|CORRECTION,quantity,evidence,date}; "
                        + "inventory_archive {item_id,reason}; inventory_restore {item_id}; "
                        + "disaster_create {name,type,date,severity,areas,location,description?,assessment_contact?,evacuation_site?}; "
                        + "disaster_close {disaster_id,reason}; household_register {disaster_id,head_name,contact,address,homeless,purok,members:[{name,age,age_months?,sex,senior,infant,pregnant,pwd}],damage,evacuation_status,evacuation_location?,note?,needs:{category:quantity}}; "
                        + "household_update {household_id,contact,damage,evacuation_status,reason}; "
                        + "donation_record {donor_id,date,location,delivery_reference?,donor_representative?,notes?,lines:[{item_id,quantity,expiry_date?}],mode:DRAFT|SUBMIT|POST}; "
                        + "donation_update {donation_id,donor_id,date,location,delivery_reference?,donor_representative?,notes?,lines:[{item_id,quantity,expiry_date?}],mode:DRAFT|SUBMIT|POST}; "
                        + "donation_approve {donation_id}; donation_reject {donation_id,reason}; "
                        + "distribution_record {disaster_id,household_id? or center_id?,date,acknowledgment,notes?,lines:[{item_id,batch_id,quantity}],mode:DRAFT|SUBMIT|POST}; "
                        + "distribution_approve {distribution_id}; distribution_reject {distribution_id,reason}. "
                        + "Use POST or approve only when the user explicitly requested it. The application requires an authorized human to review and confirm every proposal.",
                proposalSchema));
        return tools;
    }

    public static List<Map<String, Object>> openaiTools() {
        return openaiTools(null, "");
    }

    public static List<Map<String, Object>> openaiTools(AiAgent.Mode mode, String question) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Spec spec : catalog()) {
            if ("propose_change".equals(spec.name) && !hasExplicitChangeIntent(question)) {
                continue;
            }
            if ("get_memory".equals(spec.name) && mode != AiAgent.Mode.FOLLOW_UP) {
                continue;
            }
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", spec.name);
            function.put("description", spec.description);
            function.put("parameters", spec.parameters);
            list.add(Map.of("type", "function", "function", function));
        }
        return list;
    }

    static boolean hasExplicitChangeIntent(String question) {
        return question != null && question.matches("(?is).*\\b(?:add|create|register|update|edit|adjust|increase|"
                + "decrease|archive|restore|close|record|approve|reject|submit|release|distribute|modify|save|"
                + "change|set|rename|remove|mark|correct|restock|receive)\\b.*");
    }

    public static boolean isInventoryChangeRequest(String question) {
        return hasExplicitChangeIntent(question) && question.matches("(?is).*\\b(?:inventory|stock|food|foods|"
                + "item|items|supply|supplies|goods|rice|water|medicine|medicines|kit|kits|batch|quantity|"
                + "threshold|expiry|expired|storage)\\b.*");
    }

    public String execute(Call call) throws SQLException {
        authorize();
        Map<String, Object> args = parseArgs(call.arguments);
        return switch (call.name) {
            case "get_workspace_snapshot" -> snapshot();
            case "get_stock_alerts" -> stockAlerts();
            case "get_inventory" -> inventory(text(args, "filter"));
            case "get_disasters" -> disasters();
            case "get_pending_workflow" -> pending();
            case "get_donors" -> donors();
            case "get_evacuation_centers" -> centers();
            case "get_memory" -> new AiMemoryRepository().memoryBrief(barangay.getBarangayId());
            case "get_open_follow_ups" -> openFollowUps();
            case "get_historical_trends" -> historicalTrends();
            case "simulate_scenario" -> simulate(text(args, "disaster_type"), text(args, "severity"),
                    integer(args, "days"));
            default -> "Unknown tool '" + call.name + "'. Use only the approved function list.";
        };
    }

    public String snapshot() throws SQLException {
        return new AiContextService().load(barangay, user).context;
    }

    public String stockAlerts() throws SQLException {
        LocalDate today = Time.today();
        List<Inventory> items = new InventoryRepository().findByBarangay(barangay.getBarangayId());
        List<String> low = new ArrayList<>();
        List<String> expired = new ArrayList<>();
        List<String> expiring = new ArrayList<>();
        for (Inventory item : items) {
            int usable = item.getAvailableQuantity(today);
            if (item.isLowStock(today)) {
                low.add("- " + item.getItemName() + " | usable=" + usable + " " + item.getUnit()
                        + " | threshold=" + item.getLowStockThreshold() + " " + item.getUnit());
            }
            int expiredQty = item.getExpiredQuantity(today);
            if (expiredQty > 0) {
                expired.add("- " + item.getItemName() + " | expired=" + expiredQty + " " + item.getUnit()
                        + " | keep out of release");
            }
            if (item.isExpiringSoon(today, EXPIRING_DAYS)) {
                LocalDate expiry = item.getEarliestUsableExpiry(today);
                expiring.add("- " + item.getItemName() + " | earliest usable expiry=" + Time.date(expiry)
                        + " | usable=" + usable + " " + item.getUnit());
            }
        }
        return "STOCK ALERTS on " + today + ":\nLow-stock items (" + low.size() + "):\n"
                + orNone(low) + "\nExpired stock (" + expired.size() + "):\n" + orNone(expired)
                + "\nExpiring within " + EXPIRING_DAYS + " days (" + expiring.size() + "):\n" + orNone(expiring);
    }

    public String inventory(String filter) throws SQLException {
        String mode = filter == null || filter.isBlank() ? "all" : filter.trim().toLowerCase(Locale.ENGLISH);
        LocalDate today = Time.today();
        List<String> lines = new ArrayList<>();
        for (Inventory item : new InventoryRepository().findByBarangay(barangay.getBarangayId())) {
            int usable = item.getAvailableQuantity(today);
            int expired = item.getExpiredQuantity(today);
            boolean match = switch (mode) {
                case "low" -> item.isLowStock(today);
                case "expired" -> expired > 0;
                case "expiring" -> item.isExpiringSoon(today, EXPIRING_DAYS);
                default -> true;
            };
            if (match) {
                lines.add("- id=" + item.getItemId() + " | " + item.getItemName() + " | category=" + item.getCategory() + " | usable=" + usable
                        + " " + item.getUnit() + " | threshold=" + item.getLowStockThreshold() + " " + item.getUnit()
                        + " | expired=" + expired + " " + item.getUnit() + " | batches="
                        + batches(item));
            }
        }
        return "INVENTORY filter=" + mode + ":\n" + orNone(lines);
    }

    public String disasters() throws SQLException {
        DisasterRepository repository = new DisasterRepository();
        List<String> lines = new ArrayList<>();
        for (Disaster disaster : repository.findByBarangay(barangay.getBarangayId())) {
            DisasterStats stats = repository.stats(disaster.getDisasterId());
            lines.add("- id=" + disaster.getDisasterId() + " | name=" + disaster.getName() + " | type="
                    + disaster.getType() + " | status=" + disaster.getStatus() + " | severity="
                    + blank(disaster.getSeverity(), "not recorded") + " | households=" + stats.registered
                    + " | individuals=" + stats.individuals + " | assisted=" + stats.assisted
                    + " | pending households=" + stats.pending() + " | vulnerable people=" + stats.vulnerablePeople
                    + " | pending need categories=" + stats.pendingNeeds);
        }
        return "DISASTERS (no household names):\n" + orNone(lines);
    }

    public String pending() throws SQLException {
        DonationRepository.Filter donations = new DonationRepository.Filter();
        donations.status = TransactionStatus.PENDING;
        donations.limit = 30;
        DistributionRepository.Filter distributions = new DistributionRepository.Filter();
        distributions.status = TransactionStatus.PENDING;
        distributions.limit = 30;
        List<DonationRepository.Row> donationRows = new DonationRepository().list(barangay.getBarangayId(), donations);
        List<DistributionRepository.Row> distributionRows = new DistributionRepository()
                .list(barangay.getBarangayId(), distributions);
        List<String> donationLines = new ArrayList<>();
        for (DonationRepository.Row row : donationRows) {
            donationLines.add("- donation " + row.donationId + " | date=" + Time.date(row.date) + " | donor type="
                    + (row.donorType == null ? "unknown" : row.donorType) + " | units=" + row.totalUnits
                    + " | goods=" + String.join("; ", row.goods));
        }

        List<String> distributionLines = new ArrayList<>();
        for (DistributionRepository.Row row : distributionRows) {
            distributionLines.add("- distribution " + row.distributionId + " | date=" + Time.date(row.date)
                    + " | disaster=" + row.disasterName + " | goods=" + String.join("; ", row.goods));
        }
        return "PENDING WORKFLOW:\nDonations awaiting approval (" + donationRows.size() + "):\n"
                + orNone(donationLines) + "\nDistributions awaiting approval (" + distributionRows.size() + "):\n"
                + orNone(distributionLines);
    }

    public String donors() throws SQLException {
        List<String> lines = Db.read(connection -> Db.list(connection,
                "SELECT DISTINCT dn.donor_id, dn.name, dn.donor_type FROM donors dn "
                        + "JOIN donations d ON d.donor_id=dn.donor_id WHERE d.barangay_id=? "
                        + "ORDER BY dn.name LIMIT 100",
                row -> "- id=" + row.getString("donor_id") + " | " + row.getString("name")
                        + " | type=" + row.getString("donor_type"),
                barangay.getBarangayId()));
        return "DONORS previously recorded in this workspace (contact details withheld):\n" + orNone(lines);
    }

    private static String batches(Inventory item) {
        List<String> values = new ArrayList<>();
        for (InventoryBatch batch : item.getBatches()) {
            values.add(batch.getBatchId() + ":" + batch.getRemainingQuantity()
                    + (batch.getExpiryDate() == null || batch.getExpiryDate().isBlank()
                            ? "" : " exp=" + batch.getExpiryDate()));
        }
        return values.isEmpty() ? "none" : String.join(", ", values);
    }

    public String centers() throws SQLException {
        List<String> lines = new ArrayList<>();
        int capacity = 0;
        for (EvacuationCenter center : new EvacuationCenterRepository().findByBarangay(barangay)) {
            capacity += center.getCapacity();
            lines.add("- id=" + center.getCenterId() + " | " + center.getName() + " | listed capacity=" + center.getCapacity()
                    + " | occupancy not recorded");
        }
        return "EVACUATION CENTRES: listed capacity total=" + capacity + "\n" + orNone(lines);
    }

    public String openFollowUps() throws SQLException {
        List<AiMemoryRepository.FollowUp> items = new AiMemoryRepository().followUps(barangay.getBarangayId(), true);
        List<String> lines = new ArrayList<>();
        for (AiMemoryRepository.FollowUp item : items) {
            lines.add("- " + item.title + (item.detail.isEmpty() ? "" : " — " + item.detail)
                    + " | last assessed=" + (item.lastAssessedAt.isEmpty() ? "never" : item.lastAssessedAt));
        }
        return "OPEN FOLLOW-UPS (" + items.size() + "):\n" + orNone(lines);
    }

    public String simulate(String disasterType, String severity, Integer days) throws SQLException {
        AiContextService.Snapshot snap = new AiContextService().load(barangay, user);
        List<AiSimulator.NeedStock> needs = new ArrayList<>();
        Db.read(connection -> {
            List<String[]> rows = Db.list(connection,
                    "SELECT hn.category, SUM(hn.requested_quantity) AS requested FROM household_needs hn "
                            + "JOIN households h ON h.household_id=hn.household_id "
                            + "JOIN disasters d ON d.disaster_id=h.disaster_id WHERE d.barangay_id=? "
                            + "AND UPPER(d.status) NOT IN ('CLOSED','RESOLVED') GROUP BY hn.category",
                    row -> new String[] { row.getString("category"), String.valueOf(row.getInt("requested")) },
                    barangay.getBarangayId());
            for (String[] row : rows) {
                String category = row[0];
                int requested = Integer.parseInt(row[1]);
                needs.add(new AiSimulator.NeedStock(category, requested));
            }
            return null;
        });
        int capacity = 0;
        for (EvacuationCenter center : new EvacuationCenterRepository().findByBarangay(barangay)) {
            capacity += center.getCapacity();
        }
        String type = disasterType;
        if (type == null || type.isBlank()) {
            for (Disaster disaster : new DisasterRepository().findByBarangay(barangay.getBarangayId())) {
                if (disaster.isActive()) {
                    type = disaster.getType();
                    if (severity == null || severity.isBlank()) {
                        severity = disaster.getSeverity();
                    }
                    break;
                }
            }
        }
        AiSimulator.HistoricalBaseline baseline = historicalBaseline(type, severity);
        return AiSimulator.run(type, severity, days, snap.activeHouseholds, snap.affectedIndividuals, capacity,
                needs, baseline).report();
    }

    private AiSimulator.HistoricalBaseline historicalBaseline(String type, String severity) throws SQLException {
        if (type == null || type.isBlank()) {
            return new AiSimulator.HistoricalBaseline(0, 0, 0);
        }
        String sql = "SELECT COUNT(*) AS events, COALESCE(AVG(h.households), 0) AS households, "
                + "COALESCE(AVG(p.people), 0) AS people FROM disasters d "
                + "LEFT JOIN (SELECT disaster_id, COUNT(*) AS households FROM households GROUP BY disaster_id) h "
                + "ON h.disaster_id=d.disaster_id "
                + "LEFT JOIN (SELECT h.disaster_id, COUNT(f.member_id) AS people FROM households h "
                + "LEFT JOIN family_members f ON f.household_id=h.household_id GROUP BY h.disaster_id) p "
                + "ON p.disaster_id=d.disaster_id WHERE d.barangay_id=? AND LOWER(d.type)=LOWER(?) "
                + "AND UPPER(COALESCE(d.severity,''))=UPPER(?) "
                + "AND UPPER(d.status) IN ('CLOSED','RESOLVED') "
                + "AND d.disaster_date >= DATE_SUB(CURDATE(), INTERVAL 5 YEAR)";
        return Db.read(connection -> Db.one(connection, sql,
                row -> new AiSimulator.HistoricalBaseline(row.getInt("events"),
                        row.getDouble("households"), row.getDouble("people")),
                barangay.getBarangayId(), type.trim(), AiSimulator.normalize(severity)));
    }

    public String historicalTrends() throws SQLException {
        String sql = "SELECT DATE_FORMAT(d.distribution_date, '%Y-%m') AS period, i.category, i.unit, "
                + "SUM(di.quantity) AS quantity, COUNT(DISTINCT d.distribution_id) AS releases "
                + "FROM distributions d JOIN distribution_items di ON di.distribution_id=d.distribution_id "
                + "JOIN inventory_items i ON i.inventory_item_id=di.inventory_item_id "
                + "WHERE d.barangay_id=? AND d.status='APPROVED' "
                + "AND d.distribution_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH) "
                + "GROUP BY period, i.category, i.unit ORDER BY period, i.category, i.unit LIMIT 120";
        List<String> rows = Db.read(connection -> Db.list(connection, sql, row -> "- period="
                + row.getString("period") + " | category=" + row.getString("category") + " | quantity="
                + row.getLong("quantity") + " " + row.getString("unit") + " | approved releases="
                + row.getInt("releases"), barangay.getBarangayId()));
        return "OBSERVED DISTRIBUTION HISTORY (last 12 months; approved records only; not a forecast):\n"
                + orNone(rows);
    }

    public static List<String> defaultTools(AiAgent.Mode mode) {
        return switch (mode) {
            case MONITOR -> List.of("get_stock_alerts", "get_pending_workflow");
            case PLAN -> List.of("get_workspace_snapshot", "get_disasters", "get_pending_workflow",
                    "get_open_follow_ups", "get_historical_trends");
            case SIMULATE -> List.of("get_workspace_snapshot", "simulate_scenario");
            case REPORT -> List.of("get_workspace_snapshot", "get_stock_alerts", "get_disasters",
                    "get_pending_workflow");
            case FOLLOW_UP -> List.of("get_open_follow_ups", "get_stock_alerts", "get_pending_workflow");
            case ASK -> List.of("get_workspace_snapshot");
            default -> List.of("get_workspace_snapshot", "get_stock_alerts", "get_pending_workflow");
        };
    }

    private void authorize() {
        if (barangay == null || user == null || !user.isActive() || !user.canAccessBarangay(barangay)) {
            throw new SecurityException("You cannot use AI agent tools for this barangay workspace.");
        }
    }

    private static Spec spec(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", parameters);
        return new Spec(name, description, schema);
    }

    private static Map<String, Object> props(String name, String type, String description) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", type);
        field.put("description", description);
        return Map.of(name, field);
    }

    private static Map<String, Object> props(String[] names, String[] types, String[] descriptions) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 0; i < names.length; i++) {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("type", types[i]);
            field.put("description", descriptions[i]);
            properties.put(names[i], field);
        }
        return properties;
    }

    private static Map<String, Object> parseArgs(String arguments) {
        try {
            return Json.asObject(Json.parse(arguments));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("The tool arguments are not valid JSON.", invalid);
        }
    }

    private static String text(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static Integer integer(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return (int) Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String orNone(List<String> lines) {
        return lines.isEmpty() ? "None recorded.\n" : String.join("\n", lines) + "\n";
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
