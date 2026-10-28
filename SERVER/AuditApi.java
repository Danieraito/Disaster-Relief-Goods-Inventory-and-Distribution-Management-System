package SERVER;

import LIB.AuditRepository;
import LIB.UserAccount;
import com.sun.net.httpserver.HttpExchange;
import java.util.List;
import java.util.Map;

/**
 * Audit Logs screen. Admins see every workspace, Officials see their barangay, and Encoders
 * see only their own activity. Report exports are logged here too.
 */
final class AuditApi {
    private final AuditRepository audits = new AuditRepository();

    void list(HttpExchange exchange, UserAccount user) throws Exception {
        String onlyMine = user.canExportReports() ? null : user.getAccountId();
        Http.sendJson(exchange, 200, Json.obj(
                "entries", audits.list(Api.scope(user), onlyMine),
                "ownActivityOnly", onlyMine != null));
    }

    void reportExported(HttpExchange exchange, UserAccount user) throws Exception {
        if (!user.canExportReports()) {
            throw new ApiError(403, "Your role can view reports but not export them.");
        }
        Map<String, List<String>> form = Http.parseFormMulti(exchange);
        String report = Api.required(form, "report", "Report name");
        String format = Api.text(form, "format");
        audits.recordAlone(user, "Reports", Api.scope(user), "Report exported", report, null,
                format.isEmpty() ? "Exported" : format, Api.text(form, "filters"));
        Http.sendJson(exchange, 200, Json.obj("ok", true));
    }
}
