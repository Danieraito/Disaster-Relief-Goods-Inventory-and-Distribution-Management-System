package GUI;

import LIB.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

/** Encoder submits, official approves and rejects; also checks what an encoder cannot see. */
public class Flow2 {
    static void signIn(String email, String password) throws Exception {
        UserAccount user = new UserRepository().authenticate(email, password).orElseThrow();
        SwingUtilities.invokeAndWait(() -> {
            if (Flow.shell != null) Flow.shell.dispose();
            Session.user = user;
            try { Session.workspace = new BarangayRepository().findAll().get(0); } catch (Exception e) { throw new RuntimeException(e); }
            Session.disasterId = null;
            Flow.shell = new Shell(); Flow.shell.setSize(1400, 1000); Flow.shell.setVisible(true);
        });
        Thread.sleep(300);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void selectItem(String itemName) throws Exception {
        List<Dropdown> dd = new ArrayList<>(); SwingUtilities.invokeAndWait(() -> Flow.collect(Flow.shell.getContentPane(), Dropdown.class, dd));
        for (Dropdown d : dd) {
            java.lang.reflect.Field o = Dropdown.class.getDeclaredField("options"); o.setAccessible(true);
            for (Object x : (List<?>) o.get(d)) if (x instanceof Inventory && ((Inventory) x).getItemName().equals(itemName)) { SwingUtilities.invokeAndWait(() -> d.select(x)); return; }
        }
        throw new IllegalStateException("no item picker");
    }

    static void setLineInputs(String quantity, String expiry) throws Exception {
        List<Input> inputs = new ArrayList<>(); SwingUtilities.invokeAndWait(() -> Flow.collect(Flow.shell.getContentPane(), Input.class, inputs));
        java.lang.reflect.Field p = Input.class.getDeclaredField("placeholder"); p.setAccessible(true);
        for (int i = 0; i < inputs.size(); i++) {
            String ph = (String) p.get(inputs.get(i));
            if ("required".equals(ph) || "optional".equals(ph)) {
                final Input exp = inputs.get(i), qty = inputs.get(i - 1);
                SwingUtilities.invokeAndWait(() -> { qty.setText(quantity); if (!expiry.isEmpty()) exp.setText(expiry); });
                return;
            }
        }
        throw new IllegalStateException("no line inputs");
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        SwingUtilities.invokeAndWait(() -> { try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception e) { } Theme.init(); });

        Flow.step("encoder cannot adjust or delete");
        signIn("l.garcia@maligaya.example", "Maligaya#2026");
        Flow.open(new ItemDetailPage("ITM-003", null)); Flow.waitButton("Edit item");
        System.out.println("  Adjust stock visible: " + Flow.has("Adjust stock") + "  Delete visible: " + Flow.has("Delete item safely"));
        Flow.open(new ReportsPage()); Thread.sleep(800);
        System.out.println("  Export Excel visible: " + Flow.has("Export Excel"));
        try { new InventoryController().adjustStock(new InventoryController().find("ITM-003", Session.user), "OPENING-ITM-003", StockAdjustment.Type.DECREASE, StockAdjustment.Reason.DAMAGE, 1, "x", Time.today(), Session.user); System.out.println("  BUG: encoder adjusted stock"); }
        catch (SecurityException e) { System.out.println("  controller refuses encoder: " + e.getMessage()); }

        Flow.step("encoder submits a donation for approval");
        int before = Integer.parseInt(Flow.sql("SELECT SUM(remaining_quantity) FROM inventory_batches WHERE inventory_item_id='ITM-004'").replaceAll("[\\[\\]]", ""));
        Flow.open(new DonationFormPage()); Flow.waitButton("Review donation");
        Flow.pick("Donor", "San Isidro"); selectItem("Blanket"); Thread.sleep(300); setLineInputs("25", "");
        Flow.click("Review donation"); Flow.waitButton("Submit for approval");
        System.out.println("  confirm button for encoder: " + Flow.has("Submit for approval") + "  direct post offered: " + Flow.has("Confirm & add to inventory"));
        Flow.check("I verified the counted quantities"); Flow.click("Submit for approval"); Thread.sleep(1800);
        String donationId = Flow.sql("SELECT donation_id FROM donations ORDER BY donation_id DESC LIMIT 1").replaceAll("[\\[\\]]", "");
        System.out.println("  " + donationId + " status=" + Flow.sql("SELECT status FROM donations WHERE donation_id='" + donationId + "'") + "  blanket stock unchanged: "
                + Flow.sql("SELECT SUM(remaining_quantity) FROM inventory_batches WHERE inventory_item_id='ITM-004'") + " (was " + before + ")");

        Flow.step("official approves it");
        signIn("m.delacruz@maligaya.example", "Maligaya#2026");
        Flow.open(new DonationDetailPage(donationId, null)); Flow.waitButton("Approve & add to inventory");
        Flow.click("Approve & add to inventory");
        Flow.waitUntil("receipt", () -> Flow.has("Download PDF")); 
        System.out.println("  " + donationId + " status=" + Flow.sql("SELECT status FROM donations WHERE donation_id='" + donationId + "'") + "  blanket stock now: "
                + Flow.sql("SELECT SUM(remaining_quantity) FROM inventory_batches WHERE inventory_item_id='ITM-004'"));

        Flow.step("encoder prepares a release, official approves it");
        signIn("l.garcia@maligaya.example", "Maligaya#2026");
        // pick a pending household of the newest disaster that has a food item remaining need
        BeneficiaryRepository.Page page = new BeneficiaryController().page(new DisasterController().list(Session.workspace, Session.user).stream().filter(d -> d.getName().equals("Typhoon Amihan")).findFirst().get(), new BeneficiaryRepository.Filter(), 1, 50, Session.user);
        BeneficiaryRepository.Row target = null; for (BeneficiaryRepository.Row r : page.rows) if (!r.assisted) { target = r; break; }
        System.out.println("  target household " + target.householdId + " " + target.head);
        Flow.open(new HouseholdDetailPage(target.householdId, null)); Flow.waitButton("Prepare distribution"); Flow.click("Prepare distribution");
        Flow.waitButton("Review distribution"); Thread.sleep(800);
        Flow.click("Review distribution"); Flow.waitButton("Submit for approval");
        Flow.check("I verified the household"); Flow.click("Submit for approval"); Thread.sleep(1800);
        String dst = Flow.sql("SELECT distribution_id FROM distributions ORDER BY distribution_id DESC LIMIT 1").replaceAll("[\\[\\]]", "");
        System.out.println("  " + dst + " status=" + Flow.sql("SELECT status FROM distributions WHERE distribution_id='" + dst + "'"));
        int foodBefore = Integer.parseInt(Flow.sql("SELECT SUM(remaining_quantity) FROM inventory_batches WHERE inventory_item_id='ITM-001'").replaceAll("[\\[\\]]", ""));
        signIn("m.delacruz@maligaya.example", "Maligaya#2026");
        Flow.open(new DistributionDetailPage(dst)); Flow.waitButton("Approve & issue receipt"); Flow.click("Approve & issue receipt");
        Flow.waitUntil("receipt", () -> Flow.has("Download PDF"));
        int foodAfter = Integer.parseInt(Flow.sql("SELECT SUM(remaining_quantity) FROM inventory_batches WHERE inventory_item_id='ITM-001'").replaceAll("[\\[\\]]", ""));
        System.out.println("  " + dst + " status=" + Flow.sql("SELECT status FROM distributions WHERE distribution_id='" + dst + "'") + "  food " + foodBefore + " -> " + foodAfter
                + "  household assisted: " + new BeneficiaryRepository().findById(target.householdId, null).isAssisted());

        Flow.step("audit log shows the whole trail");
        Flow.open(new AuditPage()); Thread.sleep(800);
        System.out.println("  audit rows: " + Flow.sql("SELECT COUNT(*) FROM audit_logs") + "  blocked: " + Flow.sql("SELECT COUNT(*) FROM audit_logs WHERE action='VALIDATION_BLOCKED'"));
        Flow.step("done");
        System.exit(0);
    }
}
