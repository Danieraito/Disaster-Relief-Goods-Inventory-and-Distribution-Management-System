package LIB;

import java.time.LocalDateTime;

/** One line of an item's stock history: opening stock, donation, release, adjustment or record edit. */
public class StockMovement {
    public enum Kind { OPENING, DONATION, RELEASE, ADJUSTMENT, EDIT }

    public final Kind kind;
    public final String reference;
    public final LocalDateTime when;
    public final int delta;
    public final String title;
    public final String detail;
    public final String actor;
    public final String unit;
    public final String editBefore;
    public final String editAfter;
    public final String reasonCategory;
    public int before;
    public int after;

    public StockMovement(Kind kind, String reference, LocalDateTime when, int delta, String title, String detail,
            String actor, String unit, String editBefore, String editAfter, String reasonCategory) {
        this.kind = kind;
        this.reference = reference;
        this.when = when;
        this.delta = delta;
        this.title = title;
        this.detail = detail == null ? "" : detail;
        this.actor = actor == null ? "" : actor;
        this.unit = unit == null ? "" : unit;
        this.editBefore = editBefore;
        this.editAfter = editAfter;
        this.reasonCategory = reasonCategory;
    }

    public boolean changesStock() {
        return kind != Kind.EDIT;
    }
}
