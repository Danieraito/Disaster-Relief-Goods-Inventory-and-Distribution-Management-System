import LIB.AiAgent;
import LIB.AiAssistant;
import LIB.AiEvidence;
import LIB.AiMemoryRepository;
import LIB.AiPrivacyGuard;
import LIB.AiSimulator;
import LIB.AiTools;
import LIB.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AiSafetyAndForecastTest {
    public static void main(String[] args) {
        privacyGuard();
        unitSafeSimulation();
        historicalBaseline();
        evidenceReferences();
        followUpDescriptions();
        toolEvaluationSurface();
        System.out.println("AI safety, evidence, and forecast tests passed.");
    }

    private static void privacyGuard() {
        expect(AiPrivacyGuard.containsPersonalData("Register a household for Ana de la Cruz"));
        expect(AiPrivacyGuard.containsPersonalData("Register household at 15 Rizal Street"));
        expect(AiPrivacyGuard.containsPersonalData("Reach me at person@example.test"));
        expect(AiPrivacyGuard.containsPersonalData("Call 09171234567"));
        reject(AiPrivacyGuard.containsPersonalData("Check drinking water stock, pending donations, and household counts"));
    }

    private static void unitSafeSimulation() {
        AiSimulator.Result result = AiSimulator.run("Flood", "CRITICAL", 14, 8, 20, 100,
                List.of(new AiSimulator.NeedStock("Food", 30)),
                new AiSimulator.HistoricalBaseline(0, 0, 0));
        String report = result.report();
        expect(report.contains("no numeric forecast is produced"));
        expect(report.contains("no inventory comparison"));
        reject(report.contains("shortfall="));
        reject(report.contains("1.5x"));
    }

    private static void historicalBaseline() {
        AiSimulator.Result result = AiSimulator.run("Flood", "HIGH", 7, 8, 20, 100,
                List.of(), new AiSimulator.HistoricalBaseline(3, 10.4, 27.2));
        String report = result.report();
        expect(report.contains("3 completed event(s)"));
        expect(report.contains("average registered households=10"));
        expect(report.contains("not a calibrated future prediction"));
    }

    private static void evidenceReferences() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("get_inventory", "INVENTORY: id=ITEM-1 | usable=12 kit");
        sources.put("get_disasters", "DISASTERS: id=DIS-2 | status=ACTIVE");
        sources.put("get_historical_trends", "OBSERVED: no missing values");
        sources.put("propose_change", "id=private-proposal");
        String answer = AiEvidence.attach("Review the stock.", sources);
        expect(answer.contains("Evidence coverage: High"));
        expect(answer.contains("get_inventory · record IDs: ITEM-1"));
        reject(answer.contains("private-proposal"));
        expect(AiEvidence.attach("No data.", Map.of()).contains("Evidence coverage: Low"));
    }

    private static void followUpDescriptions() {
        String answer = "## Follow-up\n- Review shortages with the inventory manager.\n\n"
                + AiEvidence.attach("Assessment complete.",
                        Map.of("get_stock_alerts", "Low stock: item-1", "get_open_follow_ups", "None recorded."));
        List<String> followUps = AiMemoryRepository.extractFollowUps(answer);
        expect(followUps.size() == 1);
        expect(followUps.get(0).equals("Review shortages with the inventory manager."));
        expect(!followUps.get(0).contains("get_"));
        List<String> specific = AiMemoryRepository.extractFollowUps("## Follow-up\n"
                + "- [OPEN] Paracetamol batch B-12: 8 boxes are expired; isolate them for disposal; "
                + "done when the batch is marked unavailable and disposal is logged.");
        expect(specific.size() == 1);
        expect(specific.get(0).contains("Paracetamol batch B-12"));
        expect(specific.get(0).contains("8 boxes are expired"));
        expect(specific.get(0).contains("disposal is logged"));
        List<String> toolOnly = AiMemoryRepository.extractFollowUps("## Follow-up\n"
                + "- get_stock_alerts\n- get_open_follow_ups");
        expect(toolOnly.isEmpty());
        List<String> markedToolOnly = AiMemoryRepository.extractFollowUps(
                "## Follow-up\n- [OPEN] get_stock_alerts\n- [OPEN] get_open_follow_ups");
        expect(markedToolOnly.isEmpty());
    }

    private static void toolEvaluationSurface() {
        List<String> planTools = AiTools.defaultTools(AiAgent.Mode.PLAN);
        expect(planTools.contains("get_historical_trends"));
        Map<String, Object> toolNames = new LinkedHashMap<>();
        for (AiTools.Spec spec : AiTools.catalog()) {
            toolNames.put(spec.name, spec.parameters);
        }
        expect(toolNames.containsKey("propose_change"));
        expect(toolNames.containsKey("get_historical_trends"));
        List<Map<String, Object>> readOnlyTools = AiTools.openaiTools(AiAgent.Mode.ASK, "How much water is available?");
        reject(hasTool(readOnlyTools, "propose_change"));
        expect(hasTool(AiTools.openaiTools(AiAgent.Mode.ASK, "Please add 10 hygiene kits"), "propose_change"));
        expect(hasTool(AiTools.openaiTools(AiAgent.Mode.ASK, "Set rice stock threshold to 10"), "propose_change"));
        expect(AiTools.isInventoryChangeRequest("Set rice stock threshold to 10"));
        expect(AiTools.isInventoryChangeRequest("Mark batch B-12 as expired"));
        reject(AiTools.isInventoryChangeRequest("How much rice is in stock?"));
        reject(AiTools.isInventoryChangeRequest("Update the active disaster status"));
        Map<String, Object> proposalTool = findTool(
                AiTools.openaiTools(AiAgent.Mode.ASK, "Please add 10 hygiene kits"), "propose_change");
        Map<String, Object> schema = Json.asObject(Json.asObject(proposalTool.get("function")).get("parameters"));
        expect(Json.asArray(schema.get("required")).containsAll(List.of("action", "summary", "data")));
        AiAssistant.Turn turn = new AiAssistant.Turn("ok", List.of());
        expect(turn.promptTokens == 0 && turn.completionTokens == 0);
    }

    private static boolean hasTool(List<Map<String, Object>> tools, String name) {
        return tools.stream().anyMatch(tool -> name.equals(
                Json.text(Json.asObject(tool.get("function")).get("name"))));
    }

    private static Map<String, Object> findTool(List<Map<String, Object>> tools, String name) {
        return tools.stream().filter(tool -> name.equals(
                Json.text(Json.asObject(tool.get("function")).get("name")))).findFirst().orElseThrow();
    }

    private static void expect(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected condition to be true.");
        }
    }

    private static void reject(boolean condition) {
        if (condition) {
            throw new AssertionError("Expected condition to be false.");
        }
    }
}
