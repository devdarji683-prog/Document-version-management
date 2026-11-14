package com.dvms.ui;

import com.dvms.model.User;
import com.dvms.service.SessionManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

public final class DashboardFrame extends JFrame {
    private static final String APPLICATION_TITLE = "Document Version Management System";
    private static final Color BLUE = new Color(37, 99, 170);

    private final DocumentPanel documentPanel;

    public DashboardFrame(User user) {
        super(APPLICATION_TITLE);
        if (user == null) {
            throw new IllegalArgumentException("An authenticated user is required.");
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1050, 650);
        setMinimumSize(new java.awt.Dimension(800, 500));
        setLocationRelativeTo(null);

        documentPanel = new DocumentPanel(user, this);
        setLayout(new BorderLayout());
        add(createHeader(user), BorderLayout.NORTH);
        add(createNavigation(), BorderLayout.WEST);
        add(documentPanel, BorderLayout.CENTER);
    }

    private JPanel createHeader(User user) {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(BLUE);
        header.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 18, 12, 18));
        JLabel title = new JLabel(APPLICATION_TITLE);
        title.setForeground(Color.WHITE);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        JLabel welcome = new JLabel("Welcome, " + user.getUsername());
        welcome.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);
        header.add(welcome, BorderLayout.EAST);
        return header;
    }

    private JPanel createNavigation() {
        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 12));
        navigation.setBackground(new Color(239, 245, 252));
        navigation.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 8, 0, 8));
        JButton documentsButton = new JButton("Documents");
        JButton historyButton = new JButton("Version History");
        JButton logoutButton = new JButton("Logout");
        JButton exitButton = new JButton("Exit");
        documentsButton.addActionListener(event -> documentPanel.refreshDocuments());
        historyButton.addActionListener(event -> documentPanel.showVersionHistoryForSelection());
        logoutButton.addActionListener(event -> logout());
        exitButton.addActionListener(event -> {
            int choice = JOptionPane.showConfirmDialog(
                    this, "Exit the application?", "Exit", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                dispose();
            }
        });
        navigation.add(documentsButton);
        navigation.add(historyButton);
        navigation.add(logoutButton);
        navigation.add(exitButton);
        return navigation;
    }

    private void logout() {
        SessionManager.logout();
        dispose();
        new LoginFrame().setVisible(true);
    }
}
