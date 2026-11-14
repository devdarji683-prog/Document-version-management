package com.dvms.service;

import com.dvms.config.DatabaseConnection;
import com.dvms.dao.DocumentDAO;
import com.dvms.dao.DocumentVersionDAO;
import com.dvms.dao.UserDAO;
import com.dvms.model.Document;
import com.dvms.model.DocumentVersion;
import com.dvms.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DocumentServiceTest {
    private static final Path TEMP_STORAGE;
    private final DocumentService documentService = new DocumentService();
    private final DocumentDAO documentDAO = new DocumentDAO();
    private final DocumentVersionDAO versionDAO = new DocumentVersionDAO();
    private final UserDAO userDAO = new UserDAO();

    private User ownerUser;
    private User adminUser;
    private User otherUser;
    private final List<Long> createdDocumentIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    static {
        System.setProperty("dvms.db.config", "src/test/resources/db-test.properties");
        try {
            TEMP_STORAGE = Files.createTempDirectory("dvms-service-test-storage");
            System.setProperty("dvms.storage.root", TEMP_STORAGE.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isDbAvailable() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() throws SQLException {
        Assumptions.assumeTrue(isDbAvailable(), "MySQL isolated test DB dvms_test is required");

        String uid = UUID.randomUUID().toString().substring(0, 8);
        try (Connection conn = DatabaseConnection.getConnection()) {
            ownerUser = userDAO.create(conn, "owner_" + uid, "owner_" + uid + "@example.com",
                    "$2a$12$cPgSLHXNyhmNSJby5Oi.nOnoTroScsxALyXUh3UVvp3l0J24CCbo6", "USER", "ACTIVE");
            createdUserIds.add(ownerUser.getId());

            adminUser = userDAO.create(conn, "admin_" + uid, "admin_" + uid + "@example.com",
                    "$2a$12$cPgSLHXNyhmNSJby5Oi.nOnoTroScsxALyXUh3UVvp3l0J24CCbo6", "ADMIN", "ACTIVE");
            createdUserIds.add(adminUser.getId());

            otherUser = userDAO.create(conn, "other_" + uid, "other_" + uid + "@example.com",
                    "$2a$12$cPgSLHXNyhmNSJby5Oi.nOnoTroScsxALyXUh3UVvp3l0J24CCbo6", "USER", "ACTIVE");
            createdUserIds.add(otherUser.getId());
        }
    }

    @AfterEach
    void tearDown() {
        if (!isDbAvailable()) {
            return;
        }

        // Clean up documents and users in test database
        // Note: document_versions cannot be deleted due to MySQL immutability trigger
        // In the isolated test database dvms_test, tests create unique IDs so they don't interfere
    }

    private Path createTestFile(String name, String content) throws IOException {
        Path file = Files.createTempFile(name, ".txt");
        Files.writeString(file, content);
        return file;
    }

    @Test
    void testAddDocumentWithInitialVersion() throws Exception {
        Path file = createTestFile("doc1", "Initial content for document 1");
        DocumentService.AddDocumentResult result = documentService.addDocument(
                "Project Plan", "Architecture specifications", file, ownerUser, "First draft");

        assertTrue(result.isSuccess(), "Add document should succeed: " + result.getErrorMessage());
        assertNotNull(result.getDocument());
        createdDocumentIds.add(result.getDocument().getId());

        Document doc = result.getDocument();
        assertNotNull(doc.getCurrentVersionId(), "Current version ID must be set");

        try (Connection conn = DatabaseConnection.getConnection()) {
            List<DocumentVersion> versions = versionDAO.findByDocumentId(conn, doc.getId());
            assertEquals(1, versions.size());
            DocumentVersion v1 = versions.get(0);
            assertEquals(1, v1.getVersionNumber());
            assertEquals(ownerUser.getId(), v1.getAuthorUserId());
            assertTrue(v1.getRelativeStoragePath().endsWith(".txt"), "Storage path must retain .txt extension");

            Path stored = FileStorageService.resolveStoragePath(v1.getRelativeStoragePath());
            assertTrue(Files.exists(stored), "Stored file must exist on disk");
            assertEquals(v1.getSha256Checksum(), FileStorageService.calculateChecksum(stored));
        }
    }

    @Test
    void testCreateNewVersionIncrementsVersionNumber() throws Exception {
        Path f1 = createTestFile("v1", "Version 1 content");
        DocumentService.AddDocumentResult addResult = documentService.addDocument(
                "Design Spec", "System design", f1, ownerUser, "v1");
        assertTrue(addResult.isSuccess());
        long docId = addResult.getDocument().getId();
        createdDocumentIds.add(docId);

        Path f2 = createTestFile("v2", "Version 2 updated content");
        DocumentService.AddDocumentResult v2Result = documentService.createNewVersion(
                docId, f2, ownerUser, "Updated with v2 specs");

        assertTrue(v2Result.isSuccess(), "Create new version should succeed: " + v2Result.getErrorMessage());

        try (Connection conn = DatabaseConnection.getConnection()) {
            List<DocumentVersion> versions = versionDAO.findByDocumentId(conn, docId);
            assertEquals(2, versions.size());
            assertEquals(2, versions.get(0).getVersionNumber(), "Newest version should be 2");
            assertEquals(1, versions.get(1).getVersionNumber(), "First version should be 1");

            Document doc = documentDAO.findById(conn, docId).orElseThrow();
            assertEquals(versions.get(0).getId(), doc.getCurrentVersionId(),
                    "Current version pointer must point to version 2");
        }
    }

    @Test
    void testRestoreVersionCreatesNewVersionPreservingHistory() throws Exception {
        Path f1 = createTestFile("v1", "Content of version 1");
        DocumentService.AddDocumentResult addResult = documentService.addDocument(
                "Changelog", "Changelog doc", f1, ownerUser, "v1");
        long docId = addResult.getDocument().getId();
        createdDocumentIds.add(docId);

        Path f2 = createTestFile("v2", "Content of version 2 (bad edit)");
        documentService.createNewVersion(docId, f2, ownerUser, "v2");

        Path f3 = createTestFile("v3", "Content of version 3");
        documentService.createNewVersion(docId, f3, ownerUser, "v3");

        // Now restore version 1
        DocumentService.RestoreVersionResult restoreResult = documentService.restoreVersion(
                docId, 1, ownerUser, "Rollback to version 1");

        assertTrue(restoreResult.isSuccess(), "Restoration should succeed");
        assertEquals(4, restoreResult.getVersionNumber(), "Restoring v1 when 3 versions exist must create version 4");

        try (Connection conn = DatabaseConnection.getConnection()) {
            List<DocumentVersion> versions = versionDAO.findByDocumentId(conn, docId);
            assertEquals(4, versions.size(), "History must contain all 4 versions");

            DocumentVersion v4 = versions.get(0);
            DocumentVersion v1 = versions.get(3);
            assertEquals(v1.getSha256Checksum(), v4.getSha256Checksum(),
                    "Restored version 4 must match checksum of version 1");

            Document doc = documentDAO.findById(conn, docId).orElseThrow();
            assertEquals(v4.getId(), doc.getCurrentVersionId(), "Current version must now point to version 4");
        }
    }

    @Test
    void testRestoreFailsOnCorruptedSourceFile() throws Exception {
        Path f1 = createTestFile("v1", "Clean content");
        DocumentService.AddDocumentResult addResult = documentService.addDocument(
                "Tamper Doc", "Testing corruption", f1, ownerUser, "v1");
        long docId = addResult.getDocument().getId();
        createdDocumentIds.add(docId);

        try (Connection conn = DatabaseConnection.getConnection()) {
            List<DocumentVersion> versions = versionDAO.findByDocumentId(conn, docId);
            Path storedPath = FileStorageService.resolveStoragePath(versions.get(0).getRelativeStoragePath());

            // Corrupt file on disk
            Files.writeString(storedPath, "Corrupted content altered by attacker");
        }

        DocumentService.RestoreVersionResult restoreResult = documentService.restoreVersion(
                docId, 1, ownerUser, "Try restore");

        assertFalse(restoreResult.isSuccess(), "Restoration must fail on corrupted file");
        assertTrue(restoreResult.getErrorMessage().contains("integrity check failed"));
    }

    @Test
    void testRestoreFailsOnMissingSourceFile() throws Exception {
        Path f1 = createTestFile("v1", "Will be deleted");
        DocumentService.AddDocumentResult addResult = documentService.addDocument(
                "Missing Doc", "Testing missing file", f1, ownerUser, "v1");
        long docId = addResult.getDocument().getId();
        createdDocumentIds.add(docId);

        try (Connection conn = DatabaseConnection.getConnection()) {
            List<DocumentVersion> versions = versionDAO.findByDocumentId(conn, docId);
            Path storedPath = FileStorageService.resolveStoragePath(versions.get(0).getRelativeStoragePath());
            Files.delete(storedPath);
        }

        DocumentService.RestoreVersionResult restoreResult = documentService.restoreVersion(
                docId, 1, ownerUser, "Try restore");

        assertFalse(restoreResult.isSuccess(), "Restoration must fail on missing file");
        assertTrue(restoreResult.getErrorMessage().contains("not found in storage"));
    }

    @Test
    void testOwnershipEnforcementRejectsUnauthorizedUsers() throws Exception {
        Path f1 = createTestFile("v1", "Confidential text");
        DocumentService.AddDocumentResult addResult = documentService.addDocument(
                "Private Project", "Secret data", f1, ownerUser, "v1");
        long docId = addResult.getDocument().getId();
        createdDocumentIds.add(docId);

        // otherUser attempts update metadata
        assertThrows(SQLException.class, () ->
                documentService.updateDocumentMetadata(docId, "Hacked Title", "Hacked Desc", otherUser));

        // otherUser attempts create new version
        Path f2 = createTestFile("v2", "Unauthorized content");
        DocumentService.AddDocumentResult v2Res = documentService.createNewVersion(docId, f2, otherUser, "v2");
        assertFalse(v2Res.isSuccess());
        assertTrue(v2Res.getErrorMessage().contains("do not have permission"));

        // otherUser attempts restore
        DocumentService.RestoreVersionResult rRes = documentService.restoreVersion(docId, 1, otherUser, "restore");
        assertFalse(rRes.isSuccess());
        assertTrue(rRes.getErrorMessage().contains("do not have permission"));

        // otherUser attempts view version history
        assertThrows(SQLException.class, () ->
                documentService.getVersionHistory(docId, otherUser));

        // otherUser attempts delete
        assertThrows(SQLException.class, () ->
                documentService.deleteDocument(docId, otherUser));
    }

    @Test
    void testAdminAuthorizationAllowsManagement() throws Exception {
        Path f1 = createTestFile("v1", "User doc");
        DocumentService.AddDocumentResult addResult = documentService.addDocument(
                "Staff Notes", "Original notes", f1, ownerUser, "v1");
        long docId = addResult.getDocument().getId();
        createdDocumentIds.add(docId);

        // admin updates metadata
        assertDoesNotThrow(() ->
                documentService.updateDocumentMetadata(docId, "Admin Updated Title", "Admin updated desc", adminUser));

        // admin creates new version
        Path f2 = createTestFile("v2", "Admin revised version");
        DocumentService.AddDocumentResult v2Res = documentService.createNewVersion(docId, f2, adminUser, "Admin revision");
        assertTrue(v2Res.isSuccess(), "Admin must be authorized to create versions");

        // admin views history
        List<DocumentVersion> history = documentService.getVersionHistory(docId, adminUser);
        assertEquals(2, history.size());

        // admin restores version
        DocumentService.RestoreVersionResult rRes = documentService.restoreVersion(docId, 1, adminUser, "Admin restore");
        assertTrue(rRes.isSuccess(), "Admin must be authorized to restore versions");
        assertEquals(3, rRes.getVersionNumber());
    }

    @Test
    void testDeletionPolicyRejectsDeletingDocumentsWithVersions() throws Exception {
        Path f1 = createTestFile("v1", "Doc with history");
        DocumentService.AddDocumentResult addResult = documentService.addDocument(
                "Audit Record", "Permanent record", f1, ownerUser, "v1");
        long docId = addResult.getDocument().getId();
        createdDocumentIds.add(docId);

        // Deleting document with version history must be rejected
        SQLException ex = assertThrows(SQLException.class, () ->
                documentService.deleteDocument(docId, ownerUser));
        assertTrue(ex.getMessage().contains("Cannot delete document with version history"));

        // Document still exists
        try (Connection conn = DatabaseConnection.getConnection()) {
            assertTrue(documentDAO.findById(conn, docId).isPresent());
        }
    }

    @Test
    void testConcurrentVersionCreationSafety() throws Exception {
        Path f1 = createTestFile("v1", "Base content");
        DocumentService.AddDocumentResult addResult = documentService.addDocument(
                "Concurrent Test", "Testing concurrency", f1, ownerUser, "v1");
        long docId = addResult.getDocument().getId();
        createdDocumentIds.add(docId);

        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Path file = createTestFile("concurrent-" + index, "Concurrent content " + index);
                    DocumentService.AddDocumentResult res = documentService.createNewVersion(
                            docId, file, ownerUser, "Concurrent update " + index);
                    if (res.isSuccess()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Log
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Concurrent operations must complete within 30s");
        executor.shutdown();

        assertEquals(threadCount, successCount.get(), "All concurrent version creations should succeed due to row locking");

        try (Connection conn = DatabaseConnection.getConnection()) {
            List<DocumentVersion> versions = versionDAO.findByDocumentId(conn, docId);
            assertEquals(1 + threadCount, versions.size(), "All versions should be stored consecutively");

            // Verify unique consecutive version numbers: 4, 3, 2, 1
            for (int i = 0; i < versions.size(); i++) {
                assertEquals(versions.size() - i, versions.get(i).getVersionNumber());
            }
        }
    }

    @Test
    void testFailedTransactionCleansUpFile() throws Exception {
        Path file = createTestFile("orphan", "Content that will fail");

        // documentId 9999999 does not exist, so transaction will fail
        DocumentService.AddDocumentResult res = documentService.createNewVersion(
                9999999L, file, ownerUser, "Fail version");

        assertFalse(res.isSuccess());

        // Verify storage directory has no lingering files created for this failed attempt
        try (var stream = Files.walk(TEMP_STORAGE)) {
            boolean hasLingering = stream.anyMatch(p -> {
                try {
                    return Files.isRegularFile(p) && Files.readString(p).contains("Content that will fail");
                } catch (IOException e) {
                    return false;
                }
            });
            assertFalse(hasLingering, "Failed transaction must clean up stored file");
        }
    }
}

