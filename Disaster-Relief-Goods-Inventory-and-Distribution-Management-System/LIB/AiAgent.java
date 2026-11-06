package LIB;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Disaster-relief agent: pulls live data through approved tools, reasons over it, plans, simulates,
 * remembers prior runs, and tracks follow-ups. It never writes stock or approvals.
 */
public final class AiAgent {
    public static final int MAX_TOOL_ROUNDS = 4;

    public enum Mode {
        ASSESS("Assess", "Situation assessment comparing possible actions"),
        MONITOR("Monitor", "Shortages, critical stock, and pending workflow"),
        PLAN("Plan", "Multi-step response plan"),
        SIMULATE("Simulate", "Disaster scenario and resource projection"),
        REPORT("Report", "Structured incident report and action list"),
        FOLLOW_UP("Follow-up", "Reassess unresolved recommendations"),
        ASK("Ask", "Question answered with live workspace data");

        public final String label;
        public final String hint;

        Mode(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }
    }

    public static final class Request {
        public Mode mode = Mode.ASSESS;
        public String question = "";
        public String disasterType = "";
        public String severity = "MODERATE";
        public Integer days;
        public List<String> followUpIds = List.of();
    }

    public static final class Result {
        public final Mode mode;
        public final String answer;
        public final List<String> toolsUsed;
        public final String runId;
        public final int followUpsOpened;
        public final List<AiChangeRepository.Proposal> proposals;

        Result(Mode mode, String answer, List<String> toolsUsed, String runId, int followUpsOpened,
                List<AiChangeRepository.Proposal> proposals) {
            this.mode = mode;
            this.answer = answer;
            this.toolsUsed = toolsUsed;
            this.runId = runId;
            this.followUpsOpened = followUpsOpened;
            this.proposals = proposals;
        }

    }

    private static final class Metrics {
        long providerMillis;
        long promptTokens;
        long completionTokens;

        void add(AiAssistant.Turn turn) {
            providerMillis += turn.elapsedMillis;
            promptTokens += turn.promptTokens;
            completionTokens += turn.completionTokens;
        }
    }

    private static final String RULES = "You are BarangayCare's disaster-relief operations agent. "
            + "Call approved tools for live data before concluding. Use only tool results and verified context. "
            + "Do not invent quantities, people, dates, occupancy, transport, or ration standards. "
            + "If a fact is missing, say it is unknown. Distinguish calculated facts from suggestions. "
            + "For factual claims, cite exact record IDs when relevant; the application displays data sources separately. "
            + "Never invent evidence references. State uncertainty and separate evidence from inference. "
            + "Conclude with a confidence statement that reflects data completeness, not a made-up probability. "
            + "Format answers with concise Markdown headings and bullets. Use standard pipe tables with one row per line "
            + "when a table is useful; never use HTML such as <br>. Prefer bullets for long explanations, and do not "
            + "print internal tool/function names in the answer. "
            + "Compare realistic alternative actions when recommending. Never claim to approve, release, "
            + "transfer, or modify stock. All actions require an authorized human. "
            + "Never retrieve, reveal, or infer personal household/member information. For explicitly requested household changes, "
            + "use only personal details the operator supplied and never invent missing values. "
            + "For inventory changes, use the supplied live inventory and batch records to identify the exact item and batch; "
            + "never guess an item ID, batch ID, or quantity. Creating a new item may include its explicitly supplied opening "
            + "quantity. Changes to an existing item's name, category, unit, threshold, or storage require an inventory_update "
            + "proposal with the verified current fields and a reason. Stock loss/correction requires an inventory_adjust "
            + "proposal with a valid batch, adjustment reason, evidence, and date. Do not use a correction to record newly "
            + "received goods; use the donation workflow and ask for missing donor/receipt details instead. "
            + "When the user explicitly requests a record change, call propose_change with exact values from the user's request. "
            + "A tool call only stages a proposal; it never changes the database. Only an authorized Barangay Official or Admin "
            + "may confirm and apply a proposal after reviewing its before/after values. Do not propose approval or release unless explicitly requested. "
            + "If required values are missing, ask for them instead of inventing them. "
            + "End with a section titled 'Follow-up' only when a specific action remains outstanding. "
            + "Write each task as one concise bullet in this format: [OPEN] <named item, record, or event>: "
            + "<verified issue>; <specific next action>; done when <clear completion check>. "
            + "Use names, quantities, dates, status, or record IDs from the live results when available. "
            + "Do not write generic tasks such as 'monitor stock', 'review the situation', or 'follow up'. "
            + "Do not create a task for a data lookup alone. If the evidence does not identify a specific issue "
            + "or action, leave it out and state what information is missing in the main answer. "
            + "Never list tool/function names or generic internal steps as follow-ups.";

    public Result run(Barangay barangay, UserAccount user, Request request) throws SQLException {
        if (barangay == null || user == null || !user.isActive() || !user.canAccessBarangay(barangay)) {
            throw new SecurityException("You cannot run the AI agent for this barangay workspace.");
        }
        Mode mode = request == null || request.mode == null ? Mode.ASSESS : request.mode;
        String question = request == null || request.question == null ? "" : request.question.trim();
        if (question.length() > AiAssistant.MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("Keep the question under %,d characters.", AiAssistant.MAX_QUESTION_LENGTH));
        }
        AiTools tools = new AiTools(barangay, user);
        AiAssistant.Config config = AiAssistant.loadConfig();
        Metrics metrics = new Metrics();
        long started = System.nanoTime();
        List<String> used = new ArrayList<>();
        Map<String, String> gathered = new LinkedHashMap<>();
        List<AiChangeRepository.Draft> proposed = new ArrayList<>();
        String answer;
        try {
            for (String name : AiTools.defaultTools(mode)) {
                String args = argumentsFor(name, request);
                gathered.put(name, tools.execute(new AiTools.Call(name, name, args)));
                used.add(name);
            }
            if (AiTools.isInventoryChangeRequest(question) && !gathered.containsKey("get_inventory")) {
                String inventory = tools.execute(new AiTools.Call("inventory-change-context", "get_inventory", "{\"filter\":\"all\"}"));
                gathered.put("get_inventory", inventory);
                used.add("get_inventory");
            }
            if (mode == Mode.FOLLOW_UP && !request.followUpIds.isEmpty()) {
                new AiMemoryRepository().markAssessed(barangay, user, request.followUpIds);
            }
            List<AiAssistant.Message> messages = new ArrayList<>();
            messages.add(AiAssistant.Message.system(RULES + "\n" + instructions(mode)));
            messages.add(AiAssistant.Message.user(task(mode, question, request) + "\n\nLIVE TOOL RESULTS:\n"
                    + formatGathered(gathered)));
            Map<String, String> evidence = new LinkedHashMap<>(gathered);
            answer = complete(messages, tools, used, request, proposed, barangay, user, evidence, metrics);
            answer = AiEvidence.attach(answer, evidence);
        } catch (SQLException | RuntimeException failure) {
            try {
                new AiMemoryRepository().saveRun(barangay, user, mode.name(), question,
                        "Agent request failed: " + failure.getMessage(), used, "FAILED", config.provider,
                        config.model, (System.nanoTime() - started) / 1_000_000L, metrics.providerMillis,
                        metrics.promptTokens,
                        metrics.completionTokens);
            } catch (SQLException trackingFailure) {
                failure.addSuppressed(trackingFailure);
            }
            throw failure;
        }
        int openBefore = new AiMemoryRepository().followUps(barangay.getBarangayId(), true).size();
        String runId = new AiMemoryRepository().saveRun(barangay, user, mode.name(), question, answer, used,
                "SUCCEEDED", config.provider, config.model, (System.nanoTime() - started) / 1_000_000L,
                metrics.providerMillis, metrics.promptTokens, metrics.completionTokens);
        List<AiChangeRepository.Proposal> proposals = new AiChangeRepository().create(barangay, user, runId, proposed);
        int openAfter = new AiMemoryRepository().followUps(barangay.getBarangayId(), true).size();
        return new Result(mode, answer, used, runId, Math.max(0, openAfter - openBefore), proposals);
    }

    private String complete(List<AiAssistant.Message> messages, AiTools tools, List<String> used, Request request,
            List<AiChangeRepository.Draft> proposed, Barangay barangay, UserAccount user,
            Map<String, String> evidence, Metrics metrics) throws SQLException {
        List<Map<String, Object>> catalog = AiTools.openaiTools(
                request == null ? Mode.ASSESS : request.mode,
                request == null ? "" : request.question);
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            AiAssistant.Turn turn;
            try {
                turn = AiAssistant.chat(messages, catalog);
                metrics.add(turn);
            } catch (IllegalStateException failed) {
                if (round == 0 && AiAssistant.isToolCapabilityError(failed.getMessage())) {
                    turn = AiAssistant.chat(messages, List.of());
                    metrics.add(turn);
                } else {
                    throw failed;
                }
            }
            if (!turn.wantsTools()) {
                if (turn.content.isBlank()) {
                    throw new IllegalStateException("The model returned an empty response.");
                }
                return turn.content;
            }
            messages.add(AiAssistant.Message.assistant(turn.content, turn.toolCalls));
            for (AiAssistant.ToolCall call : turn.toolCalls) {
                String args = call.arguments;
                if ("simulate_scenario".equals(call.name) && (args == null || args.equals("{}")) && request != null) {
                    args = argumentsFor(call.name, request);
                }
                String result;
                if ("propose_change".equals(call.name)) {
                    try {
                        Map<String, Object> arguments = Json.asObject(Json.parse(args));
                        proposed.add(AiChangeService.prepare(request == null ? "" : request.question,
                                arguments, barangay, user));
                        result = "Proposal staged for human review. No application record was changed.";
                    } catch (IllegalArgumentException | IllegalStateException invalidProposal) {
                        result = "Proposal not staged: " + invalidProposal.getMessage()
                                + ". Ask the operator for missing or corrected information.";
                    }
                } else {
                    try {
                        result = tools.execute(new AiTools.Call(call.id, call.name, args));
                    } catch (IllegalArgumentException | IllegalStateException invalidToolCall) {
                        result = "Tool call was not completed: " + invalidToolCall.getMessage()
                                + ". Correct the arguments or explain that the requested data is unavailable.";
                    }
                }
                if (!"propose_change".equals(call.name)) {
                    evidence.merge(call.name, result, (previous, next) -> previous + "\n" + next);
                }
                used.add(call.name);
                messages.add(AiAssistant.Message.tool(call, result));
            }
        }
        messages.add(AiAssistant.Message.user(
                "The tool-call limit was reached. Give the best answer using verified data already gathered. "
                        + "State clearly which requested details could not be retrieved."));
        AiAssistant.Turn last = AiAssistant.chat(messages, List.of());
        metrics.add(last);
        if (last.content.isBlank()) {
            throw new IllegalStateException("The model returned an empty response.");
        }
        return last.content;
    }

    private static String argumentsFor(String name, Request request) {
        if (!"simulate_scenario".equals(name) || request == null) {
            return "{}";
        }
        Map<String, Object> args = new LinkedHashMap<>();
        if (request.disasterType != null && !request.disasterType.isBlank()) {
            args.put("disaster_type", request.disasterType.trim());
        }
        if (request.severity != null && !request.severity.isBlank()) {
            args.put("severity", request.severity.trim());
        }
        if (request.days != null && request.days > 0) {
            args.put("days", request.days);
        }
        return Json.stringify(args);
    }

    private static String formatGathered(Map<String, String> gathered) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : gathered.entrySet()) {
            out.append("### ").append(entry.getKey()).append("\n").append(entry.getValue()).append("\n");
        }
        return out.toString();
    }

    private static String task(Mode mode, String question, Request request) {
        String extra = question.isEmpty() ? "" : "\nOperator note: " + question;
        return switch (mode) {
            case MONITOR -> "Detect shortages, critical/expired stock, and pending donation or distribution issues. "
                    + "Rank what needs attention now." + extra;
            case PLAN -> "Create a multi-step response plan with ordered actions, who (by role) should do each step, "
                    + "dependencies, and what cannot be known yet." + extra;
            case SIMULATE -> "Interpret the simulation engine output. Predict resource requirements, compare holding "
                    + "stock vs. requesting replenishment vs. prioritizing expiring batches, and list unknowns. "
                    + "Scenario severity=" + (request == null ? "MODERATE" : request.severity) + extra;
            case REPORT -> "Produce a structured incident report: situation, verified facts, risks, recommended "
                    + "actions, and an action list. Label every item as fact or recommendation." + extra;
            case FOLLOW_UP -> "Reassess each open follow-up against live data. For each task, state whether it is "
                    + "still needed, done, or blocked, name the exact item/record and verified issue, and state "
                    + "the next action or completion evidence. Do not retain a task if no specific action remains."
                    + extra;
            case ASK -> question.isEmpty()
                    ? "Answer using live workspace data. If the request is unclear, give a short situation briefing."
                    : question;
            default -> "Generate a situation assessment. Analyse the current response and compare possible next "
                    + "actions with trade-offs." + extra;
        };
    }

    private static String instructions(Mode mode) {
        return switch (mode) {
            case MONITOR -> "Write short sections: Critical now, Pending workflow, Watch list.";
            case PLAN -> "Write numbered steps. Each step: action, role, data it depends on, and a success check.";
            case SIMULATE -> "Do not invent extra quantities. Quote the simulation numbers and explain uncertainty.";
            case REPORT -> "Use headings: Incident summary, Verified facts, Risks, Action list.";
            case FOLLOW_UP -> "Use one short subsection per existing task. Include its status, the specific evidence "
                    + "checked, and the next action or why it is done/blocked. In the final Follow-up section, list "
                    + "only still-needed tasks using the required item, issue, action, and completion-check format.";
            default -> "Include compared actions and a Follow-up list for unfinished checks.";
        };
    }
}
