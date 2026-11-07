package LIB;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the report tables shown on the Reports screen and written to Excel / PDF. */
public class ReportService {
    public static final String[] INVENTORY_TYPES = { "Current stock", "Low stock", "Expired items" };
    public static final String[] BENEFICIARY_TYPES = { "Families & vulnerable groups", "Assistance needed" };
    public static final String[] DONATION_TYPES = { "By period", "By donor", "By item" };
    public static final String[] DISTRIBUTION_TYPES = { "By disaster", "By area", "By item", "By beneficiary" };

    /** The filters of the Reports screen. */
    public static final class Params {
        public Barangay barangay;
        public String type;
        public LocalDate from;
        public LocalDate to;
        public String category;
        public String disasterId;
        public String donorId;
        public String householdId;
    }

    private final InventoryRepository inventory = new InventoryRepository();

    public ReportTable build(String group, Params params) throws SQLException {
        switch (group) {
            case "Inventory":
                return inventoryReport(params);
            case "Disaster & beneficiaries":
                return beneficiaryReport(params);
            case "Donations":
                return donationReport(params);
            default:
                return distributionReport(params);
        }
    }

    // ------------------------------------------------------------------ inventory

    private ReportTable inventoryReport(Params params) throws SQLException {
        LocalDate today = Time.today();
        List<Inventory> items = new ArrayList<>();
        for (Inventory item : inventory.findByBarangay(params.barangay.getBarangayId(), false)) {
            if (params.category == null || params.category.isEmpty() || item.getCategory().equals(params.category)) {
                items.add(item);
            }
        }
        int recorded = 0;
        int expired = 0;
        int low = 0;
        for (Inventory item : items) {
            recorded += item.getQuantity();
            expired += item.getExpiredQuantity(today);
            if (item.isLowStock(today)) {
                low++;
            }
        }
        String type = params.type == null ? INVENTORY_TYPES[0] : params.type;
        ReportTable table;
        if (type.equals("Low stock")) {
            table = new ReportTable("Low stock report", "Item", "Available", "Threshold", "Shortfall", "Storage");
            for (Inventory item : items) {
                if (item.isLowStock(today)) {
                    int available = item.getAvailableQuantity(today);
                    table.row(item.getItemName() + " (" + item.getItemId() + ")", Units.quantity(available, item.getUnit()),
                            Units.quantity(item.getLowStockThreshold(), item.getUnit()),
                            Units.quantity(Math.max(0, item.getLowStockThreshold() - available + 1), item.getUnit()),
                            item.getStorageLocation());
                }
            }
        } else if (type.equals("Expired items")) {
            table = new ReportTable("Expired items report", "Item", "Batch", "Expired on", "Quantity", "Storage");
            for (Inventory item : items) {
                for (InventoryBatch batch : item.getBatches()) {
                    if (batch.isExpired(today) && batch.getRemainingQuantity() > 0) {
                        table.row(item.getItemName() + " (" + item.getItemId() + ")", batch.getBatchId(),
                                Time.date(batch.getExpiryLocalDate()),
                                Units.quantity(batch.getRemainingQuantity(), item.getUnit()), item.getStorageLocation());
                    }
                }
            }
        } else {
            table = new ReportTable("Current stock report", "Item", "Recorded", "Available", "Threshold", "Expiry",
                    "Status");
            for (Inventory item : items) {
                String status = item.hasExpiredStock(today) ? "Low / expired" : item.isLowStock(today) ? "Low stock"
                        : item.isExpiringSoon(today, 30) ? "Expiring soon" : "Available";
                if (item.hasExpiredStock(today) && !item.isLowStock(today)) {
                    status = "Expired stock";
                }
                table.row(item.getItemName() + " (" + item.getItemId() + ")",
                        Units.quantity(item.getQuantity(), item.getUnit()),
                        Units.quantity(item.getAvailableQuantity(today), item.getUnit()),
                        Units.quantity(item.getLowStockThreshold(), item.getUnit()),
                        item.getEarliestExpiry() == null ? "Not applicable" : Time.date(item.getEarliestExpiry()),
                        status);
            }
        }
        table.summary("Recorded stock", String.format("%,d", recorded))
                .summary("Usable stock", String.format("%,d", recorded - expired))
                .summary("Low stock items", String.valueOf(low))
                .summary("Expired stock", String.format("%,d", expired));
        table.note = "Mixed units are administrative counts, not equivalent relief quantities. As of "
                + Time.longStamp(Time.now()) + ".";
        return table;
    }

    // ------------------------------------------------------------------ disasters & beneficiaries

    private ReportTable beneficiaryReport(Params params) throws SQLException {
        String type = params.type == null ? BENEFICIARY_TYPES[0] : params.type;
        List<Disaster> disasters = new DisasterRepository().findByBarangay(params.barangay.getBarangayId());
        List<Disaster> selected = new ArrayList<>();
        for (Disaster disaster : disasters) {
            if (params.disasterId == null || params.disasterId.isEmpty() || disaster.getDisasterId().equals(params.disasterId)) {
                selected.add(disaster);
            }
        }
        DisasterRepository repository = new DisasterRepository();
        if (type.equals("Assistance needed")) {
            ReportTable table = new ReportTable("Assistance needed report", "Disaster", "Category", "Needed (pending)",
                    "Available", "Coverage");
            Map<String, Integer> available = availableByCategory(params.barangay);
            for (Disaster disaster : selected) {
                DisasterStats stats = repository.stats(disaster.getDisasterId());
                for (String category : BeneficiaryRepository.NEED_CATEGORIES) {
                    Integer needed = stats.pendingNeeds.get(category);
                    if (needed != null && needed > 0) {
                        int have = available.getOrDefault(category, 0);
                        table.row(disaster.getName(), category, String.format("%,d", needed), String.format("%,d", have),
                                have >= needed ? "Covered" : "Short by " + String.format("%,d", needed - have));
                    }
                }
            }
            table.summary("Events", String.valueOf(selected.size()));
            table.note = "Needs are the requested quantities of households still waiting for assistance.";
            return table;
        }
        ReportTable table = new ReportTable("Families & vulnerable groups", "Disaster", "Area", "Families", "Individuals",
                "Seniors", "PWD", "Pregnant", "Infants", "Assisted", "Pending");
        int families = 0;
        int assisted = 0;
        int people = 0;
        for (Disaster disaster : selected) {
            final int[] counts = new int[2];
            List<String[]> rows = Db.read(connection -> Db.list(connection, "SELECT COALESCE(NULLIF(h.purok, ''), "
                    + "'Unassigned') AS area, COUNT(DISTINCT h.household_id) AS families, COUNT(m.member_id) AS people, "
                    + "SUM(CASE WHEN m.is_senior_citizen THEN 1 ELSE 0 END) AS seniors, "
                    + "SUM(CASE WHEN m.is_pwd THEN 1 ELSE 0 END) AS pwd, "
                    + "SUM(CASE WHEN m.is_pregnant THEN 1 ELSE 0 END) AS pregnant, "
                    + "SUM(CASE WHEN m.is_infant THEN 1 ELSE 0 END) AS infants, "
                    + "COUNT(DISTINCT CASE WHEN " + DisasterRepository.ASSISTED + " THEN h.household_id END) AS assisted "
                    + "FROM households h JOIN family_members m ON m.household_id = h.household_id "
                    + "WHERE h.disaster_id = ? GROUP BY area ORDER BY area", row -> new String[] { row.getString("area"),
                        row.getString("families"), row.getString("people"), row.getString("seniors"),
                        row.getString("pwd"), row.getString("pregnant"), row.getString("infants"),
                        row.getString("assisted") }, disaster.getDisasterId()));
            for (String[] row : rows) {
                int rowFamilies = Integer.parseInt(row[1]);
                int rowAssisted = Integer.parseInt(row[7]);
                families += rowFamilies;
                assisted += rowAssisted;
                people += Integer.parseInt(row[2]);
                table.row(disaster.getName(), row[0], row[1], row[2], row[3], row[4], row[5], row[6], row[7],
                        String.valueOf(rowFamilies - rowAssisted));
            }
        }
        table.summary("Families", String.format("%,d", families)).summary("Individuals", String.format("%,d", people))
                .summary("Assisted", String.format("%,d", assisted))
                .summary("Pending", String.format("%,d", families - assisted));
        return table;
    }

    private Map<String, Integer> availableByCategory(Barangay barangay) throws SQLException {
        Map<String, Integer> available = new LinkedHashMap<>();
        LocalDate today = Time.today();
        for (Inventory item : inventory.findByBarangay(barangay.getBarangayId(), false)) {
            available.merge(item.getCategory(), item.getAvailableQuantity(today), Integer::sum);
        }
        return available;
    }

    // ------------------------------------------------------------------ donations

    private ReportTable donationReport(Params params) throws SQLException {
        String type = params.type == null ? DONATION_TYPES[0] : params.type;
        String barangayId = params.barangay.getBarangayId();
        List<Object> args = new ArrayList<>();
        args.add(barangayId);
        StringBuilder filter = new StringBuilder();
        if (params.from != null) {
            filter.append(" AND d.donation_date >= ?");
            args.add(params.from);
        }
        if (params.to != null) {
            filter.append(" AND d.donation_date <= ?");
            args.add(params.to);
        }
        if (params.donorId != null && !params.donorId.isEmpty()) {
            filter.append(" AND d.donor_id = ?");
            args.add(params.donorId);
        }
        if (params.category != null && !params.category.isEmpty()) {
            filter.append(" AND i.category = ?");
            args.add(params.category);
        }
        String base = " FROM donation_items di JOIN donations d ON d.donation_id = di.donation_id "
                + "JOIN donors dn ON dn.donor_id = d.donor_id JOIN inventory_items i ON i.inventory_item_id = "
                + "di.inventory_item_id LEFT JOIN user_accounts ap ON ap.account_id = d.received_by_account_id "
                + "WHERE d.barangay_id = ? AND d.status = 'APPROVED'" + filter;
        ReportTable table;
        if (type.equals("By donor")) {
            table = new ReportTable("Donations by donor", "Donor", "Type", "Donations", "Units received", "Latest");
            for (String[] row : Db.read(connection -> Db.list(connection, "SELECT dn.name, dn.donor_type, "
                    + "COUNT(DISTINCT d.donation_id) AS donations, SUM(di.quantity) AS units, MAX(d.donation_date) AS latest"
                    + base + " GROUP BY dn.donor_id, dn.name, dn.donor_type ORDER BY units DESC",
                    r -> new String[] { r.getString("name"), label(r.getString("donor_type")), r.getString("donations"),
                        String.format("%,d", r.getInt("units")), Time.date(Db.date(r, "latest")) }, args.toArray()))) {
                table.row(row);
            }
        } else if (type.equals("By item")) {
            table = new ReportTable("Donations by item", "Item", "Category", "Donations", "Quantity received");
            for (String[] row : Db.read(connection -> Db.list(connection, "SELECT i.item_name, i.category, i.unit, "
                    + "COUNT(DISTINCT d.donation_id) AS donations, SUM(di.quantity) AS units" + base
                    + " GROUP BY i.inventory_item_id, i.item_name, i.category, i.unit ORDER BY i.item_name",
                    r -> new String[] { r.getString("item_name"), r.getString("category"), r.getString("donations"),
                        Units.quantity(r.getInt("units"), r.getString("unit")) }, args.toArray()))) {
                table.row(row);
            }
        } else {
            table = new ReportTable("Donations by period", "Date", "Reference", "Donor", "Item", "Quantity",
                    "Receiving officer");
            for (String[] row : Db.read(connection -> Db.list(connection, "SELECT d.donation_date, d.donation_id, "
                    + "dn.name AS donor, i.item_name, i.unit, di.quantity, ap.name AS officer" + base
                    + " ORDER BY d.donation_date DESC, d.donation_id DESC, di.line_number",
                    r -> new String[] { Time.date(Db.date(r, "donation_date")), r.getString("donation_id"),
                        r.getString("donor"), r.getString("item_name"), Units.quantity(r.getInt("quantity"), r.getString("unit")),
                        Db.text(r, "officer") }, args.toArray()))) {
                table.row(row);
            }
        }
        int donations = Db.read(connection -> Db.intValue(connection, "SELECT COUNT(DISTINCT d.donation_id)" + base,
                args.toArray()));
        table.summary("Confirmed donations", String.valueOf(donations));
        table.note = "Only confirmed donations are included.";
        return table;
    }

    // ------------------------------------------------------------------ distribution

    private ReportTable distributionReport(Params params) throws SQLException {
        String type = params.type == null ? DISTRIBUTION_TYPES[0] : params.type;
        List<Object> args = new ArrayList<>();
        args.add(params.barangay.getBarangayId());
        StringBuilder filter = new StringBuilder();
        if (params.from != null) {
            filter.append(" AND ds.distribution_date >= ?");
            args.add(params.from);
        }
        if (params.to != null) {
            filter.append(" AND ds.distribution_date <= ?");
            args.add(params.to);
        }
        if (params.disasterId != null && !params.disasterId.isEmpty()) {
            filter.append(" AND ds.disaster_id = ?");
            args.add(params.disasterId);
        }
        if (params.householdId != null && !params.householdId.isEmpty()) {
            filter.append(" AND ds.beneficiary_id = ?");
            args.add(params.householdId);
        }
        if (params.category != null && !params.category.isEmpty()) {
            filter.append(" AND i.category = ?");
            args.add(params.category);
        }
        String base = " FROM distribution_items di JOIN distributions ds ON ds.distribution_id = di.distribution_id "
                + "JOIN disasters dis ON dis.disaster_id = ds.disaster_id JOIN inventory_items i ON i.inventory_item_id = "
                + "di.inventory_item_id LEFT JOIN households hh ON hh.household_id = ds.beneficiary_id "
                + "LEFT JOIN evacuation_centers ec ON ec.center_id = ds.evacuation_center_id "
                + "WHERE ds.barangay_id = ? AND ds.status = 'APPROVED'" + filter;
        ReportTable table;
        if (type.equals("By area")) {
            table = new ReportTable("Distribution by area", "Disaster", "Area", "Households assisted", "Releases",
                    "Units released");
            for (String[] row : Db.read(connection -> Db.list(connection, "SELECT dis.name AS disaster, "
                    + "COALESCE(NULLIF(hh.purok, ''), 'Evacuation centers') AS area, "
                    + "COUNT(DISTINCT CASE WHEN ds.evacuation_center_id IS NULL THEN ds.beneficiary_id END) AS households, "
                    + "COUNT(DISTINCT ds.distribution_id) AS releases, SUM(di.quantity) AS units" + base
                    + " GROUP BY dis.disaster_id, dis.name, area ORDER BY dis.name, area", r -> new String[] {
                        r.getString("disaster"), r.getString("area"), r.getString("households"), r.getString("releases"),
                        String.format("%,d", r.getInt("units")) }, args.toArray()))) {
                table.row(row);
            }
        } else if (type.equals("By item")) {
            table = new ReportTable("Distribution by item", "Item", "Category", "Releases", "Quantity released");
            for (String[] row : Db.read(connection -> Db.list(connection, "SELECT i.item_name, i.category, i.unit, "
                    + "COUNT(DISTINCT ds.distribution_id) AS releases, SUM(di.quantity) AS units" + base
                    + " GROUP BY i.inventory_item_id, i.item_name, i.category, i.unit ORDER BY i.item_name",
                    r -> new String[] { r.getString("item_name"), r.getString("category"), r.getString("releases"),
                        Units.quantity(r.getInt("units"), r.getString("unit")) }, args.toArray()))) {
                table.row(row);
            }
        } else if (type.equals("By beneficiary")) {
            table = new ReportTable("Distribution by beneficiary", "Recipient", "Disaster", "Releases", "Units released",
                    "Last release");
            for (String[] row : Db.read(connection -> Db.list(connection, "SELECT COALESCE(ec.name, hh.household_name) AS "
                    + "recipient, dis.name AS disaster, COUNT(DISTINCT ds.distribution_id) AS releases, "
                    + "SUM(di.quantity) AS units, MAX(ds.distribution_date) AS last_date" + base
                    + " GROUP BY recipient, dis.disaster_id, dis.name ORDER BY recipient", r -> new String[] {
                        r.getString("recipient"), r.getString("disaster"), r.getString("releases"),
                        String.format("%,d", r.getInt("units")), Time.date(Db.date(r, "last_date")) }, args.toArray()))) {
                table.row(row);
            }
        } else {
            table = new ReportTable("Distribution by disaster", "Disaster", "Releases", "Households assisted",
                    "Units released", "Last release");
            for (String[] row : Db.read(connection -> Db.list(connection, "SELECT dis.name AS disaster, "
                    + "COUNT(DISTINCT ds.distribution_id) AS releases, "
                    + "COUNT(DISTINCT CASE WHEN ds.evacuation_center_id IS NULL THEN ds.beneficiary_id END) AS households, "
                    + "SUM(di.quantity) AS units, MAX(ds.distribution_date) AS last_date" + base
                    + " GROUP BY dis.disaster_id, dis.name ORDER BY dis.name", r -> new String[] { r.getString("disaster"),
                        r.getString("releases"), r.getString("households"), String.format("%,d", r.getInt("units")),
                        Time.date(Db.date(r, "last_date")) }, args.toArray()))) {
                table.row(row);
            }
        }
        int releases = Db.read(connection -> Db.intValue(connection, "SELECT COUNT(DISTINCT ds.distribution_id)" + base,
                args.toArray()));
        table.summary("Confirmed releases", String.valueOf(releases));
        table.note = "Only confirmed releases are included. Mixed units are administrative counts.";
        return table;
    }

    private static String label(String donorType) {
        switch (donorType) {
            case "ORGANIZATION":
                return "Organization";
            case "AGENCY":
                return "Government agency";
            default:
                return "Individual";
        }
    }
}
