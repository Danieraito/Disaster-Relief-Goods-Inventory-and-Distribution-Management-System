package GUI;

import javax.swing.JPanel;

/** A screen that reloads its data every time it is shown. */
abstract class RefreshablePanel extends JPanel {
    abstract void refresh();
}
