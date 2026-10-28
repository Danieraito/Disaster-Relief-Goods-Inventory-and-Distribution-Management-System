package SERVER;

import LIB.Barangay;
import LIB.BarangayRepository;
import LIB.Database;
import LIB.UserAccount;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Small helpers shared by the API classes: reading form values, workspaces and transactions. */
final class Api {
    private Api() {
    }

    /** Work done inside one database transaction. */
    interface Work<T> {
        T run(Connection connection) throws Exception;
    }

    /**
     * Runs the work as one all-or-nothing database transaction while holding the write lock.
     * If anything fails, every change in it is rolled back.
     */
    static <T> T inTransaction(Work<T> work) throws Exception {
        synchronized (Database.WRITE_LOCK) {
            try (Connection connection = Database.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    T result = work.run(connection);
                    connection.commit();
                    return result;
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                }
            }
        }
    }

    // ------------------------------------------------------------- form values
    static String text(Map<String, List<String>> form, String name) {
        List<String> values = form.get(name);
        return values == null || values.isEmpty() ? "" : values.get(0).trim();
    }

    static List<String> all(Map<String, List<String>> form, String name) {
        List<String> values = form.get(name);
        return values == null ? Collections.emptyList() : values;
    }

    static String required(Map<String, List<String>> form, String name, String label) {
        String value = text(form, name);
        if (value.isEmpty()) {
            throw new ApiError(400, label + " is required.");
        }
        return value;
    }

    static int wholeNumber(String value, String label) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ApiError(400, label + " must be a whole number.");
        }
    }

    /** Checks a YYYY-MM-DD date. Empty is allowed only when optional is true. */
    static String date(String value, String label, boolean optional) {
        if (value == null || value.trim().isEmpty()) {
            if (optional) {
                return null;
            }
            throw new ApiError(400, label + " is required.");
        }
        try {
            return LocalDate.parse(value.trim()).toString();
        } catch (DateTimeParseException e) {
            throw new ApiError(400, label + " must look like 2026-10-03.");
        }
    }

    // ------------------------------------------------------------- workspaces
    /** The barangay id a user's lists are limited to, or null for Admins (who see every workspace). */
    static String scope(UserAccount user) {
        return user.getRole() == UserAccount.Role.ADMIN ? null : user.getBarangay().getBarangayId();
    }

    /** Admins choose a barangay on the form; everyone else always works in their own. */
    static Barangay targetBarangay(UserAccount user, String chosenId) throws SQLException {
        if (user.getRole() != UserAccount.Role.ADMIN) {
            return user.getBarangay();
        }
        Barangay barangay = new BarangayRepository().findById(chosenId).orElse(null);
        if (barangay == null) {
            throw new ApiError(400, "Choose a barangay workspace.");
        }
        return barangay;
    }

    /** Stops the request if the user may not work in this barangay. */
    static void requireAccess(UserAccount user, String barangayId) {
        if (barangayId == null) {
            throw new ApiError(404, "Record not found.");
        }
        if (user.getRole() != UserAccount.Role.ADMIN
                && !user.getBarangay().getBarangayId().equals(barangayId)) {
            throw new ApiError(403, "This record belongs to another barangay workspace.");
        }
    }

    // ------------------------------------------------------------ stock change
    /** Remembers each item's quantity before a stock change, for the audit log. */
    static Map<LIB.Inventory, Integer> stockBefore(List<LIB.TransactionItem> lines) {
        Map<LIB.Inventory, Integer> before = new java.util.LinkedHashMap<>();
        for (LIB.TransactionItem line : lines) {
            before.putIfAbsent(line.getInventory(), line.getInventory().getQuantity());
        }
        return before;
    }

    /** "Rice 50 → 48 sack" text for each item, used in the audit log's Before → after column. */
    static String[] stockChange(Map<LIB.Inventory, Integer> before) {
        StringBuilder from = new StringBuilder();
        StringBuilder to = new StringBuilder();
        for (Map.Entry<LIB.Inventory, Integer> entry : before.entrySet()) {
            LIB.Inventory item = entry.getKey();
            if (from.length() > 0) {
                from.append("; ");
                to.append("; ");
            }
            from.append(item.getItemName()).append(' ').append(entry.getValue()).append(' ').append(item.getUnit());
            to.append(item.getItemName()).append(' ').append(item.getQuantity()).append(' ').append(item.getUnit());
        }
        return new String[] {from.toString(), to.toString()};
    }

    /** "120 Rice sack, 40 Hygiene kit" text for a list of lines. */
    static String lineSummary(List<LIB.TransactionItem> lines) {
        StringBuilder text = new StringBuilder();
        for (LIB.TransactionItem line : lines) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(line.getQuantity()).append(' ').append(line.getInventory().getUnit())
                    .append(' ').append(line.getInventory().getItemName());
        }
        return text.toString();
    }

    static String roleLabel(String role) {
        if ("ADMIN".equals(role)) {
            return "Admin";
        }
        if ("BARANGAY_OFFICIAL".equals(role)) {
            return "Barangay Official";
        }
        return "Encoder";
    }
}
