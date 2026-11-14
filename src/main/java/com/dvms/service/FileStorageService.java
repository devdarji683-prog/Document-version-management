package com.dvms.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;

public final class FileStorageService {
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final long MAX_FILE_SIZE = 1024L * 1024L * 1024L; // 1 GB

    private FileStorageService() {
    }

    public static class StorageResult {
        private final String relativePath;
        private final String checksum;
        private final long fileSize;

        public StorageResult(String relativePath, String checksum, long fileSize) {
            this.relativePath = relativePath;
            this.checksum = checksum;
            this.fileSize = fileSize;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public String getChecksum() {
            return checksum;
        }

        public long getFileSize() {
            return fileSize;
        }
    }

    public static class ValidatedFile {
        private final Path originalPath;
        private final String originalFilename;
        private final long size;

        public ValidatedFile(Path originalPath, String originalFilename, long size) {
            this.originalPath = originalPath;
            this.originalFilename = originalFilename;
            this.size = size;
        }

        public Path getOriginalPath() {
            return originalPath;
        }

        public String getOriginalFilename() {
            return originalFilename;
        }

        public long getSize() {
            return size;
        }
    }

    public static ValidatedFile validateFile(Path filePath) throws StorageException {
        if (filePath == null) {
            throw new StorageException("File path cannot be null.");
        }

        try {
            if (!Files.exists(filePath)) {
                throw new StorageException("File does not exist: " + filePath);
            }

            if (!Files.isRegularFile(filePath)) {
                throw new StorageException("Path is not a regular file: " + filePath);
            }

            if (!Files.isReadable(filePath)) {
                throw new StorageException("File is not readable: " + filePath);
            }

            long size = Files.size(filePath);
            if (size == 0) {
                throw new StorageException("File is empty: " + filePath);
            }

            if (size > MAX_FILE_SIZE) {
                throw new StorageException("File too large (>1GB): " + filePath);
            }

            String filename = filePath.getFileName().toString();
            if (!StorageConfig.isSupportedExtension(filename)) {
                throw new StorageException("Unsupported file type: " + filename);
            }

            return new ValidatedFile(filePath, filename, size);

        } catch (IOException e) {
            throw new StorageException("Error reading file: " + filePath, e);
        }
    }

    public static StorageResult storeFile(Path validatedPath) throws StorageException {
        if (validatedPath == null) {
            throw new StorageException("File path cannot be null.");
        }

        Path storageRoot = StorageConfig.ensureStorageRootExists();

        String originalFilename = validatedPath.getFileName().toString();
        String originalExtension = "";
        int lastDot = originalFilename.lastIndexOf('.');
        if (lastDot >= 0) {
            originalExtension = originalFilename.substring(lastDot).toLowerCase(Locale.ROOT);
        }

        String storageFilename = UUID.randomUUID().toString() + originalExtension;
        Path targetPath = storageRoot.resolve(storageFilename).normalize();

        // Security: verify the normalized path is strictly within storage root
        if (!targetPath.startsWith(storageRoot) || targetPath.equals(storageRoot)) {
            throw new StorageException("Invalid file path: path traversal detected.");
        }

        try {
            Files.copy(validatedPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            String checksum = calculateChecksum(targetPath);
            long fileSize = Files.size(targetPath);

            // Stored relative path retains intended extension and matches file on disk exactly
            return new StorageResult(storageFilename, checksum, fileSize);

        } catch (IOException e) {
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException cleanupException) {
                // Log but don't mask original exception
            }
            throw new StorageException("Failed to store file: " + validatedPath, e);
        }
    }

    public static Path resolveStoragePath(String relativePath) throws StorageException {
        if (relativePath == null || relativePath.isBlank()) {
            throw new StorageException("Relative storage path cannot be empty.");
        }

        // Schema constraint chk_document_versions_relative_path enforcement:
        // No leading slash, no backslashes, no drive letter, no parent traversal
        if (relativePath.startsWith("/") || relativePath.startsWith("\\")
                || relativePath.contains("\\")
                || relativePath.matches("^[A-Za-z]:.*")
                || relativePath.contains("..")) {
            throw new StorageException("Path traversal or illegal relative path detected: " + relativePath);
        }

        Path storageRoot = StorageConfig.getStorageRoot();
        Path resolved = storageRoot.resolve(relativePath).normalize();

        if (!resolved.startsWith(storageRoot) || resolved.equals(storageRoot)) {
            throw new StorageException("Resolved path escapes storage root: " + relativePath);
        }

        return resolved;
    }

    public static String calculateChecksum(Path filePath) throws StorageException {
        if (filePath == null) {
            throw new StorageException("File path cannot be null.");
        }
        if (!Files.isRegularFile(filePath)) {
            throw new StorageException("Path is not a regular file: " + filePath);
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            try (InputStream fis = Files.newInputStream(filePath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            throw new StorageException("SHA-256 algorithm not available.", e);
        } catch (IOException e) {
            throw new StorageException("Failed to read file for checksum: " + filePath, e);
        }
    }

    public static boolean verifyChecksum(Path filePath, String expectedChecksum) throws StorageException {
        if (expectedChecksum == null || !expectedChecksum.matches("^[0-9a-fA-F]{64}$")) {
            throw new StorageException("Expected checksum must be 64 hexadecimal characters.");
        }

        String actualChecksum = calculateChecksum(filePath);
        byte[] actualBytes = actualChecksum.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        byte[] expectedBytes = expectedChecksum.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);

        return MessageDigest.isEqual(actualBytes, expectedBytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    public static void deleteFile(String relativePath) throws StorageException {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }

        Path filePath = resolveStoragePath(relativePath);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new StorageException("Failed to delete file: " + relativePath, e);
        }
    }

    public static void cleanupOnFailure(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }

        try {
            deleteFile(relativePath);
        } catch (Exception e) {
            // Best effort cleanup after failure
        }
    }
}
