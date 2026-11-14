package com.dvms.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public final class StorageConfig {
    private static final String DEFAULT_STORAGE_DIR = "document-storage";
    private static final String ENV_STORAGE_ROOT = "DVMS_STORAGE_ROOT";
    private static final String SYS_PROP_STORAGE_ROOT = "dvms.storage.root";

    private StorageConfig() {
    }

    public static Path getStorageRoot() {
        // Check system property first (for testing and runtime override)
        String propPath = System.getProperty(SYS_PROP_STORAGE_ROOT);
        if (propPath != null && !propPath.isBlank()) {
            return Paths.get(propPath).toAbsolutePath().normalize();
        }

        // Then check environment variable
        String envPath = System.getenv(ENV_STORAGE_ROOT);
        if (envPath != null && !envPath.isBlank()) {
            return Paths.get(envPath).toAbsolutePath().normalize();
        }

        // Default: document-storage directory under current working directory
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        return cwd.resolve(DEFAULT_STORAGE_DIR).normalize();
    }

    public static Path ensureStorageRootExists() throws StorageException {
        Path root = getStorageRoot();

        try {
            if (Files.notExists(root)) {
                Files.createDirectories(root);
            } else if (!Files.isDirectory(root)) {
                throw new StorageException(
                    "Storage path exists but is not a directory: " + root);
            }
        } catch (java.io.IOException e) {
            throw new StorageException(
                "Failed to create or access storage directory: " + root, e);
        }

        return root;
    }

    public static String[] getSupportedExtensions() {
        return new String[] {
            ".txt", ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".rtf", ".odt", ".ods", ".odp", ".csv", ".md", ".json", ".xml",
            ".log", ".sql", ".java", ".py", ".js", ".ts", ".c", ".cpp", ".h",
            ".php", ".rb", ".go", ".rs", ".swift", ".kt", ".scala", ".sh",
            ".bat", ".cmd", ".yml", ".yaml", ".ini", ".cfg", ".conf"
        };
    }

    public static boolean isSupportedExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        for (String ext : getSupportedExtensions()) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    public static String getDisplayFilename(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int lastSeparator = Math.max(
            originalFilename.lastIndexOf('/'),
            originalFilename.lastIndexOf('\\')
        );
        return lastSeparator >= 0 ? originalFilename.substring(lastSeparator + 1) : originalFilename;
    }
}
