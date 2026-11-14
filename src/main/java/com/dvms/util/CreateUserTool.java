package com.dvms.util;

import com.dvms.config.DatabaseConnection;
import com.dvms.dao.UserDAO;
import com.dvms.model.User;
import com.dvms.service.PasswordService;
import java.io.Console;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Scanner;

public final class CreateUserTool {
    private CreateUserTool() {
    }

    public static void main(String[] args) {
        System.out.println("=== Document Version Management System — Create User ===");
        System.out.println();

        Console console = System.console();
        Scanner scanner = console == null ? new Scanner(System.in) : null;

        String username = readLine("Username: ", console, scanner);
        if (username == null || username.isBlank()) {
            System.err.println("Username must not be empty.");
            System.exit(1);
        }

        String email = readLine("Email: ", console, scanner);
        if (email == null || email.isBlank()) {
            System.err.println("Email must not be empty.");
            System.exit(1);
        }

        char[] password = readPassword("Password: ", console, scanner);
        if (password == null || password.length == 0) {
            System.err.println("Password must not be empty.");
            System.exit(1);
        }

        char[] confirmPassword = readPassword("Confirm password: ", console, scanner);
        if (!Arrays.equals(password, confirmPassword)) {
            Arrays.fill(password, '\0');
            if (confirmPassword != null) {
                Arrays.fill(confirmPassword, '\0');
            }
            System.err.println("Passwords do not match.");
            System.exit(1);
        }
        if (confirmPassword != null) {
            Arrays.fill(confirmPassword, '\0');
        }

        String role = readLine("Role (USER or ADMIN) [USER]: ", console, scanner);
        if (role == null || role.isBlank()) {
            role = "USER";
        }
        role = role.trim().toUpperCase();
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            Arrays.fill(password, '\0');
            System.err.println("Role must be USER or ADMIN.");
            System.exit(1);
        }

        try {
            String passwordHash = PasswordService.hashPassword(password);
            Arrays.fill(password, '\0');

            try (Connection connection = DatabaseConnection.getConnection()) {
                UserDAO userDAO = new UserDAO();

                Optional<User> existing = userDAO.findByUsername(connection, username.trim());
                if (existing.isPresent()) {
                    System.err.println("A user with username '" + username.trim() + "' already exists.");
                    System.exit(1);
                }

                User created = userDAO.create(connection, username.trim(), email.trim(),
                        passwordHash, role, "ACTIVE");
                System.out.println();
                System.out.println("User created successfully:");
                System.out.println("  ID:       " + created.getId());
                System.out.println("  Username: " + created.getUsername());
                System.out.println("  Email:    " + created.getEmail());
                System.out.println("  Role:     " + created.getRole());
                System.out.println("  Status:   " + created.getAccountStatus());
            }
        } catch (SQLException exception) {
            System.err.println("Database error: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static String readLine(String prompt, Console console, Scanner scanner) {
        if (console != null) {
            return console.readLine(prompt);
        }
        System.out.print(prompt);
        return scanner.hasNextLine() ? scanner.nextLine() : null;
    }

    private static char[] readPassword(String prompt, Console console, Scanner scanner) {
        if (console != null) {
            return console.readPassword(prompt);
        }
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            return null;
        }
        return scanner.nextLine().toCharArray();
    }
}
