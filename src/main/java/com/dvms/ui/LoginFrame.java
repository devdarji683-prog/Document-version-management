package com.dvms.ui;

import com.dvms.service.AuthResult;
import com.dvms.service.AuthService;
import com.dvms.service.SessionManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;

public final class LoginFrame extends JFrame {
    private static final String APPLICATION_TITLE = "Document Version Management System";
    private static final Color BLUE = new Color(37, 99, 170);

    private final JTextField usernameField = new JTextField(22);
    private final JPasswordField passwordField = new JPasswordField(22);
    private final AuthService authService = new AuthService();
    private JButton loginButton;

    public LoginFrame() {
        super(APPLICATION_TITLE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(460, 340));
        setContentPane(createContent());
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel createContent() {
        JPanel content = new JPanel(new BorderLayout(12, 20));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));

        JLabel heading = new JLabel(APPLICATION_TITLE, SwingConstants.CENTER);
        heading.setForeground(BLUE);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 21));
        content.add(heading, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBackground(Color.WHITE);
        addField(fields, "Username", usernameField, 0);
        addField(fields, "Password", passwordField, 1);
        content.add(fields, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        footer.setBackground(Color.WHITE);
        loginButton = new JButton("Login");
        JButton clearButton = new JButton("Clear");
        JButton exitButton = new JButton("Exit");
        loginButton.addActionListener(this::attemptLogin);
        clearButton.addActionListener(event -> clearFields());
        exitButton.addActionListener(event -> dispose());
        footer.add(loginButton);
        footer.add(clearButton);
        footer.add(exitButton);
        content.add(footer, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(loginButton);
        return content;
    }

    private static void addField(JPanel panel, String label, JTextField field, int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(8, 5, 8, 5);
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.gridx = 0;
        constraints.gridy = row;
        panel.add(new JLabel(label), constraints);
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        panel.add(field, constraints);
    }

    private void attemptLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getPassword();

        if (username.isEmpty() || password.length == 0) {
            Arrays.fill(password, '\0');
            passwordField.setText("");
            JOptionPane.showMessageDialog(
                    this, "Enter both a username and password.", "Missing sign-in details",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        loginButton.setEnabled(false);
        loginButton.setText("Signing in...");

        new SwingWorker<AuthResult, Void>() {
            @Override
            protected AuthResult doInBackground() {
                try {
                    return authService.authenticate(username, password);
                } finally {
                    Arrays.fill(password, '\0');
                }
            }

            @Override
            protected void done() {
                try {
                    AuthResult result = get();
                    if (result.isSuccess()) {
                        SessionManager.login(result.getUser());
                        dispose();
                        new DashboardFrame(result.getUser()).setVisible(true);
                    } else {
                        passwordField.setText("");
                        JOptionPane.showMessageDialog(
                                LoginFrame.this, result.getMessage(), "Login failed",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception exception) {
                    passwordField.setText("");
                    JOptionPane.showMessageDialog(
                            LoginFrame.this,
                            "A system error occurred. Please try again later.",
                            "Login failed",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    loginButton.setEnabled(true);
                    loginButton.setText("Login");
                }
            }
        }.execute();
    }

    private void clearFields() {
        usernameField.setText("");
        passwordField.setText("");
        usernameField.requestFocusInWindow();
    }
}
