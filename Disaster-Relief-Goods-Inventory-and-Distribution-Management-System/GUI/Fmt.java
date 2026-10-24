package GUI;

import LIB.Time;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text formatting and parsing used by the screens. */
final class Fmt {
    private static final DateTimeFormatter[] DATE_INPUTS = {
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("MM-dd-yyyy"),
    };
    private static final Pattern NUMBER = Pattern.compile("^(.*?)(\\d[\\d,]*)(.*)$");

    private Fmt() {
    }

    static String n(int number) {
        return String.format("%,d", number);
    }

    /** Parses a typed date such as 31 Mar 2027 or 2027-03-31; blank gives null; anything else throws. */
    static LocalDate parseDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String value = text.trim();
        for (DateTimeFormatter format : DATE_INPUTS) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // try the next accepted format
            }
        }
        throw new IllegalArgumentException("Enter the date like " + Time.date(Time.today()) + ".");
    }

    /** Whole number of zero or more; throws with a readable message otherwise. */
    static int parseCount(String text, String what) {
        String value = text == null ? "" : text.trim().replace(",", "");
        if (!value.matches("\\d{1,9}")) {
            throw new IllegalArgumentException(what + " must be a whole number of 0 or more.");
        }
        return Integer.parseInt(value);
    }

    /** Turns stored ASCII (-> and a leading minus) into the typographic forms of the design. */
    static String pretty(String text) {
        if (text == null) {
            return "";
        }
        return text.replace(" -> ", " → ").replaceAll("(^|[\\s,(])-(\\d)", "$1−$2")
                .replace(" - ", " — ");
    }

    /** "Food 480" + "Food 600" becomes "Food 480 → 600"; plain values become "a → b". */
    static String beforeAfter(String before, String after) {
        if (before == null || before.isEmpty() || after == null || after.isEmpty()) {
            return pretty(before == null || before.isEmpty() ? after : before);
        }
        String[] left = before.split("\n");
        String[] right = after.split("\n");
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < left.length; index++) {
            String a = left[index];
            String b = index < right.length ? right[index] : "";
            Matcher first = NUMBER.matcher(a);
            Matcher second = NUMBER.matcher(b);
            if (out.length() > 0) {
                out.append('\n');
            }
            if (first.matches() && second.matches() && first.group(1).equals(second.group(1))
                    && first.group(3).equals(second.group(3))) {
                out.append(first.group(1)).append(first.group(2)).append(" → ").append(second.group(2))
                        .append(first.group(3));
            } else {
                out.append(a).append(" → ").append(b);
            }
        }
        return out.toString();
    }

    static String initialsOf(String name) {
        String[] parts = name.trim().split("\\s+");
        return parts.length == 1 ? parts[0].substring(0, 1).toUpperCase()
                : (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    static String firstName(String name) {
        return name == null ? "" : name.trim().split("\\s+")[0];
    }

    static String greeting() {
        int hour = Time.now().getHour();
        return hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
    }
}
