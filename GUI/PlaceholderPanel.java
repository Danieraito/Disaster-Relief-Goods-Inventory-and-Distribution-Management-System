package GUI;

import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/** Stand-in for a screen that has not been built yet. */
class PlaceholderPanel extends RefreshablePanel {
    PlaceholderPanel(String title) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 22f));
        add(heading, BorderLayout.NORTH);

        JLabel note = new JLabel("This screen is coming in the next step.", SwingConstants.CENTER);
        note.setFont(note.getFont().deriveFont(15f));
        add(note, BorderLayout.CENTER);
    }

    @Override
    void refresh() {
        // Nothing to load yet.
    }
}
