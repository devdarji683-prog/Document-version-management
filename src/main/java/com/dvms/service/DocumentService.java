package com.dvms.service;

import com.dvms.config.DatabaseConnection;
import com.dvms.dao.DocumentDAO;
import com.dvms.dao.DocumentVersionDAO;
import com.dvms.model.Document;
import com.dvms.model.DocumentVersion;
import com.dvms.model.User;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class DocumentService {
    private final DocumentDAO documentDAO = new DocumentDAO();
    private final DocumentVersionDAO versionDAO = new DocumentVersionDAO();

    public static class AddDocumentResult {
        private final boolean success;
        private final Document document;
        private final String errorMessage;

        public AddDocumentResult(boolean success, Document document, String errorMessage) {
            this.success = success;
            this.document = document;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return success;
        }

        public Document getDocument() {
            return document;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public static class RestoreVersionResult {
        private final boolean success;
        private final int newVersionNumber;
        private final String errorMessage;

        public RestoreVersionResult(boolean success, int newVersionNumber, String errorMessage) {
            this.success = success;
            this.newVersionNumber = newVersionNumber;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getVersionNumber() {
            return newVersionNumber;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public AddDocumentResult addDocument(
            String title,
            String description,
            Path filePath,
            User currentUser,
            String changeSummary) {
        if (currentUser == null) {
            return new AddDocumentResult(false, null, "An authenticated user is required.");
        }
        if (title == null || title.isBlank()) {
            return new AddDocumentResult(false, null, "Document title is required.");
        }

        FileStorageService.ValidatedFile validatedFile;
        String relativeStoragePath = null;
        String checksum = null;

        try {
            validatedFile = FileStorageService.validateFile(filePath);
            FileStorageService.StorageResult storageResult = FileStorageService.storeFile(
                    validatedFile.getOriginalPath());
            relativeStoragePath = storageResult.getRelativePath();
            checksum = storageResult.getChecksum();
        } catch (StorageException e) {
            return new AddDocumentResult(false, null, "Failed to store file: " + e.getMessage());
        }

        boolean committed = false;
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // 1. Create document
                Document newDocument = documentDAO.create(
                        connection,
                        title.trim(),
                        description != null && !description.isBlank() ? description.trim() : null,
                        currentUser.getId());

                // 2. Create initial version (version 1)
                DocumentVersion newVersion = versionDAO.create(
                        connection,
                        newDocument.getId(),
                        1,
                        relativeStoragePath,
                        checksum,
                        currentUser.getId(),
                        changeSummary != null && !changeSummary.isBlank() ? changeSummary.trim() : "Initial version");

                // 3. Update document current_version pointer
                documentDAO.updateCurrentVersion(connection, newDocument.getId(), newVersion.getId());

                connection.commit();
                committed = true;

                Optional<Document> refreshed = documentDAO.findById(connection, newDocument.getId());
                return new AddDocumentResult(true, refreshed.orElse(newDocument), null);

            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    // Suppress secondary rollback error
                }
                return new AddDocumentResult(false, null, "Database error: " + e.getMessage());
            }
        } catch (SQLException e) {
            return new AddDocumentResult(false, null, "Failed to connect to database: " + e.getMessage());
        } finally {
            if (!committed && relativeStoragePath != null) {
                FileStorageService.cleanupOnFailure(relativeStoragePath);
            }
        }
    }

    public AddDocumentResult createNewVersion(
            long documentId,
            Path filePath,
            User currentUser,
            String changeSummary) {
        if (currentUser == null) {
            return new AddDocumentResult(false, null, "An authenticated user is required.");
        }

        FileStorageService.ValidatedFile validatedFile;
        String relativeStoragePath = null;
        String checksum = null;

        try {
            validatedFile = FileStorageService.validateFile(filePath);
            FileStorageService.StorageResult storageResult = FileStorageService.storeFile(
                    validatedFile.getOriginalPath());
            relativeStoragePath = storageResult.getRelativePath();
            checksum = storageResult.getChecksum();
        } catch (StorageException e) {
            return new AddDocumentResult(false, null, "Failed to store file: " + e.getMessage());
        }

        boolean committed = false;
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // 1. Lock document row for update to serialize concurrent version creations
                Optional<Document> docOpt = documentDAO.findByIdForUpdate(connection, documentId);
                if (docOpt.isEmpty()) {
                    connection.rollback();
                    return new AddDocumentResult(false, null, "Document not found: " + documentId);
                }

                Document document = docOpt.get();
                if (!hasDocumentAccess(document, currentUser)) {
                    connection.rollback();
                    return new AddDocumentResult(false, null, "You do not have permission to update this document.");
                }

                // 2. Read versions while holding row lock
                List<DocumentVersion> existingVersions = versionDAO.findByDocumentId(connection, documentId);
                int nextVersionNumber = 1;
                for (DocumentVersion v : existingVersions) {
                    if (v.getVersionNumber() >= nextVersionNumber) {
                        nextVersionNumber = v.getVersionNumber() + 1;
                    }
                }

                // 3. Insert new version
                DocumentVersion newVersion = versionDAO.create(
                        connection,
                        documentId,
                        nextVersionNumber,
                        relativeStoragePath,
                        checksum,
                        currentUser.getId(),
                        changeSummary != null && !changeSummary.isBlank() ? changeSummary.trim() : "Version " + nextVersionNumber);

                // 4. Update document's current_version pointer
                documentDAO.updateCurrentVersion(connection, documentId, newVersion.getId());

                connection.commit();
                committed = true;

                Optional<Document> refreshed = documentDAO.findById(connection, documentId);
                return new AddDocumentResult(true, refreshed.orElse(document), null);

            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    // Suppress secondary rollback error
                }
                return new AddDocumentResult(false, null, "Database error: " + e.getMessage());
            }
        } catch (SQLException e) {
            return new AddDocumentResult(false, null, "Failed to connect to database: " + e.getMessage());
        } finally {
            if (!committed && relativeStoragePath != null) {
                FileStorageService.cleanupOnFailure(relativeStoragePath);
            }
        }
    }

    public RestoreVersionResult restoreVersion(
            long documentId,
            int sourceVersionNumber,
            User currentUser,
            String changeSummary) throws StorageException, SQLException {
        if (currentUser == null) {
            throw new IllegalArgumentException("An authenticated user is required.");
        }

        String newRelativePath = null;
        boolean committed = false;

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // 1. Lock document row for update
                Optional<Document> docOpt = documentDAO.findByIdForUpdate(connection, documentId);
                if (docOpt.isEmpty()) {
                    connection.rollback();
                    return new RestoreVersionResult(false, -1, "Document not found: " + documentId);
                }

                Document document = docOpt.get();
                if (!hasDocumentAccess(document, currentUser)) {
                    connection.rollback();
                    return new RestoreVersionResult(false, -1, "You do not have permission to restore this document.");
                }

                // 2. Locate source version
                List<DocumentVersion> existingVersions = versionDAO.findByDocumentId(connection, documentId);
                DocumentVersion sourceVersion = null;
                int nextVersionNumber = 1;
                for (DocumentVersion v : existingVersions) {
                    if (v.getVersionNumber() == sourceVersionNumber) {
                        sourceVersion = v;
                    }
                    if (v.getVersionNumber() >= nextVersionNumber) {
                        nextVersionNumber = v.getVersionNumber() + 1;
                    }
                }

                if (sourceVersion == null) {
                    connection.rollback();
                    return new RestoreVersionResult(false, -1, "Source version not found: " + sourceVersionNumber);
                }

                // 3. Resolve source file and verify integrity
                Path sourceFilePath = FileStorageService.resolveStoragePath(sourceVersion.getRelativeStoragePath());
                if (!Files.exists(sourceFilePath)) {
                    connection.rollback();
                    return new RestoreVersionResult(false, -1,
                            "Source file not found in storage: " + sourceVersion.getRelativeStoragePath());
                }

                if (!FileStorageService.verifyChecksum(sourceFilePath, sourceVersion.getSha256Checksum())) {
                    connection.rollback();
                    return new RestoreVersionResult(false, -1,
                            "Source file integrity check failed. The file may be corrupted.");
                }

                // 4. Copy source file to a new storage snapshot
                FileStorageService.StorageResult newStorageResult = FileStorageService.storeFile(sourceFilePath);
                newRelativePath = newStorageResult.getRelativePath();
                String newChecksum = newStorageResult.getChecksum();

                // 5. Create new version record (immutable history preserved)
                String summary = changeSummary != null && !changeSummary.isBlank() ? changeSummary.trim()
                        : "Restored from version " + sourceVersionNumber;

                DocumentVersion newVersion = versionDAO.create(
                        connection,
                        documentId,
                        nextVersionNumber,
                        newRelativePath,
                        newChecksum,
                        currentUser.getId(),
                        summary);

                // 6. Update current version pointer
                documentDAO.updateCurrentVersion(connection, documentId, newVersion.getId());

                connection.commit();
                committed = true;

                return new RestoreVersionResult(true, nextVersionNumber, null);

            } catch (SQLException | StorageException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    // Suppress secondary rollback error
                }
                throw e;
            }
        } finally {
            if (!committed && newRelativePath != null) {
                FileStorageService.cleanupOnFailure(newRelativePath);
            }
        }
    }

    public void deleteDocument(long documentId, User currentUser) throws SQLException {
        if (currentUser == null) {
            throw new IllegalArgumentException("An authenticated user is required.");
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<Document> docOpt = documentDAO.findByIdForUpdate(connection, documentId);
                if (docOpt.isEmpty()) {
                    connection.rollback();
                    throw new SQLException("Document not found: " + documentId);
                }

                Document doc = docOpt.get();
                if (!hasDocumentAccess(doc, currentUser)) {
                    connection.rollback();
                    throw new SQLException("You do not have permission to delete this document.");
                }

                // Enforce immutable version history: reject deleting documents with versions
                List<DocumentVersion> versions = versionDAO.findByDocumentId(connection, documentId);
                if (!versions.isEmpty()) {
                    connection.rollback();
                    throw new SQLException("Cannot delete document with version history. "
                            + "DVMS enforces immutable version records to preserve the audit trail.");
                }

                int rows = documentDAO.deleteById(connection, documentId);
                if (rows == 0) {
                    connection.rollback();
                    throw new SQLException("Document not found or already deleted: " + documentId);
                }

                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    // Suppress
                }
                throw e;
            }
        }
    }

    public void updateDocumentMetadata(long documentId, String title, String description, User currentUser)
            throws SQLException {
        if (currentUser == null) {
            throw new IllegalArgumentException("An authenticated user is required.");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Document title is required.");
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<Document> docOpt = documentDAO.findByIdForUpdate(connection, documentId);
                if (docOpt.isEmpty()) {
                    connection.rollback();
                    throw new SQLException("Document not found: " + documentId);
                }

                Document doc = docOpt.get();
                if (!hasDocumentAccess(doc, currentUser)) {
                    connection.rollback();
                    throw new SQLException("You do not have permission to update this document.");
                }

                documentDAO.updateMetadata(
                        connection,
                        documentId,
                        title.trim(),
                        description != null ? description.trim() : "");

                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    // Suppress
                }
                throw e;
            }
        }
    }

    public List<DocumentVersion> getVersionHistory(long documentId, User currentUser) throws SQLException {
        if (currentUser == null) {
            throw new IllegalArgumentException("An authenticated user is required.");
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            Optional<Document> docOpt = documentDAO.findById(connection, documentId);
            if (docOpt.isEmpty()) {
                throw new SQLException("Document not found: " + documentId);
            }

            Document doc = docOpt.get();
            if (!hasDocumentAccess(doc, currentUser)) {
                throw new SQLException("You do not have permission to view this document.");
            }

            return versionDAO.findByDocumentId(connection, documentId);
        }
    }

    public List<Document> getDocumentsForUser(User currentUser) throws SQLException {
        if (currentUser == null) {
            throw new IllegalArgumentException("An authenticated user is required.");
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            if ("ADMIN".equalsIgnoreCase(currentUser.getRole())) {
                return documentDAO.findAll(connection);
            } else {
                return documentDAO.findByOwner(connection, currentUser.getId());
            }
        }
    }

    public Optional<Document> getDocumentById(long documentId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return documentDAO.findById(connection, documentId);
        }
    }

    public List<Document> getDocumentsByOwner(long userId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return documentDAO.findByOwner(connection, userId);
        }
    }

    public static boolean hasDocumentAccess(Document document, User user) {
        if (document == null || user == null) {
            return false;
        }
        return document.getOwnerUserId() == user.getId() || "ADMIN".equalsIgnoreCase(user.getRole());
    }
}
