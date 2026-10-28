package SERVER;

import LIB.AuditRepository;
import LIB.Barangay;
import LIB.Database;
import LIB.Disaster;
import LIB.DisasterController;
import LIB.DisasterRepository;
import LIB.EvacuationCenter;
import LIB.EvacuationCenterController;
import LIB.UserAccount;
import com.sun.net.httpserver.HttpExchange;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

/** Disasters screen: record disaster events, close or reopen them, and manage evacuation centers. */
final class DisasterApi {
    private static final List<String> TYPES = List.of("Typhoon", "Flood", "Fire", "Earthquake", "Landslide", "Other");
    private static final List<String> SEVERITIES = List.of("LOW", "MODERATE", "HIGH");

    private final DisasterRepository repository = new DisasterRepository();
    private final DisasterController controller = new DisasterController();
    private final EvacuationCenterController centerController = new EvacuationCenterController();
    private final AuditRepository audits = new AuditRepository();

    void list(HttpExchange exchange, UserAccount user) throws Exception {
        String scope = Api.scope(user);
        Http.sendJson(exchange, 200, Json.obj(
                "disasters", repository.listSummaries(scope),
                "centers", repository.listCenters(scope),
                "types", TYPES,
                "severities", SEVERITIES));
    }

    void create(HttpExchange exchange, UserAccount user) throws Exception {
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String name = Api.required(form, "name", "Disaster name");
        String type = Api.required(form, "type", "Disaster type");
        String date = Api.date(Api.text(form, "date"), "Date of occurrence", false);
        String severity = Api.text(form, "severity").toUpperCase();
        if (!SEVERITIES.contains(severity)) {
            throw new ApiError(400, "Choose a severity: Low, Moderate or High.");
        }
        String areas = Api.required(form, "affectedAreas", "Affected areas");
        Barangay barangay = Api.targetBarangay(user, Api.text(form, "barangayId"));

        String id = Api.inTransaction((Connection connection) -> {
            String disasterId = Database.nextId(connection, "disasters", "disaster_id", "DIS");
            // The controller checks that this user may work in the barangay.
            Disaster disaster = controller.createDisaster(disasterId, name, type, barangay.getName(),
                    date, "ACTIVE", barangay, user);
            disaster.setSeverity(severity);
            disaster.setAffectedAreas(areas);
            DisasterRepository.save(connection, disaster, null);
            audits.record(connection, user, "Disasters", barangay.getBarangayId(), "Disaster recorded",
                    disasterId, null, name + " · " + type + " · " + severity, areas, null, null);
            return disasterId;
        });
        Http.sendJson(exchange, 201, Json.obj("ok", true, "id", id));
    }

    /** Closes an active disaster (response finished) or reopens a closed one. */
    void changeStatus(HttpExchange exchange, UserAccount user) throws Exception {
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String disasterId = Api.required(form, "id", "Disaster");
        boolean close = "CLOSED".equals(Api.text(form, "status"));
        String reason = Api.text(form, "reason");

        Api.inTransaction((Connection connection) -> {
            Disaster disaster = DisasterRepository.findById(connection, disasterId);
            if (disaster == null) {
                throw new ApiError(404, "Disaster not found.");
            }
            String before = disaster.isActive() ? "Active" : "Closed";
            controller.updateDisasterStatus(disaster, close ? "CLOSED" : "ACTIVE", user);
            DisasterRepository.save(connection, disaster, close ? Database.today().toString() : null);
            audits.record(connection, user, "Disasters", disaster.getBarangay().getBarangayId(),
                    close ? "Disaster closed" : "Disaster reopened", disasterId, before,
                    close ? "Closed" : "Active", reason.isEmpty() ? null : reason, null, null);
            return null;
        });
        Http.sendJson(exchange, 200, Json.obj("ok", true));
    }

    void createCenter(HttpExchange exchange, UserAccount user) throws Exception {
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String name = Api.required(form, "name", "Center name");
        String address = Api.required(form, "address", "Address");
        int capacity = Api.wholeNumber(Api.required(form, "capacity", "Capacity"), "Capacity");
        Barangay barangay = Api.targetBarangay(user, Api.text(form, "barangayId"));

        String id = Api.inTransaction((Connection connection) -> {
            String centerId = Database.nextId(connection, "evacuation_centers", "center_id", "EC");
            EvacuationCenter center = centerController.createEvacuationCenter(centerId, name, address,
                    capacity, barangay, user);
            DisasterRepository.saveCenter(connection, center);
            audits.record(connection, user, "Disasters", barangay.getBarangayId(), "Evacuation center added",
                    centerId, null, name + " · capacity " + capacity, address, null, null);
            return centerId;
        });
        Http.sendJson(exchange, 201, Json.obj("ok", true, "id", id));
    }
}
