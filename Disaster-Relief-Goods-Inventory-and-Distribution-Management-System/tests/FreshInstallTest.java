import LIB.*;
import java.sql.Connection;
import java.util.*;

/** What the app does on a brand-new empty database: create tables, first admin, demo data, sign in, change password. */
public class FreshInstallTest {
    public static void main(String[] args) throws Exception {
        try (Connection c = DatabaseConnection.getConnection()) {
            System.out.println("initialized before: " + SchemaUpdater.isInitialized(c));
            SchemaUpdater.createBaseSchema(c);
            System.out.println("initialized after: " + SchemaUpdater.isInitialized(c));
        }
        SchemaUpdater.ensureUpToDate();
        UserRepository users = new UserRepository();
        System.out.println("no users yet: " + users.isEmpty());
        Barangay b = new BarangayRepository().create("Maligaya", "San Isidro", "Laguna", null);
        AdminAccount admin = new AdminAccount(UUID.randomUUID().toString(), "Kayle Admin", "kayle@school.example", "kayle@school.example", "S3cret-pass");
        users.create(admin, null);
        System.out.println(DemoData.seed(b, admin, m -> { }));
        UserAccount maria = users.authenticate("m.delacruz@maligaya.example", DemoData.PASSWORD, UserAccount.Role.BARANGAY_OFFICIAL, b.getBarangayId()).orElseThrow();
        users.changeOwnPassword(maria, DemoData.PASSWORD, "NewPassw0rd!");
        System.out.println("old password still works: " + users.authenticate("m.delacruz@maligaya.example", DemoData.PASSWORD).isPresent()
                + " | new password works: " + users.authenticate("m.delacruz@maligaya.example", "NewPassw0rd!").isPresent());
        // admin account administration
        UserAccountController uc = new UserAccountController();
        UserAccount leo = users.authenticate("l.garcia@maligaya.example", DemoData.PASSWORD).orElseThrow();
        String temp = uc.resetPassword(leo, admin);
        System.out.println("reset password works: " + users.authenticate("l.garcia@maligaya.example", temp).isPresent());
        uc.deactivate(leo, "Leaving the project", admin);
        System.out.println("deactivated can sign in: " + users.authenticate("l.garcia@maligaya.example", temp).isPresent());
        try { uc.deactivate(admin, "x", admin); System.out.println("BUG: last admin deactivated"); } catch (IllegalStateException e) { System.out.println("last admin protected: " + e.getMessage()); }
        uc.reactivate(leo, "Back on the team", admin);
        uc.changeRole(leo, UserAccount.Role.BARANGAY_OFFICIAL, b, "Promoted", admin);
        System.out.println("role now: " + users.findById(leo.getAccountId()).get().getRoleLabel());
        System.out.println("FRESH INSTALL OK");
    }
}
