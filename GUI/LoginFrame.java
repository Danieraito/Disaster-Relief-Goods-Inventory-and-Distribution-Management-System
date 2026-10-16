package GUI;

import LIB.UserAccount;
import LIB.UserRepository;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.sql.SQLException;
import java.util.Optional;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.BorderFactory;

/** Login screen. */
public class LoginFrame extends JFrame {
    private final UserRepository users = new UserRepository();
    private final JTextField usernameField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JLabel messageLabel = new JLabel(" ");

    public LoginFrame() {
        super("BarangayCare - Login");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(28, 36, 28, 36));

        JLabel title = new JLabel("BarangayCare");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        title.setForeground(new Color(0x1F3A5F));
        title.setAlignmentX(LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Disaster Relief Goods Inventory and Distribution");
        subtitle.setAlignmentX(LEFT_ALIGNMENT);

        content.add(title);
        content.add(subtitle);
        content.add(Box.createRigidArea(new Dimension(0, 20)));
        content.add(labeled("Username", usernameField));
        content.add(Box.createRigidArea(new Dimension(0, 10)));
        content.add(labeled("Password", passwordField));
        content.add(Box.createRigidArea(new Dimension(0, 16)));

        JButton loginButton = new JButton("Log in");
        loginButton.setAlignmentX(LEFT_ALIGNMENT);
        loginButton.addActionListener(e -> login());
        content.add(loginButton);

        messageLabel.setForeground(new Color(0xB00020));
        messageLabel.setAlignmentX(LEFT_ALIGNMENT);
        content.add(Box.createRigidArea(new Dimension(0, 10)));
        content.add(messageLabel);

        setContentPane(content);
        getRootPane().setDefaultButton(loginButton); // Enter key logs in
        pack();
        setMinimumSize(new Dimension(420, getHeight()));
        setLocationRelativeTo(null);
    }

    private JPanel labeled(String text, java.awt.Component field) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel label = new JLabel(text);
        label.setAlignmentX(LEFT_ALIGNMENT);
        row.add(label);
        row.add(Box.createRigidArea(new Dimension(0, 4)));
        ((javax.swing.JComponent) field).setAlignmentX(LEFT_ALIGNMENT);
        row.add(field);
        return row;
    }

    private void login() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        try {
            Optional<UserAccount> account = users.authenticate(username, password);
            if (account.isPresent()) {
                dispose();
                new MainFrame(account.get()).setVisible(true);
            } else {
                messageLabel.setText("Incorrect username or password, or the account is inactive.");
                passwordField.setText("");
            }
        } catch (SQLException e) {
            messageLabel.setText("Database error: " + e.getMessage());
        }
    }
}
