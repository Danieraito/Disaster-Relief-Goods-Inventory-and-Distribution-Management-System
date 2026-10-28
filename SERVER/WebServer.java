package SERVER;

import LIB.AdminAccount;
import LIB.Barangay;
import LIB.BarangayOfficialAccount;
import LIB.BarangayRepository;
import LIB.Database;
import LIB.EncoderAccount;
import LIB.UserAccount;
import LIB.UserRepository;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * Starts BarangayCare as a website. Run it from the project root, then open
 * http://localhost:8080 in your browser.
 */
public class WebServer {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;

        Database.initialize(true);
        createDemoAccountsIfEmpty();

        // 127.0.0.1 means only this computer can open the site.
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/", new ApiHandler(new Sessions()));
        server.createContext("/", new StaticHandler(Paths.get("web")));
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println("BarangayCare is running.");
        System.out.println("Open http://localhost:" + port + " in your browser. Press Ctrl+C to stop.");
    }

    // Demo logins so the site can be tried right away. Change or remove before real use.
    private static void createDemoAccountsIfEmpty() throws Exception {
        UserRepository users = new UserRepository();
        if (users.findAll().isEmpty()) {
            users.save(new AdminAccount("USR-ADMIN", "System Admin", "admin",
                    "admin@barangaycare.local", "Admin#2026"));
            Barangay barangay = new BarangayRepository().findById("BRG-001").orElse(null);
            if (barangay != null) {
                users.save(new BarangayOfficialAccount("USR-OFF1", "Maria Santos", "msantos",
                        "msantos@barangaycare.local", "Official#2026", barangay));
            }
        }
        createDemoEncoderIfMissing(users);
    }

    // A demo Encoder so all three roles can be tried. Only added when the database has users but no Encoder.
    private static void createDemoEncoderIfMissing(UserRepository users) throws Exception {
        if (users.findAll().isEmpty() || users.findByEmail("lgarcia@barangaycare.local").isPresent()) {
            return;
        }
        for (UserAccount account : users.findAll()) {
            if (account.getRole() == UserAccount.Role.ENCODER) {
                return;
            }
        }
        Barangay barangay = new BarangayRepository().findById("BRG-001").orElse(null);
        if (barangay != null) {
            users.save(new EncoderAccount("USR-ENC1", "Leo Garcia", "lgarcia",
                    "lgarcia@barangaycare.local", "Encoder#2026", barangay));
        }
    }
}
