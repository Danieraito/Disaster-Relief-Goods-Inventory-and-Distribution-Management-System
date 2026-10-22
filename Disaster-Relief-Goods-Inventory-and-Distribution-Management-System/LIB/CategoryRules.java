package LIB;

/** Small rules about inventory categories shared by the donation and item screens. */
public final class CategoryRules {
    private CategoryRules() {
    }

    /** Food, water and medicine must carry an expiry date on every batch. */
    public static boolean needsExpiry(String category) {
        return DonationController.needsExpiry(category);
    }
}
