package com.dvms.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConfigTest {

    @AfterEach
    void tearDown() {
        System.clearProperty("dvms.db.config");
        System.clearProperty("db.url");
        System.clearProperty("db.name");
        System.clearProperty("db.host");
        System.clearProperty("db.port");
        System.clearProperty("db.user");
        System.clearProperty("db.password");
    }

    @Test
    void testLoadExistingValidConfig() throws IOException {
        Path tempFile = Files.createTempFile("db-test", ".properties");
        Files.writeString(tempFile, "db.url=jdbc:mysql://localhost:3306/test\ndb.username=testuser\ndb.password=testpass\n");
        System.setProperty("dvms.db.config", tempFile.toString());

        Properties props = DatabaseConfig.load();
        assertEquals("jdbc:mysql://localhost:3306/test", props.getProperty("db.url"));
        assertEquals("testuser", props.getProperty("db.username"));
        assertEquals("testpass", props.getProperty("db.password"));
    }

    @Test
    void testMissingKeysThrowsException() throws IOException {
        Path tempFile = Files.createTempFile("db-missing", ".properties");
        Files.writeString(tempFile, "db.url=jdbc:mysql://localhost:3306/test\n");
        System.setProperty("dvms.db.config", tempFile.toString());

        IOException ex = assertThrows(IOException.class, DatabaseConfig::load);
        assertTrue(ex.getMessage().contains("Missing required database configuration keys"));
        assertTrue(ex.getMessage().contains("db.username"));
        assertTrue(ex.getMessage().contains("db.password"));
    }

    @Test
    void testNonExistentConfigFileThrowsException() {
        System.setProperty("dvms.db.config", "non_existent_file.properties");
        IOException ex = assertThrows(IOException.class, DatabaseConfig::load);
        assertTrue(ex.getMessage().contains("Database configuration file not found"));
    }

    @Test
    void testLoadFromDiscreteSystemProperties() throws IOException {
        Path emptyFile = Files.createTempFile("db-empty", ".properties");
        System.setProperty("dvms.db.config", emptyFile.toString());
        System.setProperty("db.name", "my_dvms_db");
        System.setProperty("db.host", "127.0.0.1");
        System.setProperty("db.port", "3307");
        System.setProperty("db.user", "app_user");
        System.setProperty("db.password", "secret123");

        Properties props = DatabaseConfig.load();
        assertEquals("jdbc:mysql://127.0.0.1:3307/my_dvms_db", props.getProperty("db.url"));
        assertEquals("app_user", props.getProperty("db.username"));
        assertEquals("secret123", props.getProperty("db.password"));
    }
}

