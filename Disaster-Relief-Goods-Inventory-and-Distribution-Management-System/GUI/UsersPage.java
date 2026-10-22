package GUI;

import GUI.Theme.Tone;
import LIB.Barangay;
import LIB.BarangayRepository;
import LIB.Time;
import LIB.UserAccount;
import LIB.UserAccountController;
import LIB.UserRepository;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JTextField;

/** User accounts, roles and workspaces (Admin only). */
class UsersPage extends Page {
    private static final String ALL_ROLES = "All roles";
    private static final String ALL_STATUS = "All account status";

    private List<UserAccount> users = new ArrayList<>();
    private List<Barangay> barangays = new ArrayList<>();
    private String search = "";
    private String roleFilter = ALL_ROLES;
    private String statusFilter = ALL_STATUS;
    private String message;
    private UserAccount deactivating;
    private final VBox tableHolder = new VBox(0);

    @Override String module() { return "User administration"; }
    @Override String crumb() { return "User administration"; }

    @Override
    void load() throws Exception {
        if (!Session.isAdmin()) {
            throw new SecurityException("User administration is visible only to Admin accounts.");
        }
        users = new UserRepository().findAll();
        barangays = new BarangayRepository().findAll();
    }

    @Override
    JComponent build() {
        VBox page = new VBox(20);
        page.put(UI.pageHeader("User accounts & roles", "Admin controls for workspace access and accountable operations.",
                UI.actions(UI.secondary("Add barangay workspace", this::addBarangay))));
        if (message != null) {
            page.put(UI.alert(Tone.TEAL, "Saved", message));
        }
        int active = 0;
        for (UserAccount user : users) {
            if (user.isActive()) {
                active++;
            }
        }
        Card accounts = new Card(14);
        VBox heading = new VBox(4);
        heading.put(UI.h2("User accounts"), UI.muted(active + " active users · " + (users.size() - active) + " inactive accounts"));
        accounts.put(heading, filters(), tableHolder);
        fillTable();
        page.put(accounts);
        page.put(new Cols(22, 1.5, 1).put(permissionsCard(), new VBox(20).put(inviteCard(), deactivateCard())));
        return page;
    }

    private JComponent filters() {
        Input box = new Input(search).placeholder("Search user name or email");
        box.getDocument().addDocumentListener(new SimpleListener(() -> {
            search = box.getText();
            fillTable();
        }));
        Dropdown<String> roleBox = Dropdown.of(ALL_ROLES, "Admin", "Barangay Official", "Encoder");
        roleBox.setSelected(roleFilter);
        roleBox.onChange(() -> {
            roleFilter = roleBox.getSelected();
            fillTable();
        });
        Dropdown<String> statusBox = Dropdown.of(ALL_STATUS, "Active", "Inactive");
        statusBox.setSelected(statusFilter);
        statusBox.onChange(() -> {
            statusFilter = statusBox.getSelected();
            fillTable();
        });
        Field roleCell = new Field(null, roleBox);
        Field statusCell = new Field(null, statusBox);
        roleCell.setPreferredSize(new Dimension(170, 44));
        statusCell.setPreferredSize(new Dimension(190, 44));
        return new Cols(12, 1, 0, 0).put(new Field(null, box), roleCell, statusCell);
    }

    private void fillTable() {
        tableHolder.removeAll();
        DataTable table = new DataTable(new String[] { "Name / account", "Role", "Last active", "Status", "Account actions" },
                2, 1.1, 1.2, 0.9, 2.2);
        String needle = search.trim().toLowerCase();
        int shown = 0;
        for (UserAccount user : users) {
            if (!needle.isEmpty() && !(user.getName() + " " + user.getEmail()).toLowerCase().contains(needle)
                    || !roleFilter.equals(ALL_ROLES) && !roleFilter.equals(user.getRoleLabel())
                    || !statusFilter.equals(ALL_STATUS) && !statusFilter.equals(user.isActive() ? "Active" : "Inactive")) {
                continue;
            }
            shown++;
            Row actions = new Row(12);
            actions.put(UI.link("Edit role", () -> editRole(user)), UI.link("Reset password", () -> resetPassword(user)));
            if (user.isActive()) {
                actions.put(UI.link("Deactivate", () -> {
                    deactivating = user;
                    rebuild();
                }));
            } else {
                actions.put(UI.link("Reactivate", () -> reactivate(user)));
            }
            actions.put(UI.link("View audit", () -> go(new AuditPage(user.getName()))));
            table.row(new String[] { user.getName(), user.getEmail() + (user.getBarangay() == null ? "" : " · " + user.getBarangay().getName()) },
                    UI.left(UI.pill(user.getRoleLabel(), user.getRole() == UserAccount.Role.ADMIN ? Tone.INFO : Tone.NEUTRAL)),
                    user.getLastLoginAt() == null ? "Never" : Time.stamp(user.getLastLoginAt()),
                    UI.left(UI.pill(user.isActive() ? "Active" : "Inactive", user.isActive() ? Tone.TEAL : Tone.NEUTRAL)), actions);
        }
        if (shown == 0) {
            table.empty("No accounts match this view.");
        }
        tableHolder.put(table);
        tableHolder.revalidate();
        tableHolder.repaint();
    }

    private JComponent permissionsCard() {
        DataTable table = new DataTable(new String[] { "Capability", "Admin", "Barangay Official", "Encoder" }, 2.2, 1, 1.2, 1.5);
        table.row("View dashboard & module records", "Allowed", "Allowed", "Allowed")
                .row("Register households / prepare records", "Allowed", "Allowed", "Allowed")
                .row("Post donations / confirm releases", "Allowed", "Allowed", "Drafts only")
                .row("Adjust stock / delete unused records", "Allowed", "Allowed", "Not allowed")
                .row("Export reports / view audit logs", "Allowed", "Allowed", "Read only reports / own activity")
                .row("Manage users & assign roles", "Allowed", "Not allowed", "Not allowed");
        return UI.section("Role permissions", "User administration is visible only to Admin accounts. Other roles retain the "
                + "operational modules.", table);
    }

    private JComponent inviteCard() {
        Input name = new Input("");
        Dropdown<String> role = Dropdown.of("Encoder", "Barangay Official", "Admin");
        Input email = new Input("");
        Dropdown<Barangay> workspace = new Dropdown<>(barangays, barangay -> "Barangay " + barangay.getName());
        workspace.setSelected(Session.workspace);
        VBox error = new VBox(0);
        Wrap roleNote = new Wrap("Encoder can enter records and prepare drafts, but cannot confirm stock changing transactions or "
                + "manage accounts.", Theme.regular(12), Theme.MUTED);
        role.onChange(() -> roleNote.setText(role.getSelected().equals("Admin")
                ? "Admins can use every module, manage accounts and open any barangay workspace."
                : role.getSelected().equals("Barangay Official") ? "Barangay Officials can manage records, confirm releases and export reports."
                        : "Encoder can enter records and prepare drafts, but cannot confirm stock changing transactions or manage accounts."));
        Btn send = UI.primary("Create account");
        send.addActionListener(event -> {
            send.setEnabled(false);
            error.removeAll();
            UserAccount.Role chosen = role.getSelected().equals("Admin") ? UserAccount.Role.ADMIN
                    : role.getSelected().equals("Barangay Official") ? UserAccount.Role.BARANGAY_OFFICIAL : UserAccount.Role.ENCODER;
            Task.run(() -> new UserAccountController().invite(name.getText(), email.getText(), chosen, workspace.getSelected(),
                    Session.user), password -> {
                        showPassword("Account created", name.getText().trim() + " can sign in with " + email.getText().trim() + ".", password);
                        message = name.getText().trim() + " was added as " + role.getSelected() + ".";
                        reload();
                    }, failure -> {
                        send.setEnabled(true);
                        error.put(UI.alert(Tone.DANGER, "The account was not created", Task.message(failure)));
                        error.revalidate();
                        error.repaint();
                    });
        });
        Card card = new Card(12);
        card.put(UI.h2("Invite new user"), new Field("Full name *", name), new Field("Assigned role *", role),
                new Field("Email address *", email, "Used to sign in."), new Field("Workspace *", workspace),
                roleNote, UI.smallPara("A temporary password is shown once; the user can change it after signing in."), error,
                UI.left(send));
        return card;
    }

    private JComponent deactivateCard() {
        Card card = new Card(12);
        card.put(UI.h2("Deactivate account safely"));
        if (deactivating == null) {
            card.put(UI.mutedPara("Choose Deactivate on an account to stop its access. Past transactions stay attributed to the person "
                    + "and audit records are never deleted."));
            return card;
        }
        Area reason = new Area("", 3);
        VBox error = new VBox(0);
        Btn confirm = UI.danger("Deactivate " + deactivating.getName());
        confirm.addActionListener(event -> {
            confirm.setEnabled(false);
            UserAccount target = deactivating;
            Task.run(() -> new UserAccountController().deactivate(target, reason.getText(), Session.user), () -> {
                message = target.getName() + " was deactivated. Their past transactions remain attributed to them.";
                deactivating = null;
                reload();
            }, failure -> {
                confirm.setEnabled(true);
                error.removeAll();
                error.put(UI.alert(Tone.DANGER, "The account was not deactivated", Task.message(failure)));
                error.revalidate();
                error.repaint();
            });
        });
        card.put(UI.para("Deactivate " + deactivating.getName() + " · " + deactivating.getEmail()
                + ". Access stops immediately; audit and relief records are never deleted."),
                new Field("Reason for access change *", reason), UI.note(Tone.WARN, "Keep at least one active Admin",
                        "Changes to roles and account status are logged with the acting Admin's name and timestamp."), error,
                UI.actions(UI.secondary("Cancel", () -> {
                    deactivating = null;
                    rebuild();
                }), confirm));
        return card;
    }

    // ------------------------------------------------------------------ dialogs

    private void showPassword(String title, String text, String password) {
        JTextField field = new JTextField(password);
        field.setEditable(false);
        field.setFont(Theme.semibold(18));
        field.setForeground(Theme.TEAL);
        field.setBorder(null);
        field.setOpaque(false);
        field.setPreferredSize(new Dimension(300, 30));
        VBox form = new VBox(12);
        form.put(UI.para(text), new Field("Temporary password (shown once)", field),
                UI.alert(Tone.WARN, "Share it privately", "It cannot be shown again. If it is lost, reset the password."));
        Dialogs.form(shell, title, null, form, "Done", () -> null, 560);
    }

    private void editRole(UserAccount user) {
        Dropdown<String> role = Dropdown.of("Admin", "Barangay Official", "Encoder");
        role.setSelected(user.getRoleLabel());
        Dropdown<Barangay> workspace = new Dropdown<>(barangays, barangay -> "Barangay " + barangay.getName());
        workspace.setSelected(user.getBarangay() == null ? Session.workspace : user.getBarangay());
        Area reason = new Area("", 2);
        VBox form = new VBox(12);
        form.put(new Field("Assigned role *", role), new Field("Workspace", workspace, "Ignored for Admins."),
                new Field("Reason for access change *", reason));
        boolean ok = Dialogs.form(shell, "Edit role · " + user.getName(), null, form, "Save role", () -> {
            try {
                UserAccount.Role chosen = role.getSelected().equals("Admin") ? UserAccount.Role.ADMIN
                        : role.getSelected().equals("Barangay Official") ? UserAccount.Role.BARANGAY_OFFICIAL : UserAccount.Role.ENCODER;
                new UserAccountController().changeRole(user, chosen, workspace.getSelected(), reason.getText(), Session.user);
                return null;
            } catch (Exception error) {
                return Task.message(error);
            }
        }, 560);
        if (ok) {
            message = user.getName() + " is now " + role.getSelected() + ".";
            reload();
        }
    }

    private void resetPassword(UserAccount user) {
        if (!Dialogs.confirm(shell, "Reset password?", "Issue a new temporary password for " + user.getName()
                + "? Their current password stops working immediately.", "Reset password", true)) {
            return;
        }
        Task.run(() -> new UserAccountController().resetPassword(user, Session.user),
                password -> showPassword("Password reset", "Give this temporary password to " + user.getName() + ".", password),
                error -> Dialogs.error(shell, error));
    }

    private void reactivate(UserAccount user) {
        Area reason = new Area("", 2);
        VBox form = new VBox(12);
        form.put(new Field("Reason for access change *", reason));
        boolean ok = Dialogs.form(shell, "Reactivate " + user.getName(), null, form, "Reactivate", () -> {
            try {
                new UserAccountController().reactivate(user, reason.getText(), Session.user);
                return null;
            } catch (Exception error) {
                return Task.message(error);
            }
        }, 520);
        if (ok) {
            message = user.getName() + " can sign in again.";
            reload();
        }
    }

    private void addBarangay() {
        Input name = new Input("");
        Input municipality = new Input("");
        Input province = new Input("");
        VBox form = new VBox(12);
        form.put(new Field("Barangay name *", name), new Cols(12, 1, 1).put(new Field("Municipality *", municipality),
                new Field("Province *", province)));
        boolean ok = Dialogs.form(shell, "Add barangay workspace", "Each workspace keeps its own inventory, disasters and records.",
                form, "Create workspace", () -> {
                    if (name.getText().trim().isEmpty() || municipality.getText().trim().isEmpty() || province.getText().trim().isEmpty()) {
                        return "Fill in every field.";
                    }
                    try {
                        new BarangayRepository().create(name.getText(), municipality.getText(), province.getText(), Session.user);
                        return null;
                    } catch (Exception error) {
                        return Task.message(error);
                    }
                }, 600);
        if (ok) {
            message = "Barangay " + name.getText().trim() + " workspace was created.";
            reload();
        }
    }
}
