package com.dvms.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DatabaseConnection {
    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            // Driver discovery fallback via ServiceLoader
        }
    }

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        Properties properties;
        try {
            properties = DatabaseConfig.load();
        } catch (java.io.IOException exception) {
            throw new SQLException("Unable to load database configuration: " + exception.getMessage(), exception);
        }

        return DriverManager.getConnection(
                properties.getProperty("db.url"),
                properties.getProperty("db.username"),
                properties.getProperty("db.password"));
    }
}
