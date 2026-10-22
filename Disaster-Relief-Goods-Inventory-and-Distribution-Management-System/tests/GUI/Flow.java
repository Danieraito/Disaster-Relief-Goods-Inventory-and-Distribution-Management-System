package GUI;

import LIB.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import java.util.function.Function;
import javax.swing.*;

/** End-to-end walkthrough that drives the real screens (typing into fields, pressing buttons). */
public class Flow {
    static Shell shell;

    // ---------------------------------------------------------------- helpers
    static <T extends Component> void collect(Component root, Class<T> type, List<T> out) {
        if (type.isInstance(root)) out.add(type.cast(root));
        if (root instanceof Container) for (Component c : ((Container) root).getComponents()) collect(c, type, out);
    }
    static <T extends Component> List<T> all(Class<T> type) throws Exception {
        List<T> out = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> { if (shell != null) collect(shell.getContentPane(), type, out); });
        return out;
    }
    static Btn button(String text) throws Exception {
        for (Btn b : all(Btn.class)) if (b.getText().equals(text) && b.isShowing()) return b;
        return null;
    }
    static boolean has(String buttonText) throws Exception { return button(buttonText) != null; }
    static Field field(String label) throws Exception {
        for (Field f : all(Field.class)) {
            if (f.getComponentCount() > 0 && f.getComponent(0) instanceof JLabel && ((JLabel) f.getComponent(0)).getText().startsWith(label) && f.isShowing()) return f;
        }
        throw new IllegalStateException("No field labelled: " + label);
    }
    static void type(String label, String text) throws Exception {
        Field f = field(label);
        SwingUtilities.invokeAndWait(() -> ((javax.swing.text.JTextComponent) f.input()).setText(text));
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void pick(String label, String optionStart) throws Exception {
        Field f = field(label);
        JComponent input = f.input();
        SwingUtilities.invokeAndWait(() -> {
            try {
                if (input instanceof Dropdown) {
                    java.lang.reflect.Field o = Dropdown.class.getDeclaredField("options"); o.setAccessible(true);
                    java.lang.reflect.Field l = Dropdown.class.getDeclaredField("label"); l.setAccessible(true);
                    Function fn = (Function) l.get(input);
                    for (Object opt : (List) o.get(input)) if (String.valueOf(fn.apply(opt)).startsWith(optionStart)) { ((Dropdown) input).select(opt); return; }
                    throw new IllegalStateException("No option '" + optionStart + "' for " + label);
                } else if (input instanceof MultiSelect) {
                    ((MultiSelect) input).setSelected(new LinkedHashSet<>(Arrays.asList(optionStart.split(","))));
                } else throw new IllegalStateException("Not a dropdown: " + label);
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        });
    }
    static void click(String text) throws Exception {
        Btn b = button(text);
        if (b == null) throw new IllegalStateException("No button: " + text);
        SwingUtilities.invokeAndWait(b::doClick);
    }
    static void check(String text) throws Exception {
        for (Check c : all(Check.class)) if (c.getText().startsWith(text) && c.isShowing()) { SwingUtilities.invokeAndWait(() -> { if (!c.isSelected()) c.doClick(); }); return; }
        throw new IllegalStateException("No checkbox: " + text);
    }
    interface Cond { boolean ok() throws Exception; }
    static void waitUntil(String what, Cond cond) throws Exception {
        for (int i = 0; i < 150; i++) {
            boolean ok; try { ok = cond.ok(); } catch (Exception e) { ok = false; }
            if (ok) { Thread.sleep(250); return; }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Timed out waiting for: " + what + "  (page=" + (shell == null ? "-" : shell.currentPage().getClass().getSimpleName()) + ")");
    }
    static void waitButton(String text) throws Exception { waitUntil("button " + text, () -> { List<Btn> l = new ArrayList<>(); collect(shell.getContentPane(), Btn.class, l); for (Btn b : l) if (b.getText().equals(text) && b.isShowing()) return true; return false; }); }
    static boolean textShown(String needle) throws Exception {
        final boolean[] found = new boolean[1];
        SwingUtilities.invokeAndWait(() -> { List<JLabel> labels = new ArrayList<>(); collect(shell.getContentPane(), JLabel.class, labels); for (JLabel l : labels) if (l.getText() != null && l.getText().contains(needle)) found[0] = true;
            List<Wrap> wraps = new ArrayList<>(); collect(shell.getContentPane(), Wrap.class, wraps); for (Wrap w : wraps) { try { java.lang.reflect.Field f = Wrap.class.getDeclaredField("text"); f.setAccessible(true); if (((String) f.get(w)).contains(needle)) found[0] = true; } catch (Exception e) { } } });
        return found[0];
    }
    static void open(Page p) throws Exception { SwingUtilities.invokeAndWait(() -> shell.open(p)); Thread.sleep(700); }
    static void step(String s) { System.out.println("STEP " + s); }
    static String sql(String q) throws Exception {
        return Db.read(c -> Db.list(c, q, r -> r.getString(1))).toString();
    }

    // ---------------------------------------------------------------- the walkthrough
    public static void main(String[] args) throws Exception {
        System.setProperty("awt.useSystemAAFontSettings", "on"); System.setProperty("swing.aatext", "true");
        SwingUtilities.invokeAndWait(() -> { try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception e) { } Theme.init(); });

        step("login screen");
        final LoginFrame[] login = new LoginFrame[1];
        SwingUtilities.invokeAndWait(() -> { login[0] = new LoginFrame(); login[0].setSize(1300, 900); login[0].setVisible(true); });
        Thread.sleep(1500);
        List<Input> inputs = new ArrayList<>(); SwingUtilities.invokeAndWait(() -> collect(login[0].getContentPane(), Input.class, inputs));
        List<JPasswordField> pw = new ArrayList<>(); SwingUtilities.invokeAndWait(() -> collect(login[0].getContentPane(), JPasswordField.class, pw));
        // wrong password first
        SwingUtilities.invokeAndWait(() -> { inputs.get(0).setText("m.delacruz@maligaya.example"); pw.get(0).setText("wrong-password"); });
        List<Btn> btns = new ArrayList<>(); SwingUtilities.invokeAndWait(() -> collect(login[0].getContentPane(), Btn.class, btns));
        Btn signIn = btns.stream().filter(b -> b.getText().equals("Sign in to workspace")).findFirst().get();
        SwingUtilities.invokeAndWait(signIn::doClick); Thread.sleep(1200);
        final boolean[] stillOpen = new boolean[1]; SwingUtilities.invokeAndWait(() -> stillOpen[0] = login[0].isShowing());
        if (!stillOpen[0]) throw new IllegalStateException("login accepted a wrong password");
        System.out.println("  wrong password rejected");
        SwingUtilities.invokeAndWait(() -> pw.get(0).setText("Maligaya#2026"));
        SwingUtilities.invokeAndWait(signIn::doClick);
        waitUntil("shell window", () -> { for (Window w : Window.getWindows()) if (w instanceof Shell && w.isShowing()) { shell = (Shell) w; return true; } return false; });
        shell.setSize(1400, 1000);
        waitButton("New distribution");
        System.out.println("  signed in as " + Session.user.getName() + " (" + Session.user.getRoleLabel() + ")");

        step("add inventory item");
        open(new InventoryPage()); waitButton("Add item"); click("Add item"); waitButton("Save item");
        type("Item name", "Oral rehydration salts"); pick("Category", "Medicine"); pick("Unit", "Box");
        type("Opening quantity", "5"); type("Expiry date", "31 Mar 2027"); type("Low-stock threshold", "20");
        type("Storage location", "Barangay clinic · Shelf C-02"); type("Opening stock note", "Opening count verified against clinic stock card.");
        waitUntil("save enabled", () -> { try { return button("Save item").isEnabled(); } catch (Exception e) { return false; } });
        click("Save item"); waitButton("Adjust stock");
        System.out.println("  created: " + sql("SELECT CONCAT(inventory_item_id,' qty=',(SELECT SUM(remaining_quantity) FROM inventory_batches b WHERE b.inventory_item_id=i.inventory_item_id)) FROM inventory_items i WHERE item_name='Oral rehydration salts'"));

        step("adjust stock (damage)");
        click("Adjust stock"); waitButton("Review adjustment");
        type("Quantity", "2"); type("Reason / evidence", "Two boxes crushed in storage; verified by Ana Mendoza.");
        click("Review adjustment"); waitButton("Confirm adjustment"); click("Confirm adjustment"); waitButton("Back to item");
        System.out.println("  after adjustment: " + sql("SELECT SUM(remaining_quantity) FROM inventory_batches b JOIN inventory_items i ON i.inventory_item_id=b.inventory_item_id WHERE i.item_name='Oral rehydration salts'"));

        step("new disaster");
        open(new DisastersPage()); waitButton("Add disaster"); click("Add disaster"); waitButton("Save & activate event");
        type("Disaster name", "Purok 5 residential fire"); pick("Disaster type", "Fire"); pick("Severity", "Moderate");
        pick("Affected area", "Purok 5"); type("Location / affected addresses", "Mabini Street, Purok 5, Barangay Maligaya");
        type("Evacuation site", "Maligaya Elementary School");
        click("Save & activate event"); waitUntil("event page", () -> shell.currentPage() instanceof DisasterDetailPage); waitButton("Register household");
        System.out.println("  disaster: " + sql("SELECT CONCAT(disaster_id,' ',status) FROM disasters WHERE name='Purok 5 residential fire'"));

        step("register household");
        click("Register household"); waitButton("Add family member");
        type("Head of household", "Roberto Reyes"); type("Contact number", "0917 000 0042"); type("Complete address", "18 Mabini Street, Barangay Maligaya");
        pick("Purok", "Purok 5");
        type("Member name", "Roberto Reyes"); type("Age", "34"); pick("Sex", "Male"); click("Add family member");
        type("Member name", "Elena Reyes"); type("Age", "31"); pick("Sex", "Female"); pick("Special category", "Pregnant"); click("Add family member");
        type("Member name", "Rosa Reyes"); type("Age", "67"); pick("Sex", "Female"); pick("Special category", "Senior citizen"); click("Add family member");
        type("Member name", "Mateo Reyes"); type("Age", "6 months"); pick("Sex", "Male"); pick("Special category", "Infant"); click("Add family member");
        pick("House damage", "Partially"); pick("Evacuation status", "Evacuated"); type("Assessment note", "Roof damage; staying at school evacuation center.");
        check("Household information and needs were verified");
        waitUntil("confirm enabled", () -> { try { return button("Confirm & register household").isEnabled(); } catch (Exception e) { return false; } });
        click("Confirm & register household"); waitUntil("household page", () -> shell.currentPage() instanceof HouseholdDetailPage); waitButton("Prepare distribution");
        System.out.println("  household: " + sql("SELECT CONCAT(household_id,' members=',(SELECT COUNT(*) FROM family_members m WHERE m.household_id=h.household_id),' head=',household_name) FROM households h WHERE household_name='Roberto Reyes'"));

        step("record donation (official posts directly)");
        open(new DonationsPage()); waitButton("Record donation"); click("Record donation"); waitButton("Review donation");
        pick("Donor", "Bayanihan");
        List<Dropdown> dd = new ArrayList<>(); SwingUtilities.invokeAndWait(() -> collect(shell.getContentPane(), Dropdown.class, dd));
        Dropdown<?> itemPicker = null; for (Dropdown d : dd) { try { java.lang.reflect.Field o = Dropdown.class.getDeclaredField("options"); o.setAccessible(true); List<?> l = (List<?>) o.get(d); if (!l.isEmpty() && l.get(0) instanceof Inventory) itemPicker = d; } catch (Exception e) { } }
        final Dropdown<?> picker = itemPicker;
        SwingUtilities.invokeAndWait(() -> { try { java.lang.reflect.Field o = Dropdown.class.getDeclaredField("options"); o.setAccessible(true); for (Object x : (List<?>) o.get(picker)) if (x instanceof Inventory && ((Inventory) x).getItemName().equals("Oral rehydration salts")) ((Dropdown) picker).select(x); } catch (Exception e) { throw new RuntimeException(e); } });
        Thread.sleep(400);
        List<Input> lineInputs = new ArrayList<>(); SwingUtilities.invokeAndWait(() -> collect(shell.getContentPane(), Input.class, lineInputs));
        for (Input in : lineInputs) { if (in.getText().isEmpty() && in.isShowing()) { /* quantity then expiry are the first empty inputs after the item picker */ } }
        // quantity and expiry inputs of the first line are the Inputs placeholdered "required"; find by order: quantity (empty, no placeholder) precedes expiry
        List<Input> empties = new ArrayList<>(); for (Input in : lineInputs) if (in.isShowing()) empties.add(in);
        SwingUtilities.invokeAndWait(() -> { for (Input in : empties) { try { java.lang.reflect.Field p = Input.class.getDeclaredField("placeholder"); p.setAccessible(true); if ("required".equals(p.get(in))) in.setText("30 Mar 2027"); } catch (Exception e) { } } });
        // quantity input: the empty input immediately before the expiry input in component order
        Input qty = null; for (int i = 0; i < empties.size(); i++) { try { java.lang.reflect.Field p = Input.class.getDeclaredField("placeholder"); p.setAccessible(true); if ("required".equals(p.get(empties.get(i)))) qty = empties.get(i - 1); } catch (Exception e) { } }
        final Input q = qty; SwingUtilities.invokeAndWait(() -> q.setText("10"));
        click("Review donation"); waitButton("Confirm & add to inventory");
        check("I verified the counted quantities"); click("Confirm & add to inventory"); waitUntil("receipt", () -> shell.currentPage() instanceof DonationDetailPage && has("Download PDF"));
        System.out.println("  after donation: " + sql("SELECT SUM(remaining_quantity) FROM inventory_batches b JOIN inventory_items i ON i.inventory_item_id=b.inventory_item_id WHERE i.item_name='Oral rehydration salts'")
                + "  donation: " + sql("SELECT CONCAT(donation_id,' ',status) FROM donations ORDER BY donation_id DESC LIMIT 1"));

        step("household release from the household record");
        Beneficiaries h = new BeneficiaryRepository().findById(sql("SELECT household_id FROM households WHERE household_name='Roberto Reyes'").replaceAll("[\\[\\]]", ""), null);
        open(new HouseholdDetailPage(h.getBeneficiaryId(), null)); waitButton("Prepare distribution"); click("Prepare distribution");
        waitButton("Review distribution"); Thread.sleep(800);
        System.out.println("  prefilled lines from needs: " + textShown("Available"));
        click("Review distribution"); waitButton("Confirm & issue claim receipt");
        check("I verified the household"); click("Confirm & issue claim receipt");
        waitUntil("receipt", () -> shell.currentPage() instanceof DistributionDetailPage && has("Download PDF"));
        System.out.println("  release: " + sql("SELECT CONCAT(distribution_id,' ',status) FROM distributions ORDER BY distribution_id DESC LIMIT 1"));
        open(new HouseholdDetailPage(h.getBeneficiaryId(), null)); Thread.sleep(500);
        System.out.println("  household assisted now: " + new BeneficiaryRepository().findById(h.getBeneficiaryId(), null).isAssisted());

        step("duplicate release is blocked");
        click("Prepare distribution"); waitButton("Review distribution"); Thread.sleep(600);
        click("Review distribution"); Thread.sleep(2500);
        System.out.println("  blocked banner shown: " + textShown("Release blocked") + "  VAL logged: " + sql("SELECT COUNT(*) FROM audit_logs WHERE action='VALIDATION_BLOCKED'"));

        step("done");
        System.exit(0);
    }
}
