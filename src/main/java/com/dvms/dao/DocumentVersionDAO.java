package com.dvms.dao;

import com.dvms.model.DocumentVersion;
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

public final class DocumentVersionDAO {
    private static final String VERSION_COLUMNS = "id, document_id, version_number, relative_storage_path, "
            + "LOWER(HEX(sha256_checksum)) AS sha256_checksum, author_user_id, change_summary, created_at";

    public DocumentVersion create(
            Connection connection,
            long documentId,
            int versionNumber,
            String relativeStoragePath,
            String sha256Checksum,
            long authorUserId,
            String changeSummary) throws SQLException {
        if (versionNumber < 1) {
            throw new IllegalArgumentException("Version numbers must start at 1.");
        }
        if (sha256Checksum == null || !sha256Checksum.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("SHA-256 checksum must contain exactly 64 hexadecimal characters.");
        }

        String sql = "INSERT INTO document_versions (document_id, version_number, relative_storage_path, "
                + "sha256_checksum, author_user_id, change_summary) "
                + "VALUES (?, ?, ?, UNHEX(?), ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, documentId);
            statement.setInt(2, versionNumber);
            statement.setString(3, relativeStoragePath);
            statement.setString(4, sha256Checksum);
            statement.setLong(5, authorUserId);
            statement.setString(6, changeSummary);
            statement.executeUpdate();

            long generatedId;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Creating the document version did not return a generated ID.");
                }
                generatedId = keys.getLong(1);
            }
            return findById(connection, generatedId)
                    .orElseThrow(() -> new SQLException("Created document version could not be read back."));
        }
    }

    public Optional<DocumentVersion> findById(Connection connection, long id) throws SQLException {
        String sql = "SELECT " + VERSION_COLUMNS + " FROM document_versions WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapVersion(results)) : Optional.empty();
            }
        }
    }

    public List<DocumentVersion> findByDocumentId(Connection connection, long documentId) throws SQLException {
        String sql = "SELECT " + VERSION_COLUMNS
                + " FROM document_versions WHERE document_id = ? ORDER BY version_number DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, documentId);
            try (ResultSet results = statement.executeQuery()) {
                List<DocumentVersion> versions = new ArrayList<>();
                while (results.next()) {
                    versions.add(mapVersion(results));
                }
                return List.copyOf(versions);
            }
        }
    }

    private static DocumentVersion mapVersion(ResultSet results) throws SQLException {
        return new DocumentVersion(
                results.getLong("id"),
                results.getLong("document_id"),
                results.getInt("version_number"),
                results.getString("relative_storage_path"),
                results.getString("sha256_checksum"),
                results.getLong("author_user_id"),
                results.getString("change_summary"),
                toLocalDateTime(results.getTimestamp("created_at")));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}