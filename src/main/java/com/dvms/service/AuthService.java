package com.dvms.service;

import com.dvms.config.DatabaseConnection;
import com.dvms.dao.UserDAO;
import com.dvms.model.User;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AuthService {
    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());
    private static final String GENERIC_FAILURE = "Invalid username or password.";
    private static final String ACCOUNT_INACTIVE = "This account is not active. Contact an administrator.";
    // Valid pre-computed BCrypt hash (work factor 12) for timing mitigation
    private static final String DUMMY_HASH =
            "$2a$12$cPgSLHXNyhmNSJby5Oi.nOnoTroScsxALyXUh3UVvp3l0J24CCbo6";

    private final UserDAO userDAO = new UserDAO();

    public AuthResult authenticate(String username, char[] password) {
        if (username == null || username.isBlank() || password == null || password.length == 0) {
            return AuthResult.failure(GENERIC_FAILURE);
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            Optional<User> found = userDAO.findByUsername(connection, username.trim());
            if (found.isEmpty()) {
                dummyVerify(password);
                return AuthResult.failure(GENERIC_FAILURE);
            }

            User user = found.get();
            String storedHash = user.getPasswordHash();

            if (!PasswordService.isValidBCryptHash(storedHash)) {
                dummyVerify(password);
                return AuthResult.failure(GENERIC_FAILURE);
            }

            if (!PasswordService.verifyPassword(password, storedHash)) {
                return AuthResult.failure(GENERIC_FAILURE);
            }

            if (!"ACTIVE".equals(user.getAccountStatus())) {
                return AuthResult.failure(ACCOUNT_INACTIVE);
            }

            return AuthResult.success(user);
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "Database error during authentication: {0}", exception.getMessage());
            return AuthResult.failure("A system error occurred. Please try again later.");
        }
    }

    private static void dummyVerify(char[] password) {
        PasswordService.verifyPassword(password, DUMMY_HASH);
    }
}
