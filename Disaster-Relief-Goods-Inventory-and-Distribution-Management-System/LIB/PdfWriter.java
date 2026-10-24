package LIB;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A tiny PDF writer for reports and receipts: headings, paragraphs, key/value blocks, tables that wrap and
 * paginate, and signature lines. Uses the built-in Helvetica fonts, so it needs no library and no font files.
 */
public final class PdfWriter {
    // Helvetica glyph widths for ASCII 32..126 (1/1000 em).
    private static final int[] WIDTHS = {
        278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
        556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
        1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
        667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
        333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
        556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584
    };
    private static final Charset ANSI = Charset.forName("windows-1252");
    private static final float MARGIN = 40f;

    private final float pageWidth;
    private final float pageHeight;
    private final List<StringBuilder> pages = new ArrayList<>();
    private StringBuilder page;
    private float y;
    private String footer = "";

    public PdfWriter(boolean landscape) {
        this.pageWidth = landscape ? 841.89f : 595.28f;
        this.pageHeight = landscape ? 595.28f : 841.89f;
        newPage();
    }

    // ------------------------------------------------------------------ text helpers

    public static float width(String text, float size, boolean bold) {
        float total = 0;
        for (char c : clean(text).toCharArray()) {
            total += (c >= 32 && c <= 126 ? WIDTHS[c - 32] : 556) * size / 1000f;
        }
        return bold ? total * 1.06f : total;
    }

    private static String clean(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("→", "->").replace("−", "-").replace("✓", "v").replace("\n", " ").replace("\r", " ")
                .replace("’", "'").replace("“", "\"").replace("”", "\"");
    }

    private static String escape(String text) {
        StringBuilder safe = new StringBuilder();
        for (char c : clean(text).toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') {
                safe.append('\\').append(c);
            } else if (c < 32) {
                safe.append(' ');
            } else if (ANSI.newEncoder().canEncode(c)) {
                safe.append(c);
            } else {
                safe.append('?');
            }
        }
        return safe.toString();
    }

    private List<String> wrap(String text, float maxWidth, float size, boolean bold) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : clean(text).split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (width(candidate, size, bold) <= maxWidth || line.length() == 0) {
                line = new StringBuilder(candidate);
                while (width(line.toString(), size, bold) > maxWidth && line.length() > 1) {
                    // a single very long word: break it
                    int cut = line.length() - 1;
                    while (cut > 1 && width(line.substring(0, cut), size, bold) > maxWidth) {
                        cut--;
                    }
                    lines.add(line.substring(0, cut));
                    line = new StringBuilder(line.substring(cut));
                }
            } else {
                lines.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        lines.add(line.toString());
        return lines;
    }

    private void text(float x, float baseline, String value, float size, boolean bold, float[] color) {
        page.append(String.format(Locale.ROOT, "%.3f %.3f %.3f rg BT /%s %.2f Tf %.2f %.2f Td (%s) Tj ET\n",
                color[0], color[1], color[2], bold ? "F2" : "F1", size, x, baseline, escape(value)));
    }

    private void rect(float x, float top, float w, float h, float[] fill) {
        page.append(String.format(Locale.ROOT, "%.3f %.3f %.3f rg %.2f %.2f %.2f %.2f re f\n", fill[0], fill[1], fill[2],
                x, top - h, w, h));
    }

    private void line(float x1, float y1, float x2, float y2, float[] color) {
        page.append(String.format(Locale.ROOT, "%.3f %.3f %.3f RG 0.6 w %.2f %.2f m %.2f %.2f l S\n", color[0],
                color[1], color[2], x1, y1, x2, y2));
    }

    private static final float[] INK = { 0.11f, 0.20f, 0.23f };
    private static final float[] MUTED = { 0.39f, 0.47f, 0.50f };
    private static final float[] TEAL = { 0.03f, 0.50f, 0.47f };
    private static final float[] TINT = { 0.90f, 0.95f, 0.945f };
    private static final float[] RULE = { 0.87f, 0.91f, 0.92f };

    private void newPage() {
        page = new StringBuilder();
        pages.add(page);
        y = pageHeight - MARGIN;
    }

    private void ensure(float needed) {
        if (y - needed < MARGIN + 18) {
            newPage();
        }
    }

    // ------------------------------------------------------------------ content

    public PdfWriter brand(String name, String subtitle) {
        text(MARGIN, y - 14, name, 15, true, TEAL);
        text(MARGIN, y - 28, subtitle, 8, false, MUTED);
        line(MARGIN, y - 36, pageWidth - MARGIN, y - 36, RULE);
        y -= 52;
        return this;
    }

    public PdfWriter title(String value) {
        ensure(30);
        text(MARGIN, y - 18, value, 18, true, INK);
        y -= 28;
        return this;
    }

    public PdfWriter subtitle(String value) {
        for (String part : wrap(value, pageWidth - 2 * MARGIN, 9, false)) {
            ensure(14);
            text(MARGIN, y - 10, part, 9, false, MUTED);
            y -= 13;
        }
        y -= 4;
        return this;
    }

    public PdfWriter heading(String value) {
        ensure(26);
        y -= 6;
        text(MARGIN, y - 11, value, 11, true, INK);
        y -= 20;
        return this;
    }

    public PdfWriter paragraph(String value) {
        for (String part : wrap(value, pageWidth - 2 * MARGIN, 9.5f, false)) {
            ensure(14);
            text(MARGIN, y - 10, part, 9.5f, false, INK);
            y -= 13;
        }
        y -= 3;
        return this;
    }

    public PdfWriter gap(float amount) {
        y -= amount;
        return this;
    }

    /** Label / value pairs laid out in two columns. */
    public PdfWriter keyValues(List<String[]> pairs) {
        float columnWidth = (pageWidth - 2 * MARGIN) / 2f;
        for (int index = 0; index < pairs.size(); index += 2) {
            ensure(30);
            float rowHeight = 0;
            for (int column = 0; column < 2 && index + column < pairs.size(); column++) {
                String[] pair = pairs.get(index + column);
                float x = MARGIN + column * columnWidth;
                text(x, y - 9, pair[0], 7.5f, false, MUTED);
                float used = 0;
                for (String part : wrap(pair[1], columnWidth - 14, 9.5f, true)) {
                    text(x, y - 21 - used, part, 9.5f, true, INK);
                    used += 12;
                }
                rowHeight = Math.max(rowHeight, 24 + used - 12);
            }
            y -= rowHeight + 6;
        }
        return this;
    }

    /** A table with a shaded header; columns are sized from their content and long cells wrap. */
    public PdfWriter table(List<String> headers, List<List<String>> rows) {
        int columns = headers.size();
        float available = pageWidth - 2 * MARGIN;
        float[] want = new float[columns];
        for (int column = 0; column < columns; column++) {
            want[column] = width(headers.get(column), 8, true) + 10;
            for (List<String> row : rows) {
                if (column < row.size()) {
                    want[column] = Math.max(want[column], Math.min(width(row.get(column), 8.5f, false) + 10, 260));
                }
            }
        }
        float total = 0;
        for (float value : want) {
            total += value;
        }
        float[] size = new float[columns];
        for (int column = 0; column < columns; column++) {
            size[column] = total <= available ? want[column] + (available - total) / columns
                    : Math.max(34, want[column] * available / total);
        }
        tableHeader(headers, size);
        for (List<String> row : rows) {
            List<List<String>> wrapped = new ArrayList<>();
            int lines = 1;
            for (int column = 0; column < columns; column++) {
                List<String> cell = wrap(column < row.size() ? row.get(column) : "", size[column] - 10, 8.5f, false);
                wrapped.add(cell);
                lines = Math.max(lines, cell.size());
            }
            float height = lines * 11 + 8;
            if (y - height < MARGIN + 18) {
                newPage();
                tableHeader(headers, size);
            }
            float x = MARGIN;
            for (int column = 0; column < columns; column++) {
                float baseline = y - 12;
                for (String part : wrapped.get(column)) {
                    text(x + 5, baseline, part, 8.5f, false, INK);
                    baseline -= 11;
                }
                x += size[column];
            }
            y -= height;
            line(MARGIN, y, pageWidth - MARGIN, y, RULE);
        }
        y -= 8;
        return this;
    }

    private void tableHeader(List<String> headers, float[] size) {
        ensure(40);
        rect(MARGIN, y, pageWidth - 2 * MARGIN, 20, TINT);
        float x = MARGIN;
        for (int column = 0; column < headers.size(); column++) {
            text(x + 5, y - 13, headers.get(column), 8, true, MUTED);
            x += size[column];
        }
        y -= 20;
    }

    public PdfWriter signatures(String leftLabel, String leftName, String leftNote, String rightLabel,
            String rightName, String rightNote) {
        ensure(80);
        y -= 28;
        float half = (pageWidth - 2 * MARGIN) / 2f;
        line(MARGIN, y, MARGIN + half - 30, y, MUTED);
        line(MARGIN + half + 10, y, pageWidth - MARGIN, y, MUTED);
        text(MARGIN, y - 12, leftLabel, 8, false, MUTED);
        text(MARGIN + half + 10, y - 12, rightLabel, 8, false, MUTED);
        text(MARGIN, y - 26, leftName, 9.5f, true, INK);
        text(MARGIN + half + 10, y - 26, rightName, 9.5f, true, INK);
        text(MARGIN, y - 38, leftNote, 8, false, MUTED);
        text(MARGIN + half + 10, y - 38, rightNote, 8, false, MUTED);
        y -= 52;
        return this;
    }

    public PdfWriter footer(String value) {
        this.footer = value == null ? "" : value;
        return this;
    }

    // ------------------------------------------------------------------ output

    public void save(Path file) throws IOException {
        Files.write(file, toBytes());
    }

    public byte[] toBytes() throws IOException {
        int count = pages.size();
        List<byte[]> objects = new ArrayList<>();
        StringBuilder kids = new StringBuilder();
        for (int index = 0; index < count; index++) {
            kids.append(5 + index * 2).append(" 0 R ");
        }
        objects.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));
        objects.add(bytes("<< /Type /Pages /Kids [" + kids + "] /Count " + count + " >>"));
        objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"));
        for (int index = 0; index < count; index++) {
            StringBuilder content = new StringBuilder(pages.get(index));
            String stamp = footer.isEmpty() ? "" : footer + "   ";
            String number = stamp + "Page " + (index + 1) + " of " + count;
            content.append(String.format(Locale.ROOT, "%.3f %.3f %.3f rg BT /F1 7.5 Tf %.2f 24 Td (%s) Tj ET\n",
                    MUTED[0], MUTED[1], MUTED[2], pageWidth - MARGIN - width(number, 7.5f, false), escape(number)));
            byte[] stream = content.toString().getBytes(ANSI);
            objects.add(bytes(String.format(Locale.ROOT, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %.2f %.2f] "
                    + "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents %d 0 R >>", pageWidth, pageHeight,
                    6 + index * 2)));
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(bytes("<< /Length " + stream.length + " >>\nstream\n"));
            body.write(stream);
            body.write(bytes("\nendstream"));
            objects.add(body.toByteArray());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes("%PDF-1.4\n"));
        List<Integer> offsets = new ArrayList<>();
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(out.size());
            out.write(bytes((index + 1) + " 0 obj\n"));
            out.write(objects.get(index));
            out.write(bytes("\nendobj\n"));
        }
        int xref = out.size();
        out.write(bytes("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n"));
        for (int offset : offsets) {
            out.write(bytes(String.format(Locale.ROOT, "%010d 00000 n \n", offset)));
        }
        out.write(bytes("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n"));
        return out.toByteArray();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(ANSI);
    }

    /** A ready-made PDF of a report table. */
    public static PdfWriter forReport(String barangayLine, List<String> metaLines, ReportTable table) {
        boolean landscape = table.headers.size() > 5;
        PdfWriter pdf = new PdfWriter(landscape).brand("BarangayCare", barangayLine).title(table.title);
        for (String meta : metaLines) {
            pdf.subtitle(meta);
        }
        if (!table.summary.isEmpty()) {
            pdf.keyValues(table.summary);
        }
        pdf.gap(6).table(table.headers, table.rows);
        if (!table.note.isEmpty()) {
            pdf.paragraph(table.note);
        }
        return pdf.footer("Restricted barangay records - handle household information with care");
    }
}
