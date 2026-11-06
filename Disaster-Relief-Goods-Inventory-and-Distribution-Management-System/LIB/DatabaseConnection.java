package LIB;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;

/**
 * Opens JDBC connections. Settings come from the DB_URL / DB_USER / DB_PASSWORD environment
 * variables first, then from an optional db.properties file in the working folder
 * (non-secret keys: url and user). Passwords are environment-only or held in memory for the session.
 * Closed connections go back into a small pool so screens that run
 * several queries do not pay the connection cost of a hosted database every time.
 */
public final class DatabaseConnection {
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/disaster_relief_inventory";
    private static final Path CONFIG_FILE = Paths.get("db.properties");
    private static final int MAX_IDLE = 4;
    private static final Deque<Connection> IDLE = new ArrayDeque<>();
    private static String poolKey = "";
    private static String sessionUrl;
    private static String sessionUser;
    private static String sessionPassword;

    public static final class Settings {
        public final String url;
        public final String user;
        public final String password;

        Settings(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }
    }

    private DatabaseConnection() {
    }

    public static Settings settings() {
        Properties file = loadFile();
        String url = firstText(System.getenv("DB_URL"), sessionUrl, file.getProperty("url"), DEFAULT_URL);
        String user = firstText(System.getenv("DB_USER"), sessionUser, file.getProperty("user"), "");
        String password = System.getenv("DB_PASSWORD");
        if (password == null || password.isEmpty()) {
            password = sessionPassword == null ? "" : sessionPassword;
        }
        return new Settings(url, user, password);
    }

    public static boolean isConfigured() {
        Settings settings = settings();
        return !settings.user.isEmpty() && !settings.password.isEmpty();
    }

    /** Saves non-secret connection settings only. Passwords are never persisted by the application. */
    public static void saveSettings(String url, String user, String password) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("url", url.trim());
        properties.setProperty("user", user.trim());
        properties.remove("password");
        try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
            properties.store(out, "BarangayCare database location; set DB_PASSWORD in the environment.");
        }
        setSessionSettings(url, user, password);
    }

    /** Keeps credentials in memory for the current process only. */
    public static synchronized void setSessionSettings(String url, String user, String password) {
        sessionUrl = firstText(url);
        sessionUser = firstText(user);
        sessionPassword = password == null ? "" : password;
        closeIdle();
    }

    /** Tries a one-off connection (not pooled); used by the connection settings dialog. */
    public static void test(String url, String user, String password) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url.trim(), user.trim(), password)) {
            if (!connection.isValid(2)) {
                throw new SQLException("The database connection could not be validated.");
            }
        }
    }

    public static Connection getConnection() throws SQLException {
        Settings settings = settings();
        if (settings.user.isEmpty()) {
            throw new SQLException("Database user is not set. Define DB_USER or save it in db.properties.");
        }
        if (settings.password.isEmpty()) {
            throw new SQLException("Database password is not set. Define DB_PASSWORD or enter it for this session.");
        }
        String key = settings.url + "|" + settings.user + "|" + settings.password.hashCode();
        Connection raw = null;
        synchronized (DatabaseConnection.class) {
            if (!key.equals(poolKey)) {
                closeIdleLocked();
                poolKey = key;
            }
            while (raw == null && !IDLE.isEmpty()) {
                Connection candidate = IDLE.pop();
                try {
                    if (candidate.isValid(2)) {
                        raw = candidate;
                    } else {
                        candidate.close();
                    }
                } catch (SQLException ignored) {
                    // discard a broken pooled connection and try the next one
                }
            }
        }
        if (raw == null) {
            raw = DriverManager.getConnection(settings.url, settings.user, settings.password);
        }
        return wrap(raw);
    }

    private static Connection wrap(Connection raw) {
        InvocationHandler handler = new InvocationHandler() {
            private boolean closed;

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                if (name.equals("close")) {
                    if (!closed) {
                        closed = true;
                        release(raw);
                    }
                    return null;
                }
                if (name.equals("isClosed")) {
                    return closed || raw.isClosed();
                }
                if (closed) {
                    throw new SQLException("Connection is closed.");
                }
                try {
                    return method.invoke(raw, args);
                } catch (InvocationTargetException exception) {
                    throw exception.getCause();
                }
            }
        };
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class }, handler);
    }

    private static void release(Connection raw) {
        try {
            if (raw.isClosed()) {
                return;
            }
            if (!raw.getAutoCommit()) {
                raw.rollback();
                raw.setAutoCommit(true);
            }
            synchronized (DatabaseConnection.class) {
                if (IDLE.size() < MAX_IDLE) {
                    IDLE.push(raw);
                    return;
                }
            }
            raw.close();
        } catch (SQLException exception) {
            try {
                raw.close();
            } catch (SQLException ignored) {
                // nothing more to do
            }
        }
    }

    private static synchronized void closeIdle() {
        closeIdleLocked();
        poolKey = "";
    }

    private static void closeIdleLocked() {
        while (!IDLE.isEmpty()) {
            try {
                IDLE.pop().close();
            } catch (SQLException ignored) {
                // closing a stale connection can fail safely
            }
        }
    }

    private static Properties loadFile() {
        Properties properties = new Properties();
        if (Files.isReadable(CONFIG_FILE)) {
            try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
                properties.load(in);
            } catch (IOException ignored) {
                // an unreadable file behaves like a missing one
            }
        }
        properties.remove("password");
        return properties;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}
