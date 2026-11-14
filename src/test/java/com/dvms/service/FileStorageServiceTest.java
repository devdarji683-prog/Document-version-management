package com.dvms.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class FileStorageServiceTest {
    private static final Path TEMP_STORAGE_DIR;

    static {
        try {
            TEMP_STORAGE_DIR = Files.createTempDirectory("dvms-test-storage");
            System.setProperty("dvms.storage.root", TEMP_STORAGE_DIR.toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp storage dir", e);
        }
    }

    @BeforeEach
    void setUp() {
        if (Files.exists(TEMP_STORAGE_DIR)) {
            try {
                Files.walk(TEMP_STORAGE_DIR)
                        .filter(p -> !p.equals(TEMP_STORAGE_DIR))
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                // Ignore cleanup errors
                            }
                        });
            } catch (IOException e) {
                // Ignore cleanup errors
            }
        }
    }

    @AfterEach
    void tearDown() {
        // Keep directory structure clean
    }

    @Test
    void testCalculateChecksum() throws Exception {
        Path testFile = Files.createTempFile("test", ".txt");
        Files.write(testFile, "Hello, World!".getBytes());

        String checksum = FileStorageService.calculateChecksum(testFile);

        assertEquals(64, checksum.length());
        assertTrue(checksum.matches("[0-9a-f]+"));
        assertEquals("dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f", checksum);
    }

    @Test
    void testVerifyChecksumCorrect() throws Exception {
        Path testFile = Files.createTempFile("test", ".txt");
        Files.write(testFile, "Test content".getBytes());

        String expectedChecksum = FileStorageService.calculateChecksum(testFile);
        assertTrue(FileStorageService.verifyChecksum(testFile, expectedChecksum));
    }

    @Test
    void testVerifyChecksumIncorrect() throws Exception {
        Path testFile = Files.createTempFile("test", ".txt");
        Files.write(testFile, "Test content".getBytes());

        String wrongChecksum = "0000000000000000000000000000000000000000000000000000000000000000";
        assertFalse(FileStorageService.verifyChecksum(testFile, wrongChecksum));
    }

    @Test
    void testVerifyChecksumNullExpected() {
        Path testFile = null;
        assertThrows(StorageException.class, () ->
                FileStorageService.verifyChecksum(testFile, null));
    }

    @Test
    void testVerifyChecksumEmptyExpected() {
        Path testFile = null;
        assertThrows(StorageException.class, () ->
                FileStorageService.verifyChecksum(testFile, ""));
    }

    @Test
    void testValidateFileSuccess() throws Exception {
        Path testFile = Files.createTempFile("test", ".txt");
        Files.write(testFile, "Content".getBytes());

        FileStorageService.ValidatedFile validated = FileStorageService.validateFile(testFile);

        assertNotNull(validated);
        assertEquals("txt", getExtension(validated.getOriginalFilename()));
        assertTrue(validated.getSize() > 0);
    }

    @Test
    void testValidateFileNonExistent() {
        Path nonExistent = Paths.get("/non/existent/file.txt");
        StorageException e = assertThrows(StorageException.class, () ->
                FileStorageService.validateFile(nonExistent));
        assertTrue(e.getMessage().contains("File does not exist"));
    }

    @Test
    void testValidateFileEmpty() throws Exception {
        Path emptyFile = Files.createTempFile("empty", ".txt");

        StorageException e = assertThrows(StorageException.class, () ->
                FileStorageService.validateFile(emptyFile));
        assertTrue(e.getMessage().contains("File is empty"));
    }

    @Test
    void testValidateFileUnsupportedExtension() throws Exception {
        Path invalidFile = Files.createTempFile("virus", ".exe");
        Files.write(invalidFile, "malicious-code".getBytes());

        StorageException e = assertThrows(StorageException.class, () ->
                FileStorageService.validateFile(invalidFile));
        assertTrue(e.getMessage().contains("Unsupported file type"));
    }

    @Test
    void testStoreFileSuccess() throws Exception {
        Path testFile = Files.createTempFile("test", ".txt");
        Files.write(testFile, "Test content for storage".getBytes());

        FileStorageService.StorageResult result = FileStorageService.storeFile(testFile);

        assertNotNull(result);
        assertNotNull(result.getRelativePath());
        assertEquals(64, result.getChecksum().length());
        assertTrue(result.getFileSize() > 0);
        assertTrue(result.getRelativePath().endsWith(".txt"), "Stored file relative path must retain extension");

        // The stored file must exist directly at storageRoot.resolve(relativePath)
        Path storedFile = StorageConfig.getStorageRoot().resolve(result.getRelativePath());
        assertTrue(Files.exists(storedFile), "File must exist on disk with its extension");
        assertEquals(result.getChecksum(), FileStorageService.calculateChecksum(storedFile));
    }

    @Test
    void testDeleteFile() throws Exception {
        Path testFile = Files.createTempFile("test", ".txt");
        Files.write(testFile, "Content to be deleted".getBytes());

        FileStorageService.StorageResult result = FileStorageService.storeFile(testFile);
        Path storageRoot = StorageConfig.getStorageRoot();
        Path storedFile = storageRoot.resolve(result.getRelativePath());
        assertTrue(Files.exists(storedFile), "File should exist before deleteFile call");

        // Delete the file
        FileStorageService.deleteFile(result.getRelativePath());

        // Verify it is completely removed from disk
        assertFalse(Files.exists(storedFile), "File must be removed after deleteFile");
    }

    @Test
    void testStoragePathIsSafe() throws Exception {
        Path testFile = Files.createTempFile("test", ".txt");
        Files.write(testFile, "Content".getBytes());

        FileStorageService.StorageResult result = FileStorageService.storeFile(testFile);

        String relativePath = result.getRelativePath();
        assertFalse(relativePath.startsWith("/"));
        assertFalse(relativePath.startsWith("\\"));
        assertFalse(relativePath.contains(".."));
    }

    @Test
    void testResolveStoragePathTraversals() {
        assertThrows(StorageException.class, () -> FileStorageService.resolveStoragePath(null));
        assertThrows(StorageException.class, () -> FileStorageService.resolveStoragePath(""));
        assertThrows(StorageException.class, () -> FileStorageService.resolveStoragePath("/etc/passwd"));
        assertThrows(StorageException.class, () -> FileStorageService.resolveStoragePath("\\Windows\\System32"));
        assertThrows(StorageException.class, () -> FileStorageService.resolveStoragePath("../secret.txt"));
        assertThrows(StorageException.class, () -> FileStorageService.resolveStoragePath("folder/../../secret.txt"));
        assertThrows(StorageException.class, () -> FileStorageService.resolveStoragePath("C:\\secret.txt"));
    }

    @Test
    void testCorruptedFileFailsIntegrityCheck() throws Exception {
        Path testFile = Files.createTempFile("test", ".txt");
        Files.write(testFile, "Original untampered content".getBytes());

        FileStorageService.StorageResult result = FileStorageService.storeFile(testFile);
        Path storedFile = FileStorageService.resolveStoragePath(result.getRelativePath());

        // Tamper with file content
        Files.write(storedFile, "Tampered malicious content".getBytes());

        assertFalse(FileStorageService.verifyChecksum(storedFile, result.getChecksum()));
    }

    @Test
    void testCleanupOnFailure() {
        assertDoesNotThrow(() -> FileStorageService.cleanupOnFailure(null));
        assertDoesNotThrow(() -> FileStorageService.cleanupOnFailure(""));
    }

    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(lastDot + 1) : "";
    }
}
