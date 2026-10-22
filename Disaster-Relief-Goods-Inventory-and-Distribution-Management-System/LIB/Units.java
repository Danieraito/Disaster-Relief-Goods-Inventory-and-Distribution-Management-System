package LIB;

/** Small wording helpers shared by the audit ledger, receipts and screens. */
public final class Units {
    private Units() {
    }

    /** "1 kit", "2 packs", "3 boxes". */
    public static String quantity(int amount, String unit) {
        return String.format("%,d", amount) + " " + plural(amount, unit);
    }

    public static String plural(int amount, String unit) {
        if (unit == null || unit.isEmpty() || amount == 1) {
            return unit == null ? "" : unit;
        }
        String lower = unit.toLowerCase();
        if (lower.endsWith("s") || lower.endsWith("kg")) {
            return unit;
        }
        if (lower.endsWith("x") || lower.endsWith("ch") || lower.endsWith("sh")) {
            return unit + "es";
        }
        return unit + "s";
    }

    /** Short label used in the ledger: Food, Water, Hygiene, Clothing, Medicine, Shelter. */
    public static String shortCategory(String category) {
        if (category == null) {
            return "";
        }
        switch (category) {
            case "Hygiene kits":
                return "Hygiene";
            case "Shelter materials":
                return "Shelter";
            default:
                return category;
        }
    }
}
