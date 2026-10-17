package GUI;

import LIB.Barangay;
import LIB.BarangayRepository;
import LIB.Inventory;
import LIB.InventoryRepository;
import LIB.UserAccount;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

final class DataAccess {
    static final BarangayRepository BARANGAYS = new BarangayRepository();
    static final InventoryRepository INVENTORY = new InventoryRepository();

    private DataAccess() {
    }

    static List<Inventory> inventoryFor(UserAccount user) throws SQLException {
        List<Inventory> items = new ArrayList<>();
        if (user.getRole() == UserAccount.Role.ADMIN) {
            for (Barangay barangay : BARANGAYS.findAll()) {
                items.addAll(INVENTORY.findByBarangay(barangay.getBarangayId()));
            }
        } else {
            items.addAll(INVENTORY.findByBarangay(user.getBarangay().getBarangayId()));
        }
        return items;
    }

    static String workspaceName(UserAccount user) {
        return user.getBarangay() == null ? "All barangays (admin)" : user.getBarangay().getName();
    }

    static String roleLabel(UserAccount user) {
        switch (user.getRole()) {
            case ADMIN:
                return "Admin";
            case BARANGAY_OFFICIAL:
                return "Barangay Official";
            default:
                return "Encoder";
        }
    }
}