package com.dvms.dao;

import com.dvms.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

public final class UserDAO {
    private static final String USER_COLUMNS = "id, username, email, password_hash, role, "
            + "account_status, created_at, updated_at";

    public User create(
            Connection connection,
            String username,
            String email,
            String passwordHash,
            String role,
            String accountStatus) throws SQLException {
        String sql = "INSERT INTO users (username, email, password_hash, role, account_status) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, username);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            statement.setString(4, role);
            statement.setString(5, accountStatus);
            statement.executeUpdate();

            long generatedId;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Creating the user did not return a generated ID.");
                }
                generatedId = keys.getLong(1);
            }
            return findById(connection, generatedId)
                    .orElseThrow(() -> new SQLException("Created user could not be read back."));
        }
    }

    public Optional<User> findById(Connection connection, long id) throws SQLException {
        String sql = "SELECT " + USER_COLUMNS + " FROM users WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapUser(results)) : Optional.empty();
            }
        }
    }

    public Optional<User> findByUsername(Connection connection, String username) throws SQLException {
        String sql = "SELECT " + USER_COLUMNS + " FROM users WHERE username = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapUser(results)) : Optional.empty();
            }
        }
    }

    public int updateAccountStatus(Connection connection, long id, String accountStatus) throws SQLException {
        String sql = "UPDATE users SET account_status = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountStatus);
            statement.setLong(2, id);
            return statement.executeUpdate();
        }
    }

    private static User mapUser(ResultSet results) throws SQLException {
        return new User(
                results.getLong("id"),
                results.getString("username"),
                results.getString("email"),
                results.getString("password_hash"),
                results.getString("role"),
                results.getString("account_status"),
                toLocalDateTime(results.getTimestamp("created_at")),
                toLocalDateTime(results.getTimestamp("updated_at")));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}