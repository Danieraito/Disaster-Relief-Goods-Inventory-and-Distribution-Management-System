package LIB;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A standard bundle released to one family, for example "Family Food Pack = 6 kg rice +
 * 4 canned goods + 6 coffee". Packs let officials release the same goods to many households at once.
 */
public class ReliefPack implements BarangayScoped {

    /** One item in the pack and how many units each family gets. */
    public static final class Line {
        private final Inventory inventory;
        private final int quantityPerFamily;

        public Line(Inventory inventory, int quantityPerFamily) {
            this.inventory = Objects.requireNonNull(inventory, "inventory");
            if (quantityPerFamily <= 0) {
                throw new IllegalArgumentException("Quantity per family must be positive.");
            }
            this.quantityPerFamily = quantityPerFamily;
        }

        public Inventory getInventory() {
            return inventory;
        }

        public int getQuantityPerFamily() {
            return quantityPerFamily;
        }
    }

    private final String packId;
    private String name;
    private final Barangay barangay;
    private final List<Line> lines = new ArrayList<>();

    public ReliefPack(String packId, String name, Barangay barangay) {
        this.packId = requireText(packId, "packId");
        this.name = requireText(name, "Pack name");
        this.barangay = Objects.requireNonNull(barangay, "barangay");
    }

    public String getPackId() {
        return packId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = requireText(name, "Pack name");
    }

    @Override
    public Barangay getBarangay() {
        return barangay;
    }

    public List<Line> getLines() {
        return Collections.unmodifiableList(new ArrayList<>(lines));
    }

    /** Adds an item to the pack. Items must come from the pack's barangay and appear only once. */
    public void addLine(Inventory inventory, int quantityPerFamily) {
        if (!sharesBarangayWith(inventory)) {
            throw new IllegalArgumentException(inventory.getItemName() + " belongs to another barangay's inventory.");
        }
        for (Line line : lines) {
            if (line.getInventory().getItemId().equals(inventory.getItemId())) {
                throw new IllegalArgumentException(inventory.getItemName() + " is already in this pack.");
            }
        }
        lines.add(new Line(inventory, quantityPerFamily));
    }

    /** Stock of an item that can still be released today (has stock and has not expired). */
    public static int usableQuantity(Inventory inventory, LocalDate today) {
        int usable = 0;
        for (InventoryBatch batch : inventory.getBatches()) {
            if (!batch.isExpiredOn(today)) {
                usable += batch.getRemainingQuantity();
            }
        }
        return usable;
    }

    /**
     * How many families can receive this pack with the usable stock on hand. The item that runs
     * out first decides the answer.
     */
    public int familiesCovered(LocalDate today) {
        if (lines.isEmpty()) {
            return 0;
        }
        int covered = Integer.MAX_VALUE;
        for (Line line : lines) {
            covered = Math.min(covered, usableQuantity(line.getInventory(), today) / line.getQuantityPerFamily());
        }
        return covered;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        return value.trim();
    }
}
