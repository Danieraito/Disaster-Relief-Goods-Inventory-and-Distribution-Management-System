package GUI;

import java.awt.Component;
import java.awt.Container;

public final class AiCenterPageRenderTest {
    public static void main(String[] args) {
        String answer = "## Critical now\n"
                + "| Issue | Detail (source) | Why it matters | Suggested action |\n"
                + "|-------|-----------------|----------------|------------------|\n"
                + "| Expired medicine | 8 boxes | Health risk | Isolate for disposal |\n"
                + "| Low stock | 10 boxes | May run out | Start procurement |\n"
                + "<br>\\*All actions require authorized human approval.";
        String normalized = AiCenterPage.normalizeMarkup(answer);
        if (normalized.contains("<br>") || normalized.split("\n").length < 6
                || AiCenterPage.plain("\\*All actions").startsWith("\\")) {
            throw new AssertionError("HTML breaks or escaped Markdown syntax were not normalized.");
        }
        Container rendered = AiCenterPage.render(answer);
        DataTable table = findTable(rendered);
        if (table == null || table.getComponentCount() != 3) {
            throw new AssertionError("Expected a four-column table with two rendered rows.");
        }
        System.out.println("AI answer formatting tests passed.");
    }

    private static DataTable findTable(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof DataTable table) {
                return table;
            }
            if (component instanceof Container child) {
                DataTable table = findTable(child);
                if (table != null) {
                    return table;
                }
            }
        }
        return null;
    }

}
