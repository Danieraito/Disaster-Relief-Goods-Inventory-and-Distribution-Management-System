package LIB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adds deterministic source references and an evidence-coverage indicator to agent output. */
public final class AiEvidence {
    private static final Pattern ID = Pattern.compile("\\bid=([A-Za-z0-9._:-]+)");

    private AiEvidence() {
    }

    public static String attach(String answer, Map<String, String> evidence) {
        List<String> sources = new ArrayList<>();
        boolean incomplete = false;
        if (evidence != null) {
            for (Map.Entry<String, String> entry : evidence.entrySet()) {
                if ("propose_change".equals(entry.getKey())) {
                    continue;
                }
                String value = entry.getValue() == null ? "" : entry.getValue();
                Matcher matcher = ID.matcher(value);
                List<String> ids = new ArrayList<>();
                while (matcher.find() && ids.size() < 5) {
                    ids.add(matcher.group(1));
                }
                sources.add("- " + entry.getKey()
                        + (ids.isEmpty() ? "" : " · record IDs: " + String.join(", ", ids)));
                String lower = value.toLowerCase();
                incomplete |= lower.contains("not recorded") || lower.contains("unknown")
                        || lower.contains("not specified") || lower.contains("none recorded");
            }
        }
        String coverage = sources.size() >= 3 && !incomplete ? "High"
                : sources.size() >= 2 ? "Moderate" : "Low";
        StringBuilder out = new StringBuilder(answer == null ? "" : answer.trim());
        out.append("\n\n## Evidence and confidence\n")
                .append("Evidence coverage: ").append(coverage)
                .append(" (source coverage indicator only; not a calibrated probability or forecast).\n")
                .append("### Application sources\n");
        if (sources.isEmpty()) {
            out.append("- No live application sources were available.\n");
        } else {
            out.append(String.join("\n", sources)).append('\n');
        }
        out.append("Confidence is lower when source data is missing or not recorded; verify evidence before acting.");
        return out.toString();
    }
}
