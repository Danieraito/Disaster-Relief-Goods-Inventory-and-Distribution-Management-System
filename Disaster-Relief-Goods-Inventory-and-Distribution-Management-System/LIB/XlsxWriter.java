package LIB;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a plain .xlsx workbook (one sheet) without any library: an .xlsx file is a zip of a few XML files.
 * Styles: 0 normal, 1 bold, 2 table heading (bold on a light teal fill), 3 title.
 */
public final class XlsxWriter {
    private final List<int[]> styles = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();
    private final String sheetName;

    public XlsxWriter(String sheetName) {
        String clean = sheetName == null ? "Report" : sheetName.replaceAll("[\\[\\]:*?/\\\\]", " ").trim();
        this.sheetName = clean.isEmpty() ? "Report" : clean.substring(0, Math.min(31, clean.length()));
    }

    public XlsxWriter row(int style, String... cells) {
        List<String> row = new ArrayList<>();
        for (String cell : cells) {
            row.add(cell == null ? "" : cell);
        }
        rows.add(row);
        styles.add(new int[] { style });
        return this;
    }

    public XlsxWriter blank() {
        return row(0);
    }

    /** Adds the title, filter lines, headline figures, the table and its note. */
    public static void writeReport(Path file, String barangayLine, List<String> metaLines, ReportTable table)
            throws IOException {
        XlsxWriter sheet = new XlsxWriter(table.title);
        sheet.row(3, barangayLine);
        sheet.row(1, table.title);
        for (String line : metaLines) {
            sheet.row(0, line);
        }
        sheet.blank();
        for (String[] pair : table.summary) {
            sheet.row(1, pair[0], pair[1]);
        }
        if (!table.summary.isEmpty()) {
            sheet.blank();
        }
        sheet.row(2, table.headers.toArray(new String[0]));
        for (List<String> row : table.rows) {
            sheet.row(0, row.toArray(new String[0]));
        }
        if (!table.note.isEmpty()) {
            sheet.blank();
            sheet.row(0, table.note);
        }
        sheet.save(file);
    }

    public void save(Path file) throws IOException {
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                    + "</Types>");
            put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            put(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                    + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                    + "<sheets><sheet name=\"" + escape(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                    + "</Relationships>");
            put(zip, "xl/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<fonts count=\"3\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                    + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                    + "<font><b/><sz val=\"14\"/><color rgb=\"FF087F78\"/><name val=\"Calibri\"/></font></fonts>"
                    + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill>"
                    + "<fill><patternFill patternType=\"gray125\"/></fill>"
                    + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE6F3F1\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
                    + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                    + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                    + "<cellXfs count=\"4\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>"
                    + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");
            put(zip, "xl/worksheets/sheet1.xml", sheetXml());
        }
    }

    private String sheetXml() {
        int columns = 1;
        for (List<String> row : rows) {
            columns = Math.max(columns, row.size());
        }
        int[] widths = new int[columns];
        for (List<String> row : rows) {
            if (row.size() <= 1) {
                continue; // titles and notes may overflow into the next cells
            }
            for (int column = 0; column < row.size(); column++) {
                widths[column] = Math.max(widths[column], row.get(column).length());
            }
        }
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><cols>");
        for (int column = 0; column < columns; column++) {
            xml.append("<col min=\"").append(column + 1).append("\" max=\"").append(column + 1).append("\" width=\"")
                    .append(Math.min(60, Math.max(12, widths[column] + 3))).append("\" customWidth=\"1\"/>");
        }
        xml.append("</cols><sheetData>");
        for (int index = 0; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            xml.append("<row r=\"").append(index + 1).append("\">");
            for (int column = 0; column < row.size(); column++) {
                String value = row.get(column);
                if (value.isEmpty()) {
                    continue;
                }
                xml.append("<c r=\"").append(columnName(column)).append(index + 1).append("\" s=\"")
                        .append(styles.get(index)[0]).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escape(value)).append("</t></is></c>");
            }
            xml.append("</row>");
        }
        xml.append("</sheetData></worksheet>");
        return xml.toString();
    }

    private static String columnName(int index) {
        StringBuilder name = new StringBuilder();
        int number = index;
        while (number >= 0) {
            name.insert(0, (char) ('A' + number % 26));
            number = number / 26 - 1;
        }
        return name.toString();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String escape(String text) {
        StringBuilder safe = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&': safe.append("&amp;"); break;
                case '<': safe.append("&lt;"); break;
                case '>': safe.append("&gt;"); break;
                case '"': safe.append("&quot;"); break;
                default:
                    if (c >= 0x20 || c == '\n' || c == '\t') {
                        safe.append(c);
                    }
            }
        }
        return safe.toString();
    }
}
