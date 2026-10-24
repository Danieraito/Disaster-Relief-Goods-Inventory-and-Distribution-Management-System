package LIB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Household and assistance counts for one disaster, read with a handful of aggregate queries. */
public class DisasterStats {
    public static final class AreaRow {
        public final String area;
        public final int registered;
        public final int assisted;

        AreaRow(String area, int registered, int assisted) {
            this.area = area;
            this.registered = registered;
            this.assisted = assisted;
        }

        public int pending() {
            return registered - assisted;
        }
    }

    public int registered;
    public int individuals;
    public int assisted;
    public int seniors;
    public int pwd;
    public int pregnant;
    public int infants;
    public int vulnerablePeople;
    public int totallyDamaged;
    public int partiallyDamaged;
    public int evacuated;
    public final List<AreaRow> areas = new ArrayList<>();
    /** Requested quantities of households still waiting for assistance, by inventory category. */
    public final Map<String, Integer> pendingNeeds = new LinkedHashMap<>();

    public int pending() {
        return registered - assisted;
    }

    public int percentAssisted() {
        return registered == 0 ? 0 : Math.round(assisted * 100f / registered);
    }
}
