package com.dvms.service;

import com.dvms.config.DatabaseConnection;
import com.dvms.dao.UserDAO;
import com.dvms.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    private final AuthService authService = new AuthService();
    private final UserDAO userDAO = new UserDAO();
    private String testUsername;
    private Long createdUserId;

    static {
        System.setProperty("dvms.db.config", "src/test/resources/db-test.properties");
    }

    private static boolean isDbAvailable() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        testUsername = "test_user_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void tearDown() {
        if (createdUserId != null && isDbAvailable()) {
            try (Connection conn = DatabaseConnection.getConnection()) {
                conn.createStatement().executeUpdate("DELETE FROM users WHERE id = " + createdUserId);
            } catch (SQLException e) {
                // Ignore cleanup errors
            }
        }
    }

    @Test
    void testEmptyAndNullCredentialsRejected() {
        AuthResult r1 = authService.authenticate(null, "password".toCharArray());
        assertFalse(r1.isSuccess());
        assertEquals("Invalid username or password.", r1.getMessage());

        AuthResult r2 = authService.authenticate("   ", "password".toCharArray());
        assertFalse(r2.isSuccess());
        assertEquals("Invalid username or password.", r2.getMessage());

        AuthResult r3 = authService.authenticate("username", null);
        assertFalse(r3.isSuccess());
        assertEquals("Invalid username or password.", r3.getMessage());

        AuthResult r4 = authService.authenticate("username", new char[0]);
        assertFalse(r4.isSuccess());
        assertEquals("Invalid username or password.", r4.getMessage());
    }

    @Test
    void testNonExistentUserReturnsGenericFailure() {
        Assumptions.assumeTrue(isDbAvailable(), "MySQL is required for this integration test");
        AuthResult result = authService.authenticate("non_existent_user_99999", "Password123!".toCharArray());
        assertFalse(result.isSuccess());
        assertEquals("Invalid username or password.", result.getMessage());
        assertNull(result.getUser());
    }

    @Test
    void testSuccessfulAuthentication() throws SQLException {
        Assumptions.assumeTrue(isDbAvailable(), "MySQL is required for this integration test");

        char[] password = "SecretPassword123!".toCharArray();
        String hash = PasswordService.hashPassword(password);

        try (Connection conn = DatabaseConnection.getConnection()) {
            User user = userDAO.create(conn, testUsername, testUsername + "@example.com", hash, "USER", "ACTIVE");
            createdUserId = user.getId();
        }

        AuthResult result = authService.authenticate(testUsername, password);
        assertTrue(result.isSuccess());
        assertNotNull(result.getUser());
        assertEquals(testUsername, result.getUser().getUsername());
        assertNull(result.getMessage());
    }

    @Test
    void testWrongPasswordReturnsGenericFailure() throws SQLException {
        Assumptions.assumeTrue(isDbAvailable(), "MySQL is required for this integration test");

        char[] password = "CorrectPassword123!".toCharArray();
        String hash = PasswordService.hashPassword(password);

        try (Connection conn = DatabaseConnection.getConnection()) {
            User user = userDAO.create(conn, testUsername, testUsername + "@example.com", hash, "USER", "ACTIVE");
            createdUserId = user.getId();
        }

        AuthResult result = authService.authenticate(testUsername, "WrongPassword!".toCharArray());
        assertFalse(result.isSuccess());
        assertEquals("Invalid username or password.", result.getMessage());
        assertNull(result.getUser());
    }

    @Test
    void testInactiveAccountRejected() throws SQLException {
        Assumptions.assumeTrue(isDbAvailable(), "MySQL is required for this integration test");

        char[] password = "CorrectPassword123!".toCharArray();
        String hash = PasswordService.hashPassword(password);

        try (Connection conn = DatabaseConnection.getConnection()) {
            User user = userDAO.create(conn, testUsername, testUsername + "@example.com", hash, "USER", "DISABLED");
            createdUserId = user.getId();
        }

        AuthResult result = authService.authenticate(testUsername, password);
        assertFalse(result.isSuccess());
        assertEquals("This account is not active. Contact an administrator.", result.getMessage());
        assertNull(result.getUser());
    }
}
