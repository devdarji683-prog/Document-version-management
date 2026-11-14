package com.dvms.dao;

import com.dvms.model.Document;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DocumentDAO {
    private static final String DOCUMENT_COLUMNS = "id, title, description, owner_user_id, "
            + "current_version_id, created_at, updated_at";

    public Document create(Connection connection, String title, String description, long ownerUserId)
            throws SQLException {
        String sql = "INSERT INTO documents (title, description, owner_user_id) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, title);
            statement.setString(2, description);
            statement.setLong(3, ownerUserId);
            statement.executeUpdate();

            long generatedId;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Creating the document did not return a generated ID.");
                }
                generatedId = keys.getLong(1);
            }
            return findById(connection, generatedId)
                    .orElseThrow(() -> new SQLException("Created document could not be read back."));
        }
    }

    public Optional<Document> findById(Connection connection, long id) throws SQLException {
        String sql = "SELECT " + DOCUMENT_COLUMNS + " FROM documents WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapDocument(results)) : Optional.empty();
            }
        }
    }

    public Optional<Document> findByIdForUpdate(Connection connection, long id) throws SQLException {
        String sql = "SELECT " + DOCUMENT_COLUMNS + " FROM documents WHERE id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapDocument(results)) : Optional.empty();
            }
        }
    }

    public List<Document> findByOwner(Connection connection, long ownerUserId) throws SQLException {
        String sql = "SELECT " + DOCUMENT_COLUMNS
                + " FROM documents WHERE owner_user_id = ? ORDER BY updated_at DESC, id DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownerUserId);
            try (ResultSet results = statement.executeQuery()) {
                List<Document> documents = new ArrayList<>();
                while (results.next()) {
                    documents.add(mapDocument(results));
                }
                return List.copyOf(documents);
            }
        }
    }

    public List<Document> findAll(Connection connection) throws SQLException {
        String sql = "SELECT " + DOCUMENT_COLUMNS + " FROM documents ORDER BY updated_at DESC, id DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            List<Document> documents = new ArrayList<>();
            while (results.next()) {
                documents.add(mapDocument(results));
            }
            return List.copyOf(documents);
        }
    }

    public int updateMetadata(Connection connection, long id, String title, String description)
            throws SQLException {
        String sql = "UPDATE documents SET title = ?, description = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, title);
            statement.setString(2, description);
            statement.setLong(3, id);
            return statement.executeUpdate();
        }
    }

    public int updateCurrentVersion(Connection connection, long documentId, Long versionId) throws SQLException {
        String sql = "UPDATE documents SET current_version_id = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (versionId == null) {
                statement.setNull(1, java.sql.Types.BIGINT);
            } else {
                statement.setLong(1, versionId);
            }
            statement.setLong(2, documentId);
            return statement.executeUpdate();
        }
    }

    public int deleteById(Connection connection, long id) throws SQLException {
        String sql = "DELETE FROM documents WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate();
        }
    }

    private static Document mapDocument(ResultSet results) throws SQLException {
        long currentVersionIdValue = results.getLong("current_version_id");
        Long currentVersionId = results.wasNull() ? null : currentVersionIdValue;
        return new Document(
                results.getLong("id"),
                results.getString("title"),
                results.getString("description"),
                results.getLong("owner_user_id"),
                currentVersionId,
                toLocalDateTime(results.getTimestamp("created_at")),
                toLocalDateTime(results.getTimestamp("updated_at")));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
