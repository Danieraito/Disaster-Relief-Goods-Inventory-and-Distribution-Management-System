package LIB;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Builds a minimal, barangay-scoped, de-identified snapshot for AI decision support. */
public final class AiContextService {
    private static final String PEOPLE_SQL = "SELECT COUNT(DISTINCT h.household_id) AS households, COUNT(f.member_id) AS people, "
            + "SUM(CASE WHEN f.member_id IS NOT NULL AND (f.is_senior_citizen=TRUE OR f.is_infant=TRUE OR f.is_pwd=TRUE OR f.is_pregnant=TRUE) THEN 1 ELSE 0 END) AS vulnerable "
            + "FROM households h JOIN disasters d ON d.disaster_id=h.disaster_id LEFT JOIN family_members f ON f.household_id=h.household_id "
            + "WHERE d.barangay_id=? AND UPPER(d.status) NOT IN ('CLOSED','RESOLVED')";
    private static final String NEEDS_SQL = "SELECT hn.category, SUM(hn.requested_quantity) AS requested "
            + "FROM household_needs hn JOIN households h ON h.household_id=hn.household_id "
            + "JOIN disasters d ON d.disaster_id=h.disaster_id WHERE d.barangay_id=? "
            + "AND UPPER(d.status) NOT IN ('CLOSED','RESOLVED') GROUP BY hn.category ORDER BY hn.category";

    public static final class Snapshot {
        public final String context;
        public final int itemCount;
        public final int lowStock;
        public final int expired;
        public final int activeDisasters;
        public final int pendingDonations;
        public final int pendingDistributions;
        public final int activeHouseholds, affectedIndividuals, vulnerablePeople;

        Snapshot(String context, int itemCount, int lowStock, int expired, int activeDisasters,
                int pendingDonations, int pendingDistributions, int activeHouseholds, int affectedIndividuals,
                int vulnerablePeople) {
            this.context = context;
            this.itemCount = itemCount;
            this.lowStock = lowStock;
            this.expired = expired;
            this.activeDisasters = activeDisasters;
            this.pendingDonations = pendingDonations;
            this.pendingDistributions = pendingDistributions;
            this.activeHouseholds = activeHouseholds;
            this.affectedIndividuals = affectedIndividuals;
            this.vulnerablePeople = vulnerablePeople;
        }
    }

    /** Counts and lists read in one database round trip. */
    private static final class Extras {
        int pendingDonations;
        int pendingDistributions;
        int households;
        int people;
        int vulnerable;
        List<String> needLines = new ArrayList<>();
    }

    public Snapshot load(Barangay barangay, UserAccount user) throws SQLException {
        if (barangay == null || user == null || !user.isActive() || !user.canAccessBarangay(barangay)) {
            throw new SecurityException("You cannot view AI insights for this barangay workspace.");
        }
        String barangayId = barangay.getBarangayId();
        LocalDate today = Time.today();
        List<Inventory> items = new InventoryRepository().findByBarangay(barangayId);
        List<Disaster> disasters = new DisasterRepository().findByBarangay(barangayId);
        Extras extras = Db.read(connection -> readExtras(connection, barangayId));

        int low = 0;
        int expired = 0;
        List<String> stockLines = new ArrayList<>(items.size());
        for (Inventory item : items) {
            int usable = item.getAvailableQuantity(today);
            int expiredQty = item.getExpiredQuantity(today);
            if (usable <= item.getLowStockThreshold()) {
                low++;
            }
            if (expiredQty > 0) {
                expired++;
            }
            stockLines.add("- " + item.getItemName() + " | category=" + item.getCategory() + " | usable stock=" + usable
                    + " " + item.getUnit() + " | low-stock threshold=" + item.getLowStockThreshold() + " " + item.getUnit()
                    + " | expired stock=" + expiredQty + " " + item.getUnit());
        }

        int active = 0;
        List<String> disasterLines = new ArrayList<>(disasters.size());
        for (Disaster disaster : disasters) {
            if (!"CLOSED".equalsIgnoreCase(disaster.getStatus()) && !"RESOLVED".equalsIgnoreCase(disaster.getStatus())) {
                active++;
            }
            String severity = disaster.getSeverity() == null || disaster.getSeverity().trim().isEmpty()
                    ? "not recorded" : disaster.getSeverity();
            disasterLines.add("- type=" + disaster.getType() + " | status=" + disaster.getStatus()
                    + " | severity=" + severity);
        }

        StringBuilder context = new StringBuilder();
        context.append("Scope: current barangay workspace only. Snapshot date: ").append(today).append(".\n")
                .append("SYSTEM-CALCULATED SUMMARY: inventory items=").append(items.size())
                .append("; items at/below low-stock threshold=").append(low)
                .append("; items with expired stock=").append(expired)
                .append("; non-closed disasters=").append(active)
                .append("; pending donations=").append(extras.pendingDonations)
                .append("; pending distributions=").append(extras.pendingDistributions)
                .append(".\n\nINVENTORY (barangay workspace only):\n")
                .append(stockLines.isEmpty() ? "No active inventory items recorded.\n" : String.join("\n", stockLines))
                .append("\n\nDISASTERS (no household names or personal data):\n")
                .append(disasterLines.isEmpty() ? "No disaster records found.\n" : String.join("\n", disasterLines))
                .append("\n\nAGGREGATED HOUSEHOLD NEEDS FOR NON-CLOSED DISASTERS (recorded requests; not reconciled against completed distributions):\n")
                .append(extras.needLines.isEmpty() ? "No household need quantities recorded.\n"
                        : String.join("\n", extras.needLines))
                .append("\n\nLIMITATIONS: This snapshot does not contain person-specific household needs, official per-person ration standards, "
                        + "transport capacity, pending delivery arrival times, or evacuation-centre occupancy. Household need categories may not use the same units as inventory items, and recorded requests are not proof of remaining unmet need. Do not infer these values.");

        return new Snapshot(context.toString(), items.size(), low, expired, active, extras.pendingDonations,
                extras.pendingDistributions, extras.households, extras.people, extras.vulnerable);
    }

    /** Runs every aggregate query on the same connection instead of opening one per query. */
    private static Extras readExtras(Connection connection, String barangayId) throws SQLException {
        Extras extras = new Extras();
        extras.pendingDonations = Db.intValue(connection,
                "SELECT COUNT(*) FROM donations WHERE barangay_id=? AND status='PENDING'", barangayId);
        extras.pendingDistributions = Db.intValue(connection,
                "SELECT COUNT(*) FROM distributions WHERE barangay_id=? AND status='PENDING'", barangayId);
        int[] people = Db.one(connection, PEOPLE_SQL, row -> new int[] {
                row.getInt("households"), row.getInt("people"), row.getInt("vulnerable") }, barangayId);
        if (people != null) {
            extras.households = people[0];
            extras.people = people[1];
            extras.vulnerable = people[2];
        }
        extras.needLines = Db.list(connection, NEEDS_SQL,
                row -> "- " + row.getString("category") + ": " + row.getLong("requested") + " recorded requested unit(s)",
                barangayId);
        return extras;
    }
}
