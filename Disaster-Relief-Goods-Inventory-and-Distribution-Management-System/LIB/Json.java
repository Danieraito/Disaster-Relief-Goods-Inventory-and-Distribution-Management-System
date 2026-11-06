package LIB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON reader and writer so the app needs no third-party JSON library. */
public final class Json {
    private final String text;
    private int at;

    private Json(String text) {
        this.text = text == null ? "" : text;
    }

    public static Object parse(String text) {
        try {
            Json json = new Json(text);
            Object value = json.value();
            json.skipSpace();
            if (json.at != json.text.length()) {
                throw json.fail();
            }
            return value;
        } catch (RuntimeException malformed) {
            throw new IllegalArgumentException("Malformed JSON", malformed);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String text) {
        Object value = parse(text);
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
    }

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    public static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object value) {
        return value instanceof List<?> ? (List<Object>) value : List.of();
    }

    public static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static void write(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            out.append(quote(text));
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Number number) {
            double n = number.doubleValue();
            if (Double.isFinite(n) && n == Math.rint(n) && Math.abs(n) < 1e15) {
                out.append((long) n);
            } else {
                out.append(number);
            }
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(quote(String.valueOf(entry.getKey()))).append(':');
                write(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(out, list.get(i));
            }
            out.append(']');
        } else {
            out.append(quote(String.valueOf(value)));
        }
    }

    private Object value() {
        skipSpace();
        return switch (peek()) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        at++;
        skipSpace();
        if (peek() == '}') {
            at++;
            return map;
        }
        while (true) {
            skipSpace();
            String key = string();
            skipSpace();
            expect(':');
            map.put(key, value());
            skipSpace();
            char separator = next();
            if (separator == '}') {
                return map;
            }
            if (separator != ',') {
                throw fail();
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        at++;
        skipSpace();
        if (peek() == ']') {
            at++;
            return list;
        }
        while (true) {
            list.add(value());
            skipSpace();
            char separator = next();
            if (separator == ']') {
                return list;
            }
            if (separator != ',') {
                throw fail();
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char escaped = next();
            switch (escaped) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case '"', '\\', '/' -> out.append(escaped);
                case 'u' -> {
                    if (at + 4 > text.length()) {
                        throw fail();
                    }
                    out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    at += 4;
                }
                default -> throw fail();
            }
        }
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, at)) {
            throw fail();
        }
        at += word.length();
        return value;
    }

    private Object number() {
        int start = at;
        while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) {
            at++;
        }
        if (start == at) {
            throw fail();
        }
        return Double.valueOf(text.substring(start, at));
    }

    private void expect(char wanted) {
        if (next() != wanted) {
            throw fail();
        }
    }

    private char peek() {
        if (at >= text.length()) {
            throw fail();
        }
        return text.charAt(at);
    }

    private char next() {
        char c = peek();
        at++;
        return c;
    }

    private void skipSpace() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
    }

    private IllegalArgumentException fail() {
        return new IllegalArgumentException("Unexpected JSON at position " + at);
    }
}
