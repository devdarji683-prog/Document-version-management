package com.dvms.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class DatabaseConfig {
    private static final String DEFAULT_CONFIG_FILE = "db.properties";
    private static final String SYS_PROP_DB_CONFIG = "dvms.db.config";
    private static final String ENV_DB_CONFIG = "DVMS_DB_CONFIG";

    private static final String DEFAULT_DB_HOST = "localhost";
    private static final String DEFAULT_DB_PORT = "3306";

    private static final List<String> REQUIRED_KEYS = List.of(
            "db.url",
            "db.username",
            "db.password");

    private DatabaseConfig() {
    }

    public static Path getConfigPath() {
        String prop = System.getProperty(SYS_PROP_DB_CONFIG);
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop).toAbsolutePath().normalize();
        }
        String env = System.getenv(ENV_DB_CONFIG);
        if (env != null && !env.isBlank()) {
            return Path.of(env).toAbsolutePath().normalize();
        }
        return Path.of(DEFAULT_CONFIG_FILE).toAbsolutePath().normalize();
    }

    public static Properties load() throws IOException {
        Path configFile = getConfigPath();
        boolean hasExplicitConfig = (System.getProperty(SYS_PROP_DB_CONFIG) != null && !System.getProperty(SYS_PROP_DB_CONFIG).isBlank())
                || (System.getenv(ENV_DB_CONFIG) != null && !System.getenv(ENV_DB_CONFIG).isBlank());

        Properties properties = new Properties();

        if (Files.isRegularFile(configFile)) {
            try (InputStream input = Files.newInputStream(configFile)) {
                properties.load(input);
            }
        } else if (hasExplicitConfig) {
            throw new IOException(
                "Database configuration file not found: "
                    + configFile
                    + ". Check the path configured in -D" + SYS_PROP_DB_CONFIG + " or " + ENV_DB_CONFIG + ".");
        }

        // Overlay or populate from environment variables / system properties
        String dbUrl = properties.getProperty("db.url");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = getEnvOrProp("DB_URL", "db.url");
            if (dbUrl != null && !dbUrl.isBlank()) {
                properties.setProperty("db.url", dbUrl);
            } else {
                String dbName = getEnvOrProp("DB_NAME", "db.name");
                if (dbName != null && !dbName.isBlank()) {
                    String host = getEnvOrProp("DB_HOST", "db.host");
                    if (host == null || host.isBlank()) {
                        host = DEFAULT_DB_HOST;
                    }
                    String port = getEnvOrProp("DB_PORT", "db.port");
                    if (port == null || port.isBlank()) {
                        port = DEFAULT_DB_PORT;
                    }
                    properties.setProperty("db.url", "jdbc:mysql://" + host + ":" + port + "/" + dbName);
                }
            }
        }

        String dbUser = properties.getProperty("db.username");
        if (dbUser == null || dbUser.isBlank()) {
            dbUser = getEnvOrProp("DB_USER", "db.user");
            if (dbUser != null && !dbUser.isBlank()) {
                properties.setProperty("db.username", dbUser);
            }
        }

        String dbPass = properties.getProperty("db.password");
        if (dbPass == null) {
            dbPass = getEnvOrProp("DB_PASSWORD", "db.password");
            if (dbPass != null) {
                properties.setProperty("db.password", dbPass);
            }
        }

        if (!Files.isRegularFile(configFile) && properties.isEmpty()) {
            throw new IOException(
                "Database configuration file not found: "
                    + configFile
                    + ". Either copy db.properties.example to db.properties, or supply connection settings via "
                    + "environment variables (DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD).");
        }

        List<String> missingKeys = new ArrayList<>();
        for (String key : REQUIRED_KEYS) {
            String value = properties.getProperty(key);
            if (value == null) {
                missingKeys.add(key);
            }
        }

        if (!missingKeys.isEmpty()) {
            throw new IOException("Missing required database configuration keys: "
                    + String.join(", ", missingKeys)
                    + ". Provide them via db.properties or environment variables (DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD).");
        }

        return properties;
    }

    private static String getEnvOrProp(String envKey, String sysPropKey) {
        String propVal = System.getProperty(sysPropKey);
        if (propVal != null && !propVal.isBlank()) {
            return propVal.trim();
        }
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isBlank()) {
            return envVal.trim();
        }
        return null;
    }
}
