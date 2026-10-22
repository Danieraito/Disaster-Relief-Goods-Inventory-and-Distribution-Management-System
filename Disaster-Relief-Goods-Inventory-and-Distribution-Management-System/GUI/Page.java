package GUI;

import javax.swing.JComponent;

/**
 * One screen of the app. load() runs off the screen thread and reads the database into fields;
 * build() then draws the screen from those fields. Call rebuild() after changing local state (a tab,
 * a filter) and reload() after changing data.
 */
abstract class Page {
    Shell shell;

    /** The navigation tab this page belongs to, e.g. "Inventory". */
    abstract String module();

    /** Breadcrumb text after "Workspace /", e.g. "Inventory / Add item". */
    abstract String crumb();

    void load() throws Exception {
    }

    abstract JComponent build();

    final void rebuild() {
        shell.display(this, build());
    }

    final void reload() {
        shell.open(this);
    }

    final void go(Page next) {
        shell.open(next);
    }
}
