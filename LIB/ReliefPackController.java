package LIB;

/** Creates and removes relief pack templates. Only Admins and Barangay Officials may define packs. */
public class ReliefPackController {
    public ReliefPack createPack(String packId, String name, Barangay barangay, UserAccount user) {
        requireManager(user, barangay);
        return new ReliefPack(packId, name, barangay);
    }

    public void addItem(ReliefPack pack, Inventory inventory, int quantityPerFamily, UserAccount user) {
        requireManager(user, pack.getBarangay());
        pack.addLine(inventory, quantityPerFamily);
    }

    public void requireCanDelete(ReliefPack pack, UserAccount user) {
        requireManager(user, pack.getBarangay());
    }

    private static void requireManager(UserAccount user, Barangay barangay) {
        if (user == null || !user.isActive() || !user.canManageRecords() || !user.canAccessBarangay(barangay)) {
            throw new SecurityException("Only Admins and Barangay Officials can set up relief packs in this workspace.");
        }
    }
}
