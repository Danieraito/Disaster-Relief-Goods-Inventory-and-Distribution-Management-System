package LIB;

import java.util.ArrayList;
import java.util.List;

/** A finished report: a title, a few headline figures, column headings and rows of text. */
public class ReportTable {
    public final String title;
    public final List<String> headers = new ArrayList<>();
    public final List<List<String>> rows = new ArrayList<>();
    /** Label / value pairs shown above the table (for example "Usable stock", "2,297"). */
    public final List<String[]> summary = new ArrayList<>();
    public String note = "";

    public ReportTable(String title, String... headers) {
        this.title = title;
        for (String header : headers) {
            this.headers.add(header);
        }
    }

    public ReportTable row(String... cells) {
        List<String> row = new ArrayList<>();
        for (String cell : cells) {
            row.add(cell == null ? "" : cell);
        }
        rows.add(row);
        return this;
    }

    public ReportTable summary(String label, String value) {
        summary.add(new String[] { label, value });
        return this;
    }
}
