package LIB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Evidence-based planning aid; it does not multiply demand by a fixed severity factor. */
public final class AiSimulator {
    public static final class HistoricalBaseline {
        public final int eventCount;
        public final double averageHouseholds;
        public final double averagePeople;

        public HistoricalBaseline(int eventCount, double averageHouseholds, double averagePeople) {
            this.eventCount = Math.max(0, eventCount);
            this.averageHouseholds = Math.max(0, averageHouseholds);
            this.averagePeople = Math.max(0, averagePeople);
        }
    }

    public static final class Result {
        public final String disasterType;
        public final String severity;
        public final int days;
        public final int recordedHouseholds;
        public final int recordedPeople;
        public final int centerCapacity;
        public final HistoricalBaseline history;
        public final List<String> lines = new ArrayList<>();

        Result(String disasterType, String severity, int days, int recordedHouseholds, int recordedPeople,
                int centerCapacity, HistoricalBaseline history) {
            this.disasterType = disasterType;
            this.severity = severity;
            this.days = days;
            this.recordedHouseholds = recordedHouseholds;
            this.recordedPeople = recordedPeople;
            this.centerCapacity = centerCapacity;
            this.history = history;
        }

        public String report() {
            StringBuilder out = new StringBuilder();
            out.append("PLANNING SCENARIO (not a live forecast).\n")
                    .append("Scenario: ").append(disasterType).append(" · severity=").append(severity)
                    .append(" · planning horizon=").append(days).append(" day(s)\n")
                    .append("Current recorded active households=").append(recordedHouseholds)
                    .append("; recorded individuals=").append(recordedPeople).append('\n')
                    .append("Listed evacuation-centre capacity (beds/people, not occupancy)=")
                    .append(centerCapacity).append('\n');
            if (history.eventCount == 0) {
                out.append("Historical baseline: no completed matching events were found in the last five years; "
                        + "no numeric forecast is produced.\n");
            } else {
                out.append("Historical evidence: ").append(history.eventCount)
                        .append(" completed event(s) with matching type and severity in the last five years; "
                                + "average registered households=")
                        .append(round(history.averageHouseholds)).append(", average registered individuals=")
                        .append(round(history.averagePeople)).append(". This is a historical planning baseline, "
                                + "not a calibrated future prediction.\n");
            }
            out.append("Severity is reported as an event attribute; no fixed multiplier is applied. "
                    + "Occupancy, transport, and official ration standards are not recorded.\n\n");
            if (lines.isEmpty()) {
                out.append("No recorded household-need categories are available.\n");
            } else {
                out.append("RECORDED RESOURCE NEEDS (not a stock-shortage calculation):\n")
                        .append(String.join("\n", lines)).append('\n');
                out.append("Household need records do not specify units. Their quantities are not compared with "
                        + "inventory quantities; shortages and days-of-cover are intentionally not calculated.\n");
            }
            return out.toString();
        }
    }

    private AiSimulator() {
    }

    public static Result run(String disasterType, String severity, Integer days, int households, int people,
            int centerCapacity, List<NeedStock> needs, HistoricalBaseline history) {
        String type = blank(disasterType) ? "Unspecified hazard" : disasterType.trim();
        String level = normalize(severity);
        int horizon = days == null || days <= 0 ? 5 : Math.min(days, 60);
        Result result = new Result(type, level, horizon, households, people, centerCapacity,
                history == null ? new HistoricalBaseline(0, 0, 0) : history);
        if (needs != null) {
            for (NeedStock need : needs) {
                result.lines.add("- category=" + need.category + " | recorded requested=" + need.requested
                        + " (unit not recorded; no inventory comparison)");
            }
        }
        return result;
    }

    public static String normalize(String severity) {
        if (severity == null) {
            return "MODERATE";
        }
        String value = severity.trim().toUpperCase(Locale.ENGLISH);
        return switch (value) {
            case "LOW", "MILD" -> "LOW";
            case "HIGH" -> "HIGH";
            case "CRITICAL", "SEVERE" -> "CRITICAL";
            default -> "MODERATE";
        };
    }

    public static final class NeedStock {
        public final String category;
        public final int requested;

        public NeedStock(String category, int requested) {
            this.category = category == null ? "Unspecified" : category;
            this.requested = Math.max(0, requested);
        }
    }

    private static long round(double value) {
        return Math.round(value);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
