package GUI;

import GUI.Theme.Tone;
import LIB.AiAgent;
import LIB.AiAssistant;
import LIB.AiChangeRepository;
import LIB.AiChangeService;
import LIB.AiContextService;
import LIB.AiFeedbackRepository;
import LIB.AiMemoryRepository;
import LIB.Inventory;
import LIB.InventoryRepository;
import LIB.Time;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JComponent;
import javax.swing.JTextArea;

/** Live AI agent workspace: monitor, plan, simulate, remember, and follow up. Advisory only. */
class AiCenterPage extends Page {
    private static final Pattern LIST_ITEM = Pattern.compile("^([-*•]|\\d{1,2}[.)])\\s+(.*)$");
    private static final Pattern SUB_HEADING = Pattern.compile("^\\*\\*(.+?)\\*\\*:?$");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile(":?-{3,}:?");
    private static final List<String> TABS = Arrays.asList("Monitor", "Agent", "Follow-up", "Changes", "Memory");

    private int tab;
    private AiContextService.Snapshot snapshot;
    private List<StockAlerts.Notice> notices = new ArrayList<>();
    private List<AiMemoryRepository.FollowUp> followUps = Collections.emptyList();
    private List<AiMemoryRepository.Run> runs = Collections.emptyList();
    private List<AiChangeRepository.Proposal> changes = Collections.emptyList();
    private List<AiFeedbackRepository.Entry> history = Collections.emptyList();
    private AiFeedbackRepository.Summary feedbackSummary;
    private String provider = "";
    private AiAgent.Mode mode = AiAgent.Mode.ASSESS;
    private AiAgent.Result result;
    private AiChangeRepository.Proposal selectedChange;
    private String viewedAnswer;
    private String viewedMeta;
    private String problem;
    private String status;
    private boolean busy;
    private boolean feedbackSaved;

    private final GrowingArea questionBox = new GrowingArea(3);
    private final Field questionField = new Field("Question or operator note (optional)", questionBox,
            "Leave blank to run the selected job against live workspace data. Up to "
                    + String.format("%,d", AiAssistant.MAX_QUESTION_LENGTH) + " characters.");
    private final Dropdown<String> severityBox = Dropdown.of("LOW", "MODERATE", "HIGH", "CRITICAL");
    private final Field severityField = new Field("Scenario severity", severityBox);
    private final Input daysBox = new Input("5");
    private final Field daysField = new Field("Planning days", daysBox);
    private final Dropdown<String> decisionBox = Dropdown.of("Accepted", "Rejected", "Needs revision");
    private final Field decisionField = new Field("Decision", decisionBox);
    private final GrowingArea commentBox = new GrowingArea(2);
    private final Field commentField = new Field("Comment (optional)", commentBox);

    @Override String module() { return "AI Agent"; }
    @Override String crumb() { return tab == 0 ? "AI Agent / Monitor" : tab == 1 ? "AI Agent / Run"
            : tab == 2 ? "AI Agent / Follow-up" : tab == 3 ? "AI Agent / Changes" : "AI Agent / Memory"; }

    @Override
    void load() throws Exception {
        snapshot = new AiContextService().load(Session.workspace, Session.user);
        List<Inventory> items = new InventoryRepository().findByBarangay(Session.workspace.getBarangayId());
        notices = StockAlerts.of(items);
        followUps = new AiMemoryRepository().followUps(Session.workspace.getBarangayId(), false);
        runs = new AiMemoryRepository().recentRuns(Session.workspace.getBarangayId());
        changes = new AiChangeRepository().recent(Session.workspace, Session.user);
        history = new AiFeedbackRepository().recent(Session.workspace.getBarangayId());
        feedbackSummary = new AiFeedbackRepository().summary(Session.workspace.getBarangayId());
        AiAssistant.Config config = AiAssistant.loadConfig();
        provider = config.provider + " · " + config.model;
        problem = null;
        if (viewedAnswer == null) {
            viewedAnswer = systemBrief();
            viewedMeta = "Live system brief · no model yet";
        }
    }

    @Override
    JComponent build() {
        Row actions = new Row(8).right();
        if (Session.isAdmin()) {
            Btn settings = UI.secondary("Provider settings", this::settings);
            settings.setEnabled(!busy);
            actions.put(settings);
        }
        Btn run = UI.primary(busy ? "Running…" : runLabel(), this::runAgent);
        run.setEnabled(!busy);
        actions.put(run);

        VBox page = new VBox(18);
        page.put(UI.pageHeader("AI Agent",
                "Live, read-only decision support for this barangay. The agent calls approved tools; people still approve every action.",
                actions));
        if (problem != null) {
            page.put(UI.alert(Tone.DANGER, "Agent request failed", problem));
        }
        page.put(statRow());
        page.put(UI.tabs(TABS, tab, index -> {
            tab = index;
            rebuild();
        }));
        if (tab == 0) {
            page.put(monitorBody());
        } else if (tab == 1) {
            page.put(agentBody());
        } else if (tab == 2) {
            page.put(followUpBody());
        } else if (tab == 3) {
            page.put(changesBody());
        } else {
            page.put(memoryBody());
        }
        return page;
    }

    private String runLabel() {
        return switch (tab) {
            case 2 -> "Reassess open follow-ups";
            case 0 -> "Review with agent";
            case 3, 4 -> "Run " + mode.label.toLowerCase();
            default -> "Run " + mode.label.toLowerCase();
        };
    }

    private JComponent statRow() {
        int open = 0;
        for (AiMemoryRepository.FollowUp item : followUps) {
            if ("OPEN".equals(item.status)) {
                open++;
            }
        }
        return new Cols(18, 1, 1, 1, 1).stretch().put(
                UI.stat("Attention", Fmt.n(notices.size()),
                        StockAlerts.count(notices, "LOW") + " low · "
                                + StockAlerts.count(notices, "EXPIRED") + " expired · "
                                + StockAlerts.count(notices, "EXPIRING") + " expiring",
                        notices.isEmpty() ? Tone.TEAL : Tone.WARN),
                UI.stat("Pending workflow", Fmt.n(snapshot.pendingDonations + snapshot.pendingDistributions),
                        snapshot.pendingDonations + " donations · " + snapshot.pendingDistributions + " distributions",
                        snapshot.pendingDonations + snapshot.pendingDistributions > 0 ? Tone.WARN : Tone.TEAL),
                UI.stat("Open follow-ups", Fmt.n(open), open == 0 ? "Nothing waiting" : "Agent will reassess these",
                        open > 0 ? Tone.INFO : Tone.NEUTRAL),
                UI.stat("Active disasters", Fmt.n(snapshot.activeDisasters),
                        Fmt.n(snapshot.activeHouseholds) + " households · " + Fmt.n(snapshot.vulnerablePeople)
                                + " vulnerable people", Tone.NEUTRAL));
    }

    private JComponent monitorBody() {
        Card live = new Card(14);
        live.put(UI.h2("Proactive monitor"),
                UI.muted("Calculated from inventory and workflow records. The model is not required for this list."));
        if (notices.isEmpty() && snapshot.pendingDonations == 0 && snapshot.pendingDistributions == 0) {
            live.put(UI.alert(Tone.TEAL, "No shortages or pending issues",
                    "Usable stock is above thresholds, nothing has expired, and no donations or distributions are waiting."));
        }
        for (StockAlerts.Notice notice : notices) {
            live.put(UI.alert(notice.tone, notice.title, notice.message));
        }
        if (snapshot.pendingDonations > 0) {
            live.put(UI.alert(Tone.WARN, snapshot.pendingDonations + " pending donation"
                    + (snapshot.pendingDonations == 1 ? "" : "s"),
                    "Do not count these goods as usable stock until they are approved and received."));
        }
        if (snapshot.pendingDistributions > 0) {
            live.put(UI.alert(Tone.WARN, snapshot.pendingDistributions + " pending distribution"
                    + (snapshot.pendingDistributions == 1 ? "" : "s"),
                    "Do not treat these as completed releases until an authorized officer posts them."));
        }
        Card facts = new Card(14);
        facts.put(UI.h2("Workspace facts"), UI.small("Verified counts · " + Time.date(Time.today())));
        facts.put(UI.fact("Inventory items", Fmt.n(snapshot.itemCount)),
                UI.fact("Households affected", Fmt.n(snapshot.activeHouseholds)),
                UI.fact("Individuals recorded", Fmt.n(snapshot.affectedIndividuals)),
                UI.fact("Vulnerable individuals", Fmt.n(snapshot.vulnerablePeople)));
        return new Cols(22, 3, 2).stretch().put(live, facts);
    }

    private JComponent agentBody() {
        VBox column = new VBox(16);
        column.put(jobCard(), outputCard(), reviewCard());
        return column;
    }

    private JComponent jobCard() {
        Card card = new Card(14);
        VBox titles = new VBox(4);
        titles.put(UI.h2("Run the agent"),
                UI.muted(mode.hint + " · Provider: " + provider));
        card.put(titles);
        Row jobs = new Row(8);
        for (AiAgent.Mode option : AiAgent.Mode.values()) {
            Btn button = option == mode ? UI.primary(option.label) : UI.secondary(option.label);
            button.setEnabled(!busy);
            AiAgent.Mode chosen = option;
            button.addActionListener(event -> {
                mode = chosen;
                rebuild();
            });
            jobs.put(button);
        }
        card.put(jobs);
        card.put(questionField);
        if (mode == AiAgent.Mode.SIMULATE) {
            card.put(new Cols(14, 1, 1).stretch().put(severityField, daysField));
        }
        card.put(UI.smallPara("The agent first reads live stock, disasters, and pending work through approved functions, "
                + "then writes an assessment. If asked to make a change, it creates a proposal for review; it never "
                + "writes a record without confirmation. Requests detected as containing household or contact "
                + "details are blocked from Groq; use Ollama for those requests. Detection is conservative, not a "
                + "guarantee that all personal information will be identified."));
        card.put(UI.smallPara("Inventory examples: “Add a new rice item with 20 kg in storage A”; "
                + "“Set item RICE-01's low-stock threshold to 10”; or “Record 3 expired boxes from batch B-12.” "
                + "For new goods arriving for an existing item, ask the agent to record a donation and provide the donor "
                + "and delivery details. The agent stages a before/after proposal; a Barangay Official or Admin must "
                + "review it and explicitly confirm before inventory changes."));
        return card;
    }

    private JComponent outputCard() {
        Card card = new Card(14);
        VBox titles = new VBox(4);
        titles.put(UI.h2("Agent output"), UI.muted(status != null ? status : viewedMeta));
        Row pills = new Row(6);
        if (result != null) {
            pills.put(UI.pill(result.mode.label.toUpperCase(), Tone.INFO));
            pills.put(UI.pill("ADVISORY", Tone.TEAL));
        } else {
            pills.put(UI.pill("SYSTEM BRIEF", Tone.TEAL));
        }
        card.put(UI.titleRow(titles, pills));
        if (result != null && !result.toolsUsed.isEmpty()) {
            Row tools = new Row(6);
            for (String name : result.toolsUsed.stream().distinct().toList()) {
                tools.put(UI.pill(toolLabel(name), Tone.NEUTRAL));
            }
            card.put(UI.small("Data checked"), tools);
        }
        Card panel = new Card(10);
        panel.pad(16, 20, 18, 20);
        panel.colors(Theme.HEADER_BG, Theme.BORDER);
        if (busy) {
            panel.put(UI.muted("Calling approved functions, then asking the model. A local model can take a minute."));
        } else {
            panel.put(render(viewedAnswer));
        }
        card.put(panel);
        if (result != null && result.followUpsOpened > 0) {
            card.put(UI.alert(Tone.INFO, result.followUpsOpened + " follow-up"
                    + (result.followUpsOpened == 1 ? "" : "s") + " opened",
                    "Unresolved checks were stored on the Follow-up tab."));
        }
        if (result != null && !result.proposals.isEmpty()) {
            card.put(UI.alert(Tone.WARN, result.proposals.size() + " change proposal"
                    + (result.proposals.size() == 1 ? "" : "s") + " waiting for review",
                    "No proposed change has been applied. Open the Changes tab to inspect each before/after preview."));
            card.put(UI.rightActions(UI.secondary("Review proposals", () -> {
                tab = 3;
                rebuild();
            })));
        }
        return card;
    }

    private JComponent changesBody() {
        VBox column = new VBox(16);
        DataTable table = new DataTable(new String[] { "Status", "Proposed change", "Requested by", "Created", "Record" },
                0.9, 2.4, 1.2, 1.4, 1.2);
        for (AiChangeRepository.Proposal proposal : changes) {
            table.rowClick(() -> {
                selectedChange = proposal;
                rebuild();
            }, new String[] { proposal.status }, new String[] { proposal.summary,
                AiChangeService.actionLabel(proposal.action) }, proposal.proposer, proposal.createdAt,
                    proposal.recordReference == null || proposal.recordReference.isBlank() ? "—" : proposal.recordReference);
        }
        if (changes.isEmpty()) {
            table.empty("No AI changes proposed yet. Ask the agent to make a specific update from the Agent tab.");
        }
        column.put(UI.section("AI change log",
                "Proposals are never applied automatically. A Barangay Official or Admin must review and confirm.",
                table));
        if (selectedChange != null) {
            column.put(changeDetail(selectedChange));
        }
        return column;
    }

    private JComponent changeDetail(AiChangeRepository.Proposal proposal) {
        Card card = new Card(14);
        card.put(UI.h2("Review proposal"),
                UI.muted("Status: " + proposal.status + " · Proposed by " + proposal.proposer + " · " + proposal.createdAt),
                UI.muted("Reviewed by: " + (proposal.reviewer == null ? "—" : proposal.reviewer)
                        + (proposal.reviewedAt == null || proposal.reviewedAt.isBlank()
                                ? "" : " · " + proposal.reviewedAt)),
                UI.fact("Requested change", proposal.summary),
                UI.smallPara("Original user request:\n" + proposal.request),
                UI.smallPara("Before:\n" + emptyAsNone(proposal.before)),
                UI.smallPara("Proposed after:\n" + emptyAsNone(proposal.after)));
        if (proposal.outcome != null && !proposal.outcome.isBlank()) {
            card.put(UI.smallPara("Review result:\n" + proposal.outcome));
        }
        if (proposal.recordReference != null && !proposal.recordReference.isBlank()) {
            card.put(UI.smallPara("Created or updated record reference: " + proposal.recordReference));
        }
        if (AiChangeRepository.PENDING.equals(proposal.status)) {
            if (Session.canManage()) {
                Row actions = new Row(8).right();
                actions.put(UI.secondary("Reject proposal", () -> rejectChange(proposal)));
                actions.put(UI.primary(AiChangeService.highImpact(proposal)
                        ? "Confirm operational change" : "Confirm & apply", () -> applyChange(proposal)));
                card.put(actions);
            } else {
                card.put(UI.alert(Tone.INFO, "Manager review required",
                        "A Barangay Official or Admin must confirm or reject this proposal."));
            }
        }
        return card;
    }

    private static String emptyAsNone(String value) {
        return value == null || value.isBlank() || "{}".equals(value) ? "(new record)" : value;
    }

    private void applyChange(AiChangeRepository.Proposal proposal) {
        String warning = AiChangeService.highImpact(proposal)
                ? "This may change stock, approve/reject a transaction, close a disaster, or release goods. "
                        + "The change will be written through the existing validated workflow and logged under your account."
                : "This will apply the proposed change through the existing validated workflow and log it under your account.";
        if (!Dialogs.confirm(shell, "Confirm AI-proposed change",
                warning + "\n\nReview the before/after values on this page before continuing.",
                "Confirm & apply", AiChangeService.highImpact(proposal))) {
            return;
        }
        Task.run(() -> new AiChangeService(Session.workspace, Session.user).review(proposal.id), reference -> {
            selectedChange = null;
            Dialogs.message(shell, "Change applied", "The change was applied and logged."
                    + (reference == null || reference.isBlank() ? "" : "\nRecord reference: " + reference),
                    Tone.TEAL);
            reloadChanges();
        }, error -> {
            Dialogs.error(shell, error);
            reloadChanges();
        });
    }

    private void rejectChange(AiChangeRepository.Proposal proposal) {
        if (!Dialogs.confirm(shell, "Reject AI proposal",
                "Reject this proposal? No application record will be changed. The decision will be logged.",
                "Reject", false)) {
            return;
        }
        Task.run(() -> {
            new AiChangeService(Session.workspace, Session.user).reject(proposal.id, "Rejected by reviewer.");
            return null;
        }, ignored -> {
            selectedChange = null;
            reloadChanges();
        }, error -> {
            Dialogs.error(shell, error);
            reloadChanges();
        });
    }

    private void reloadChanges() {
        Task.run(() -> new AiChangeRepository().recent(Session.workspace, Session.user), rows -> {
            changes = rows;
            rebuild();
        }, error -> {
            problem = Task.message(error);
            rebuild();
        });
    }

    private JComponent reviewCard() {
        Card card = new Card(14);
        card.put(UI.h2("Administrator review"),
                UI.mutedPara("Feedback is stored with this run. It does not change inventory or release goods."));
        if (feedbackSaved) {
            card.put(UI.alert(Tone.TEAL, "Feedback recorded", "The review is in Memory."));
        }
        if (!Session.canManage()) {
            card.put(UI.alert(Tone.INFO, "Read-only review",
                    "Only a Barangay Official or Admin can submit recommendation feedback."));
        } else if (result == null) {
            card.put(UI.muted("Run the agent before recording administrator feedback."));
        } else {
            Cols fields = new Cols(14, 1, 2).stretch();
            fields.put(decisionField, commentField);
            Btn save = UI.primary("Save feedback", this::saveFeedback);
            save.setEnabled(!busy);
            card.put(fields, UI.rightActions(save));
        }
        return card;
    }

    private JComponent followUpBody() {
        DataTable table = new DataTable(new String[] { "Status", "Follow-up action", "Last assessed", "Recorded", "" },
                0.9, 2.6, 1.1, 1.1, 1.2);
        for (AiMemoryRepository.FollowUp item : followUps) {
            Tone tone = "OPEN".equals(item.status) ? Tone.INFO
                    : "RESOLVED".equals(item.status) ? Tone.TEAL : Tone.NEUTRAL;
            Row actions = new Row(8);
            if ("OPEN".equals(item.status) && Session.canManage()) {
                String id = item.followUpId;
                actions.put(UI.link("Resolve", () -> setFollowUp(id, "RESOLVED")),
                        UI.link("Dismiss", () -> setFollowUp(id, "DISMISSED")));
            }
            table.row(UI.left(UI.pill(item.status, tone)),
                    new String[] { item.title, item.detail.equals(item.title) ? "" : item.detail },
                    item.lastAssessedAt.isEmpty() ? "Never" : item.lastAssessedAt, item.createdAt, actions);
        }
        if (followUps.isEmpty()) {
            table.empty("No follow-ups yet. When the agent lists unfinished checks, they appear here.");
        }
        return UI.section("Open follow-up actions",
                "Each action should identify the specific item or record, the verified issue, and what staff must do "
                        + "to complete it. Run Follow-up to check whether the action is still needed; resolve or dismiss "
                        + "it when no work remains.",
                table);
    }

    private JComponent memoryBody() {
        VBox column = new VBox(16);
        long elapsed = 0;
        long tokens = 0;
        int succeeded = 0;
        int failed = 0;
        for (AiMemoryRepository.Run run : runs) {
            elapsed += run.providerElapsedMillis;
            tokens += run.promptTokens + run.completionTokens;
            if ("FAILED".equals(run.status)) {
                failed++;
            } else {
                succeeded++;
            }
        }
        String average = runs.isEmpty() ? "n/a" : String.format("%,d ms", elapsed / runs.size());
        column.put(UI.section("Provider performance",
                "Recent " + runs.size() + " runs · " + succeeded + " succeeded · " + failed
                        + " failed · average total latency " + average + " · "
                        + String.format("%,d", tokens) + " prompt/completion tokens.",
                UI.mutedPara("Token counts are a usage proxy. Dollar cost is not estimated because provider pricing "
                        + "varies by account, model, and date; check the provider's usage dashboard.")));
        DataTable runTable = new DataTable(new String[] {
            "When", "Job / outcome", "Provider / model", "Provider / total ms", "Tokens", "Tools", "Request"
        }, 1.0, 0.8, 1.8, 0.8, 0.8, 1.2, 1.6);
        for (AiMemoryRepository.Run run : runs) {
            AiMemoryRepository.Run stored = run;
            runTable.rowClick(() -> {
                viewedAnswer = stored.answer;
                viewedMeta = stored.createdAt + " · " + stored.mode + " · remembered run";
                tab = 1;
                rebuild();
            }, run.createdAt, run.mode + " / " + run.status,
                    run.provider.isEmpty() ? "—" : run.provider + " / " + run.model,
                    run.providerElapsedMillis + " / " + run.elapsedMillis,
                    String.format("%,d", run.promptTokens + run.completionTokens),
                    run.toolsUsed.isEmpty() ? "—" : run.toolsUsed,
                    run.question.isBlank() ? "No operator note" : run.question);
        }
        if (runs.isEmpty()) {
            runTable.empty("No agent runs stored for this workspace yet.");
        }
        column.put(UI.section("Previous assessments", "Newest first. Open a row to read it on the Agent tab.", runTable));

        DataTable reviews = new DataTable(new String[] { "Decision", "Reviewer", "Recorded", "Run", "Comment" },
                1.0, 1.2, 1.3, 1.2, 2.2);
        for (AiFeedbackRepository.Entry entry : history) {
            String comment = entry.comment == null || entry.comment.isEmpty() ? "No comment provided." : entry.comment;
            reviews.row(UI.left(UI.pill(entry.decision, toneFor(entry.decision))), entry.reviewer, entry.createdAt,
                    entry.runId.isEmpty() ? "Legacy" : entry.runId.substring(0, Math.min(8, entry.runId.length())),
                    comment);
        }
        if (history.isEmpty()) {
            reviews.empty("No administrator feedback recorded yet.");
        }
        AiFeedbackRepository.Summary summary = feedbackSummary;
        if (summary != null) {
            column.put(UI.section("Feedback evaluation",
                    "Recorded decisions are linked to agent runs: " + summary.total() + " total · "
                            + summary.accepted + " accepted · " + summary.rejected + " rejected · "
                            + summary.needsRevision + " need revision. Counts describe reviewer feedback, "
                            + "not independently verified operational outcomes.",
                    UI.muted("Use the linked run and reviewer comments to assess whether recommendations were useful.")));
        }
        column.put(UI.section("Administrator decisions", "Human review of earlier advice.", reviews));
        return column;
    }

    private void runAgent() {
        if (busy) {
            return;
        }
        AiAgent.Request request = new AiAgent.Request();
        if (tab == 0) {
            request.mode = AiAgent.Mode.MONITOR;
        } else if (tab == 2) {
            request.mode = AiAgent.Mode.FOLLOW_UP;
            List<String> ids = new ArrayList<>();
            for (AiMemoryRepository.FollowUp item : followUps) {
                if ("OPEN".equals(item.status)) {
                    ids.add(item.followUpId);
                }
            }
            request.followUpIds = ids;
        } else {
            request.mode = mode;
        }
        request.question = questionBox.getText().trim();
        request.severity = severityBox.getSelected();
        request.disasterType = "";
        try {
            if (!daysBox.getText().isBlank()) {
                request.days = Integer.parseInt(daysBox.getText().trim());
            }
        } catch (NumberFormatException invalid) {
            problem = "Planning days must be a whole number.";
            rebuild();
            return;
        }
        if (request.question.length() > AiAssistant.MAX_QUESTION_LENGTH) {
            problem = "Keep the question under " + AiAssistant.MAX_QUESTION_LENGTH + " characters.";
            rebuild();
            return;
        }
        busy = true;
        problem = null;
        feedbackSaved = false;
        status = "Reading live workspace data…";
        tab = 1;
        rebuild();
        Task.run(() -> new AiAgent().run(Session.workspace, Session.user, request), done -> {
            busy = false;
            result = done;
            viewedAnswer = done.answer;
            viewedMeta = "Just now · " + done.mode.label + " · " + done.toolsUsed.stream().distinct().count()
                    + " tools";
            status = null;
            try {
                followUps = new AiMemoryRepository().followUps(Session.workspace.getBarangayId(), false);
                runs = new AiMemoryRepository().recentRuns(Session.workspace.getBarangayId());
                changes = new AiChangeRepository().recent(Session.workspace, Session.user);
            } catch (Exception refreshError) {
                problem = "Agent run succeeded, but its history could not be refreshed: "
                        + Task.message(refreshError);
            }
            redrawIfShowing();
        }, error -> {
            busy = false;
            status = null;
            problem = Task.message(error);
            redrawIfShowing();
        });
    }

    private void setFollowUp(String id, String statusValue) {
        Task.run(() -> {
            new AiMemoryRepository().updateStatus(Session.workspace, Session.user, id, statusValue);
            return new AiMemoryRepository().followUps(Session.workspace.getBarangayId(), false);
        }, rows -> {
            followUps = rows;
            redrawIfShowing();
        }, error -> Dialogs.error(shell, error));
    }

    private String systemBrief() {
        StringBuilder out = new StringBuilder();
        out.append("Live system brief (no model).\n\n");
        if (snapshot.lowStock > 0) {
            out.append("- Priority: review low-stock items against verified household needs.\n");
        }
        if (snapshot.expired > 0) {
            out.append("- Safety: keep expired batches out of release.\n");
        }
        if (snapshot.pendingDonations > 0) {
            out.append("- Workflow: pending donations are not usable stock yet.\n");
        }
        if (snapshot.pendingDistributions > 0) {
            out.append("- Workflow: pending distributions are not completed releases.\n");
        }
        if (out.toString().endsWith("brief (no model).\n\n")) {
            out.append("- No low-stock, expired-stock, or pending-workflow items in the current counts.\n");
        }
        out.append("\nRun Assess, Monitor, Plan, Simulate, or Report to have the agent call live tools and reason over them.");
        return out.toString();
    }

    private void saveFeedback() {
        if (result == null) {
            Dialogs.message(shell, "Agent run required", "Run the agent before submitting feedback.", Tone.WARN);
            return;
        }
        String recommendation = result.answer;
        String comment = commentBox.getText();
        String chosen = decisionBox.getSelected();
        Task.run(() -> {
            new AiFeedbackRepository().save(Session.workspace, Session.user, result.runId,
                    chosen, recommendation, comment);
            return new AiFeedbackRepository().recent(Session.workspace.getBarangayId());
        }, rows -> {
            history = rows;
            try {
                feedbackSummary = new AiFeedbackRepository().summary(Session.workspace.getBarangayId());
            } catch (Exception error) {
                problem = "Feedback was saved, but its evaluation summary could not be refreshed: "
                        + Task.message(error);
            }
            feedbackSaved = true;
            commentBox.setText("");
            redrawIfShowing();
        }, error -> Dialogs.error(shell, error));
    }

    private void redrawIfShowing() {
        if (shell != null && shell.currentPage() == this) {
            rebuild();
        }
    }

    private void settings() {
        AiAssistant.Config config = AiAssistant.loadConfig();
        Dropdown<String> providerBox = Dropdown.of(AiAssistant.PROVIDER_OLLAMA, AiAssistant.PROVIDER_GROQ);
        providerBox.setSelected(config.provider);
        Input model = new Input(config.model);
        Input endpoint = new Input(config.endpoint);
        providerBox.onChange(() -> useProviderDefaults(providerBox.getSelected(), model, endpoint));
        VBox form = new VBox(12).put(new Field("Provider", providerBox), new Field("Model name", model),
                new Field("Endpoint", endpoint),
                UI.mutedPara("Ollama: " + AiAssistant.OLLAMA_ENDPOINT + " (model " + AiAssistant.OLLAMA_MODEL
                        + "). Groq: " + AiAssistant.GROQ_ENDPOINT + " (model " + AiAssistant.GROQ_MODEL
                        + "). Set GROQ_API_KEY in your operating system environment. API keys are never stored by this screen."));
        boolean saved = Dialogs.form(shell, "AI provider settings",
                "Choose the model service used by the agent.", form, "Save settings", () -> {
                    try {
                        AiAssistant.saveConfig(providerBox.getSelected(), model.getText(), endpoint.getText());
                        return null;
                    } catch (IOException | IllegalArgumentException error) {
                        return error.getMessage();
                    }
                }, 640);
        if (saved) {
            reload();
            Dialogs.message(shell, "Settings saved", "AI provider settings were saved locally. No API key was stored.",
                    Tone.TEAL);
        }
    }

    private static void useProviderDefaults(String provider, Input model, Input endpoint) {
        if (AiAssistant.PROVIDER_GROQ.equals(provider)) {
            if (endpoint.getText().startsWith("http://localhost:11434")) {
                endpoint.setText(AiAssistant.GROQ_ENDPOINT);
            }
            if (AiAssistant.OLLAMA_MODEL.equals(model.getText()) || "llama3.2".equals(model.getText())) {
                model.setText(AiAssistant.GROQ_MODEL);
            }
        } else {
            if (endpoint.getText().startsWith("https://api.groq.com")) {
                endpoint.setText(AiAssistant.OLLAMA_ENDPOINT);
            }
            if (AiAssistant.GROQ_MODEL.equals(model.getText())) {
                model.setText(AiAssistant.OLLAMA_MODEL);
            }
        }
    }

    private static Tone toneFor(String decision) {
        if ("Accepted".equals(decision)) {
            return Tone.TEAL;
        }
        if ("Rejected".equals(decision)) {
            return Tone.DANGER;
        }
        return Tone.WARN;
    }

    static JComponent render(String text) {
        VBox box = new VBox(8);
        StringBuilder paragraph = new StringBuilder();
        String normalized = normalizeMarkup(text);
        String[] lines = normalized.split("\n", -1);
        for (int index = 0; index < lines.length;) {
            String raw = lines[index];
            String trimmed = raw.trim();
            String line = plain(trimmed);
            Matcher item = LIST_ITEM.matcher(line);
            Matcher sub = SUB_HEADING.matcher(trimmed);
            if (index + 1 < lines.length && isMarkdownTableStart(lines, index)) {
                flush(box, paragraph);
                DataTable table = renderTable(lines, index);
                box.put(table);
                index = tableEnd(lines, index);
            } else if (line.isEmpty()) {
                flush(box, paragraph);
                index++;
            } else if (trimmed.startsWith("#")) {
                flush(box, paragraph);
                box.put(UI.h3(line.replaceFirst("^#+\\s*", "")));
                index++;
            } else if (sub.matches()) {
                flush(box, paragraph);
                box.put(UI.h3(sub.group(1)));
                index++;
            } else if (item.matches()) {
                flush(box, paragraph);
                box.put(listItem(item.group(1), item.group(2)));
                index++;
            } else {
                if (paragraph.length() > 0) {
                    paragraph.append(' ');
                }
                paragraph.append(line);
                index++;
            }
        }
        flush(box, paragraph);
        return box;
    }

    private static DataTable renderTable(String[] lines, int start) {
        List<String> headers = tableCells(lines[start]);
        double[] weights = new double[headers.size()];
        Arrays.fill(weights, 1);
        DataTable table = new DataTable(headers.toArray(new String[0]), weights);
        for (int index = start + 2; index < lines.length; index++) {
            List<String> cells = tableCells(lines[index]);
            if (cells.size() != headers.size() || !lines[index].contains("|")) {
                break;
            }
            Object[] values = new Object[cells.size()];
            for (int cell = 0; cell < cells.size(); cell++) {
                values[cell] = new Wrap(plain(cells.get(cell)), Theme.regular(12.5f), Theme.TEXT).maxWidth(700);
            }
            table.row(values);
        }
        return table;
    }

    private static int tableEnd(String[] lines, int start) {
        int columns = tableCells(lines[start]).size();
        int index = start + 2;
        while (index < lines.length && lines[index].contains("|")
                && tableCells(lines[index]).size() == columns) {
            index++;
        }
        return index;
    }

    private static boolean isMarkdownTableStart(String[] lines, int index) {
        if (!lines[index].contains("|") || index + 1 >= lines.length) {
            return false;
        }
        List<String> headers = tableCells(lines[index]);
        List<String> separators = tableCells(lines[index + 1]);
        return headers.size() > 1 && headers.size() == separators.size()
                && separators.stream().allMatch(cell -> TABLE_SEPARATOR.matcher(cell).matches());
    }

    private static List<String> tableCells(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return Arrays.stream(trimmed.split("\\|", -1)).map(String::trim).toList();
    }

    static String normalizeMarkup(String text) {
        return (text == null ? "" : text).replaceAll("(?i)<br\\s*/?>|&lt;br\\s*/?&gt;", "\n");
    }

    private static void flush(VBox box, StringBuilder paragraph) {
        if (paragraph.length() > 0) {
            box.put(UI.para(paragraph.toString()));
            paragraph.setLength(0);
        }
    }

    private static JComponent listItem(String marker, String body) {
        boolean numbered = Character.isDigit(marker.charAt(0));
        Cols row = new Cols(10, numbered ? -26 : -16, 1);
        row.put(UI.label(numbered ? marker : "•", Theme.semibold(13), Theme.TEAL), UI.para(body));
        return row;
    }

    static String plain(String raw) {
        String clean = raw.replace("\\*", "*").replace("**", "").replace("__", "").replace("`", "")
                .replaceFirst("^>\\s?", "").trim();
        return clean.startsWith("* ") ? clean : clean.replace("*", "");
    }

    private static String toolLabel(String name) {
        return switch (name) {
            case "get_workspace_snapshot" -> "Workspace summary";
            case "get_stock_alerts" -> "Stock alerts";
            case "get_inventory" -> "Inventory";
            case "get_disasters" -> "Disaster events";
            case "get_pending_workflow" -> "Pending workflows";
            case "get_donors" -> "Donor records";
            case "get_evacuation_centers" -> "Evacuation centers";
            case "get_memory" -> "Prior assessments";
            case "get_open_follow_ups" -> "Open follow-ups";
            case "get_historical_trends" -> "Historical trends";
            case "simulate_scenario" -> "Scenario simulation";
            default -> name;
        };
    }

    private static final class GrowingArea extends JTextArea implements HFW {
        private static final long serialVersionUID = 1L;
        private final int minRows;

        GrowingArea(int minRows) {
            super("", minRows, 20);
            this.minRows = minRows;
            setLineWrap(true);
            setWrapStyleWord(true);
            setBorder(null);
            setOpaque(false);
            setFont(Theme.regular(13));
            setForeground(Theme.TEXT);
            setCaretColor(Theme.TEAL);
            setSelectionColor(Theme.TEAL_BORDER);
            setSelectedTextColor(Theme.TEXT);
        }

        @Override
        public int heightForWidth(int width) {
            int minimum = minRows * getFontMetrics(getFont()).getHeight();
            setSize(Math.max(width, 1), Short.MAX_VALUE);
            return Math.max(minimum, getPreferredSize().height) + 4;
        }
    }
}
