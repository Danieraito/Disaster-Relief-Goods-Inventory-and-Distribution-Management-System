package GUI;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/** Colors and fonts of the BarangayCare design. */
final class Theme {
    static final Color BG = new Color(0xF4F7F8);
    static final Color CARD = Color.WHITE;
    static final Color BORDER = new Color(0xDFE7EA);
    static final Color RULE = new Color(0xE8EDEF);
    static final Color TEXT = new Color(0x1C333B);
    static final Color BODY = new Color(0x2F444C);
    static final Color MUTED = new Color(0x647780);
    static final Color SUBTLE = new Color(0x9AA6AC);
    static final Color TEAL = new Color(0x087F78);
    static final Color TEAL_HOVER = new Color(0x076B65);
    static final Color TEAL_DARK = new Color(0x08655F);
    static final Color TEAL_TINT = new Color(0xE8F5F2);
    static final Color TEAL_BORDER = new Color(0xCBE6E2);
    static final Color HEADER_BG = new Color(0xF8FAFB);
    static final Color DISABLED_BG = new Color(0xEAEFF1);
    static final Color DISABLED_TEXT = new Color(0x93A0A5);
    static final Color WARN_BG = new Color(0xFFF6E3);
    static final Color WARN_BORDER = new Color(0xF1E3C8);
    static final Color WARN_TEXT = new Color(0x916111);
    static final Color WARN_ACCENT = new Color(0xC99A3B);
    static final Color DANGER_BG = new Color(0xFDEEEE);
    static final Color DANGER_BORDER = new Color(0xF3D7D7);
    static final Color DANGER_TEXT = new Color(0xB53C3C);
    static final Color DANGER_HOVER = new Color(0x9C2F2F);
    static final Color INFO_BG = new Color(0xEDF3FA);
    static final Color INFO_BORDER = new Color(0xD6E1ED);
    static final Color INFO_TEXT = new Color(0x2B5C8F);
    static final Color NEUTRAL_PILL = new Color(0xEEF2F4);

    /** Colour families used by alerts, pills and numbers. */
    enum Tone {
        NEUTRAL, TEAL, WARN, DANGER, INFO;

        Color bg() {
            switch (this) {
                case TEAL: return TEAL_TINT;
                case WARN: return WARN_BG;
                case DANGER: return DANGER_BG;
                case INFO: return INFO_BG;
                default: return NEUTRAL_PILL;
            }
        }

        Color border() {
            switch (this) {
                case TEAL: return TEAL_BORDER;
                case WARN: return WARN_BORDER;
                case DANGER: return DANGER_BORDER;
                case INFO: return INFO_BORDER;
                default: return BORDER;
            }
        }

        Color text() {
            switch (this) {
                case TEAL: return Theme.TEAL;
                case WARN: return WARN_TEXT;
                case DANGER: return DANGER_TEXT;
                case INFO: return INFO_TEXT;
                default: return MUTED;
            }
        }

        Color accent() {
            switch (this) {
                case TEAL: return Theme.TEAL;
                case WARN: return WARN_ACCENT;
                case DANGER: return DANGER_TEXT;
                case INFO: return INFO_TEXT;
                default: return SUBTLE;
            }
        }
    }

    private static final Map<String, Font> FONTS = new HashMap<>();
    private static boolean loaded;
    private static String fallback;

    private Theme() {
    }

    static synchronized void init() {
        if (loaded) {
            return;
        }
        loaded = true;
        for (String weight : new String[] { "Regular", "Medium", "SemiBold", "Bold" }) {
            try (InputStream in = open("Inter-" + weight + ".ttf")) {
                if (in != null) {
                    Font font = Font.createFont(Font.TRUETYPE_FONT, in);
                    GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                    FONTS.put(weight, font);
                }
            } catch (Exception ignored) {
                // the system font below is used instead
            }
        }
    }

    private static InputStream open(String name) throws IOException {
        InputStream in = Theme.class.getResourceAsStream("/fonts/" + name);
        if (in != null) {
            return in;
        }
        Path file = Paths.get("fonts", name);
        return Files.isReadable(file) ? Files.newInputStream(file) : null;
    }

    private static String fallbackFamily() {
        if (fallback == null) {
            fallback = Font.SANS_SERIF;
            java.util.Set<String> families = new java.util.HashSet<>(java.util.Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String family : new String[] { "Segoe UI", "SF Pro Text", "Helvetica Neue", "Arial" }) {
                if (families.contains(family)) {
                    fallback = family;
                    break;
                }
            }
        }
        return fallback;
    }

    static Font font(String weight, float size) {
        init();
        Font base = FONTS.get(weight);
        if (base != null) {
            return base.deriveFont(size);
        }
        boolean bold = weight.equals("Bold") || weight.equals("SemiBold");
        return new Font(fallbackFamily(), bold ? Font.BOLD : Font.PLAIN, Math.round(size));
    }

    static Font regular(float size) {
        return font("Regular", size);
    }

    static Font medium(float size) {
        return font("Medium", size);
    }

    static Font semibold(float size) {
        return font("SemiBold", size);
    }

    static Font bold(float size) {
        return font("Bold", size);
    }
}
