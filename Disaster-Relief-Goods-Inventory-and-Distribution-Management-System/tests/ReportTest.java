import LIB.*;
import java.util.*;

public class ReportTest {
    public static void main(String[] args) throws Exception {
        Barangay b = new BarangayRepository().findAll().get(0);
        ReportService svc = new ReportService();
        String[][] groups = {
            {"Inventory", String.join("|", ReportService.INVENTORY_TYPES)},
            {"Disaster & beneficiaries", String.join("|", ReportService.BENEFICIARY_TYPES)},
            {"Donations", String.join("|", ReportService.DONATION_TYPES)},
            {"Distribution", String.join("|", ReportService.DISTRIBUTION_TYPES)}};
        for (String[] g : groups) for (String type : g[1].split("\\|")) {
            ReportService.Params p = new ReportService.Params(); p.barangay = b; p.type = type;
            ReportTable t = svc.build(g[0], p);
            System.out.println(g[0] + " / " + type + " -> " + t.rows.size() + " rows, summary " + t.summary.size());
            p.category = "Food"; p.from = Time.today().minusDays(30);
            ReportTable t2 = svc.build(g[0], p);
            System.out.println("    filtered(Food, 30d) -> " + t2.rows.size() + " rows");
        }
        System.out.println("REPORTS OK");
    }
}
