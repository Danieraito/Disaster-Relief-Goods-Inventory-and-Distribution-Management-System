package GUI;

import GUI.Theme.Tone;
import LIB.Disaster;
import LIB.DisasterController;
import LIB.Time;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.swing.JComponent;

/** Add disaster: creates the event and activates it for registration. */
class DisasterFormPage extends Page {
    @Override String module() { return "Disasters"; }
    @Override String crumb() { return "Disasters / Add disaster"; }

    @Override
    JComponent build() {
        Input name = new Input("").placeholder("e.g. Typhoon Amihan, Purok 2 flood");
        Dropdown<String> type = Dropdown.of("Typhoon", "Flood", "Earthquake", "Fire", "Landslide", "Other");
        Input date = new Input(Time.date(Time.today()));
        Dropdown<String> severity = Dropdown.of("Low", "Moderate", "High", "Critical");
        severity.setSelected("Moderate");
        List<String> areas = new ArrayList<>();
        for (int purok = 1; purok <= 7; purok++) {
            areas.add("Purok " + purok);
        }
        areas.add("Barangay-wide");
        MultiSelect area = new MultiSelect(areas, "Select affected areas");
        Input location = new Input("").placeholder("Street, purok and barangay");
        Input site = new Input("").placeholder("e.g. Maligaya Elementary School (optional)");
        Input contact = new Input(Session.user.getName());
        Area note = new Area("", 3);

        Field fName = new Field("Disaster name *", name, "Include the location or event name to distinguish repeat incidents in reports.");
        Field fDate = new Field("Date of occurrence *", date);
        Field fArea = new Field("Affected area *", area);
        Field fLocation = new Field("Location / affected addresses *", location);

        Wrap summaryName = new Wrap("—", Theme.medium(12.5f), Theme.TEXT);
        Wrap summaryType = new Wrap("Typhoon", Theme.medium(12.5f), Theme.TEXT);
        Wrap summaryArea = new Wrap("—", Theme.medium(12.5f), Theme.TEXT);
        Wrap summarySeverity = new Wrap("Moderate", Theme.medium(12.5f), Theme.TEXT);
        Runnable refresh = () -> {
            summaryName.setText(name.getText().trim().isEmpty() ? "—" : name.getText().trim());
            summaryType.setText(type.getSelected());
            Set<String> selected = area.getSelected();
            summaryArea.setText(selected.isEmpty() ? "—" : String.join(", ", selected));
            summarySeverity.setText(severity.getSelected());
        };
        name.getDocument().addDocumentListener(new SimpleListener(refresh));
        type.onChange(refresh);
        area.onChange(refresh);
        severity.onChange(refresh);

        VBox error = new VBox(0);
        Btn save = UI.primary("Save & activate event");
        save.addActionListener(event -> {
            error.removeAll();
            error.revalidate();
            try {
                LocalDate when = Fmt.parseDate(date.getText());
                if (when == null) {
                    throw new IllegalArgumentException("Enter the date of occurrence.");
                }
                String areaText = String.join(", ", area.getSelected());
                save.setEnabled(false);
                Task.run(() -> new DisasterController().addDisaster(name.getText(), type.getSelected(), when,
                        severity.getSelected(), areaText, location.getText(), note.getText(), contact.getText(),
                        site.getText(), Session.workspace, Session.user), created -> {
                            Session.disasterId = created.getDisasterId();
                            go(new DisasterDetailPage(created.getDisasterId(), "Event activated · " + created.getName()
                                    + " (" + created.getDisasterId() + ") is open. Register affected households next."));
                        }, failure -> {
                            save.setEnabled(true);
                            show(error, failure);
                        });
            } catch (IllegalArgumentException failure) {
                show(error, failure);
            }
        });

        Card form = new Card(14);
        form.put(UI.h2("Event information"), UI.muted("All fields marked * are required."), fName,
                new Cols(14, 1, 1, 1).put(new Field("Disaster type *", type), fDate, new Field("Severity *", severity)),
                new Cols(14, 1, 1.4).put(fArea, fLocation), new Field("Evacuation site", site),
                new Field("Initial situation note", note), new Field("Assessment contact", contact),
                new Cols(14, 1, 0, 0).put(UI.smallPara("Changes are recorded with your name, date and time."),
                        UI.secondary("Cancel", () -> go(new DisastersPage())), save));

        Card side = new Card(12);
        side.put(UI.h2("Event setup summary"), UI.left(UI.pill("Draft not saved", Tone.NEUTRAL)),
                fact("Disaster", summaryName), UI.fact("Event ID", "Assigned after save"), fact("Type", summaryType),
                fact("Area", summaryArea), fact("Severity", summarySeverity), UI.fact("Created by", Session.user.getName()),
                UI.para("Next: register affected families, identify vulnerable members and assistance needs, then plan "
                        + "distributions using available stock."),
                UI.note(Tone.WARN, "Saving activates the event", "The event becomes Active immediately. Inventory is "
                        + "unchanged; beneficiary registration and distribution become available."));
        VBox page = new VBox(18);
        page.put(UI.pageHeader("Add disaster", "Create an event so households can be registered and assistance can be planned.", null),
                error, new Cols(22, 2.2, 1).put(form, side));
        return page;
    }

    private static Cols fact(String key, Wrap value) {
        return new Cols(12, -128, 1).put(UI.label(key, Theme.regular(12.5f), Theme.MUTED), value);
    }

    private void show(VBox error, Throwable failure) {
        error.removeAll();
        error.put(UI.alert(Tone.DANGER, "This event could not be saved", Task.message(failure)));
        error.revalidate();
        error.repaint();
    }
}
