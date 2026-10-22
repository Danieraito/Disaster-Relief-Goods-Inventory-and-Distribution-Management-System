package GUI;

import GUI.Theme.Tone;
import LIB.Disaster;
import LIB.Time;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/** The teal strip that shows which disaster a screen is working on, with a "Change disaster" button. */
final class DisasterBand {
    private DisasterBand() {
    }

    /** The disaster remembered in the session if it still exists, otherwise the newest active one. */
    static Disaster resolve(List<Disaster> all) {
        Disaster found = null;
        for (Disaster disaster : all) {
            if (disaster.getDisasterId().equals(Session.disasterId)) {
                found = disaster;
            }
        }
        if (found == null) {
            for (Disaster disaster : all) {
                if (disaster.isActive()) {
                    found = disaster;
                    break;
                }
            }
        }
        Session.disasterId = found == null ? null : found.getDisasterId();
        return found;
    }

    static JComponent of(Page page, Disaster disaster, List<Disaster> all) {
        VBox text = new VBox(3);
        text.put(UI.label(disaster.getName() + " · " + disaster.getDisasterId(), Theme.semibold(13), Theme.TEXT),
                UI.muted(Time.date(LocalDate.parse(disaster.getDate())) + " · " + disaster.getAffectedAreas() + " · "
                        + disaster.getSeverity() + " severity"));
        Pill state = UI.pill(disaster.isActive() ? "Active response" : "Closed event", disaster.isActive() ? Tone.TEAL : Tone.NEUTRAL);
        Btn change = UI.secondary("Change disaster", () -> choose(page, all));
        Cols row = new Cols(16, 1, 0, 0);
        row.put(text, state, change);
        return UI.band(row);
    }

    static JComponent of(Page page, List<Disaster> all, Disaster disaster) {
        return of(page, disaster, all);
    }

    static void choose(Page page, List<Disaster> all) {
        List<Disaster> options = new ArrayList<>(all);
        Dropdown<Disaster> picker = new Dropdown<>(options, disaster -> disaster.getName() + " · " + disaster.getDisasterId()
                + (disaster.isActive() ? "" : " (closed)"));
        for (Disaster disaster : options) {
            if (disaster.getDisasterId().equals(Session.disasterId)) {
                picker.setSelected(disaster);
            }
        }
        VBox form = new VBox(10);
        form.put(new Field("Disaster *", picker));
        boolean ok = Dialogs.form(page.shell, "Change disaster", "Households and releases always belong to one event.", form,
                "Use this disaster", () -> picker.getSelected() == null ? "Select a disaster." : null, 560);
        if (ok && picker.getSelected() != null) {
            Session.disasterId = picker.getSelected().getDisasterId();
            page.reload();
        }
    }

    /** What a screen shows when there is no disaster to work on. */
    static JComponent empty(Page page, String what) {
        VBox box = new VBox(14);
        box.put(UI.alert(Tone.INFO, "No disaster event yet", "Open a disaster event first. " + what,
                UI.primary("Add disaster", () -> page.go(new DisasterFormPage()))));
        return box;
    }
}
