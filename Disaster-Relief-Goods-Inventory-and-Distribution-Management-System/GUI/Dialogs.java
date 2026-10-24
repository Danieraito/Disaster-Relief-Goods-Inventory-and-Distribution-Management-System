package GUI;

import GUI.Theme.Tone;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Modal dialogs drawn with the same components as the screens. */
final class Dialogs {
    private Dialogs() {
    }

    static JDialog create(Component owner, String title, int width, JComponent body) {
        Window window = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        JDialog dialog = new JDialog(window, title, Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        VBox content = new VBox(16).pad(26, 28, 26, 28);
        content.put(body);
        root.add(content, BorderLayout.CENTER);
        dialog.setContentPane(root);
        int height = content.heightForWidth(width) + 4;
        root.setPreferredSize(new Dimension(width, height));
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(window);
        return dialog;
    }

    static void message(Component owner, String title, String message, Tone tone) {
        VBox body = new VBox(14);
        body.put(UI.h2(title), UI.para(message).maxWidth(460));
        JDialog[] holder = new JDialog[1];
        body.put(UI.rightActions(UI.primary("OK", () -> holder[0].dispose())));
        holder[0] = create(owner, title, 520, body);
        holder[0].setVisible(true);
    }

    static void error(Component owner, Throwable error) {
        message(owner, "Something went wrong", Task.message(error), Tone.DANGER);
    }

    /** Asks a yes/no question; returns true when the confirm button was pressed. */
    static boolean confirm(Component owner, String title, String message, String confirmLabel, boolean dangerous) {
        boolean[] result = new boolean[1];
        JDialog[] holder = new JDialog[1];
        Btn confirm = dangerous ? UI.danger(confirmLabel) : UI.primary(confirmLabel);
        confirm.addActionListener(event -> {
            result[0] = true;
            holder[0].dispose();
        });
        VBox body = new VBox(14);
        body.put(UI.h2(title), UI.para(message).maxWidth(460),
                UI.rightActions(UI.secondary("Cancel", () -> holder[0].dispose()), confirm));
        holder[0] = create(owner, title, 520, body);
        holder[0].setVisible(true);
        return result[0];
    }

    /**
     * Shows a form. The validator returns an error message (kept on screen) or null to accept.
     * Returns true when the form was accepted.
     */
    static boolean form(Component owner, String title, String subtitle, JComponent form, String okLabel,
            Supplier<String> validator, int width) {
        boolean[] result = new boolean[1];
        JDialog[] holder = new JDialog[1];
        VBox error = new VBox(0);
        Btn ok = UI.primary(okLabel);
        ok.addActionListener(event -> {
            String problem = validator.get();
            error.removeAll();
            if (problem == null) {
                result[0] = true;
                holder[0].dispose();
                return;
            }
            error.put(UI.alert(Tone.DANGER, "Check the form", problem));
            holder[0].getContentPane().setPreferredSize(new Dimension(width, 0));
            holder[0].pack();
            holder[0].setSize(width, holder[0].getContentPane().getPreferredSize().height);
            Dimension size = new Dimension(width, ((VBox) ((JPanel) holder[0].getContentPane()).getComponent(0))
                    .heightForWidth(width) + 4);
            holder[0].getContentPane().setPreferredSize(size);
            holder[0].pack();
        });
        VBox body = new VBox(14);
        body.put(UI.h2(title));
        if (subtitle != null) {
            body.put(UI.mutedPara(subtitle));
        }
        body.put(form, error, UI.rightActions(UI.secondary("Cancel", () -> holder[0].dispose()), ok));
        holder[0] = create(owner, title, width, body);
        holder[0].setVisible(true);
        return result[0];
    }
}
