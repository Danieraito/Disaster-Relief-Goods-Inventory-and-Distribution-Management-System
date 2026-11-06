import LIB.AiChangeRepository;
import LIB.AiChangeService;
import LIB.Barangay;
import LIB.EncoderAccount;
import LIB.Json;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AiChangeProposalTest {
    public static void main(String[] args) throws Exception {
        Barangay workspace = new Barangay("b-1", "Maligaya", "San Isidro", "Laguna");
        EncoderAccount encoder = new EncoderAccount("u-1", "Test Encoder", "encoder", "encoder@example.test",
                "test-password", workspace);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("action", "inventory_create");
        input.put("summary", "Add first-aid kits");
        input.put("data", Map.of(
                "name", "First-aid kit",
                "category", "Medical",
                "opening_quantity", 25,
                "unit", "kit",
                "threshold", 5,
                "storage", "Store room",
                "note", "Requested by operator"));

        AiChangeRepository.Draft draft = AiChangeService.prepare(
                "Record 25 first-aid kits", input, workspace, encoder);
        Map<String, Object> payload = Json.asObject(Json.parse(draft.proposal));
        if (!"inventory_create".equals(draft.action)
                || !"Record 25 first-aid kits".equals(draft.request)
                || !"Add first-aid kits".equals(draft.summary)
                || !draft.before.equals("{}")
                || !"First-aid kit".equals(Json.asObject(payload.get("data")).get("name"))) {
            throw new AssertionError("The proposal did not preserve the request and proposed values.");
        }

        expectInvalid(Map.of("action", "arbitrary_sql", "summary", "Bad action", "data", Map.of("sql", "UPDATE")));
        expectInvalid(Map.of("action", "inventory_create", "summary", "Incomplete", "data",
                Map.of("name", "Kit")));
        expectInvalid(Map.of("action", "inventory_create", "summary", "Extra field", "data",
                Map.of("name", "Kit", "category", "Medical", "opening_quantity", 1, "unit", "kit",
                        "threshold", 0, "storage", "A1", "execute_sql", "no")));
        System.out.println("AI change proposal validation tests passed.");
    }

    private static void expectInvalid(Map<String, Object> input) throws Exception {
        Barangay workspace = new Barangay("b-1", "Maligaya", "San Isidro", "Laguna");
        EncoderAccount encoder = new EncoderAccount("u-1", "Test Encoder", "encoder", "encoder@example.test",
                "test-password", workspace);
        try {
            AiChangeService.prepare("test", input, workspace, encoder);
            throw new AssertionError("Invalid proposal was accepted.");
        } catch (IllegalArgumentException expected) {
            if (expected.getMessage() == null || expected.getMessage().isBlank()) {
                throw new AssertionError("Rejected proposals must explain why they were invalid.");
            }
        }
    }
}
