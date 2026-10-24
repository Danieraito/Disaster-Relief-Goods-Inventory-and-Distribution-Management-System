package GUI;

import LIB.Barangay;
import LIB.UserAccount;

/** Who is signed in and which barangay workspace they are working in. */
final class Session {
    static UserAccount user;
    static Barangay workspace;
    /** The disaster chosen on the Beneficiaries / Distribution screens (null until chosen). */
    static String disasterId;

    private Session() {
    }

    static void clear() {
        user = null;
        workspace = null;
        disasterId = null;
    }

    static boolean canManage() {
        return user != null && user.canManageRecords();
    }

    static boolean isAdmin() {
        return user != null && user.canManageUsers();
    }
}
