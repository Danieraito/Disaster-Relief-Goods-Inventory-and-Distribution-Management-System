package LIB;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Philippine Standard Time helpers shared by the models and the GUI. */
public final class Time {
    public static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM · h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter LONG_STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy 'at' h:mm a", Locale.ENGLISH);

    private Time() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(MANILA);
    }

    public static LocalDate today() {
        return LocalDate.now(MANILA);
    }

    public static String date(LocalDate value) {
        return value == null ? "—" : DATE.format(value);
    }

    public static String shortDate(LocalDate value) {
        return value == null ? "—" : SHORT_DATE.format(value);
    }

    public static String stamp(LocalDateTime value) {
        return value == null ? "—" : STAMP.format(value);
    }

    public static String time(LocalDateTime value) {
        return value == null ? "—" : TIME.format(value);
    }

    public static String longStamp(LocalDateTime value) {
        return value == null ? "—" : LONG_STAMP.format(value);
    }

    /** Parses an ISO date (yyyy-MM-dd); blank text means "no date". */
    public static LocalDate parse(String iso) {
        return iso == null || iso.trim().isEmpty() ? null : LocalDate.parse(iso.trim());
    }
}
