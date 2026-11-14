package com.dvms.util;

import com.dvms.config.DatabaseConnection;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseConnectionTest {
    private DatabaseConnectionTest() {
    }

    public static void main(String[] args) {
        try (Connection connection = DatabaseConnection.getConnection()) {
            if (connection.isClosed()) {
                throw new SQLException("The JDBC driver returned a closed connection.");
            }

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT DATABASE()")) {
                if (!resultSet.next()) {
                    throw new SQLException("The database server returned no current database.");
                }

                String databaseName = resultSet.getString(1);
                if (databaseName == null || databaseName.isBlank()) {
                    throw new SQLException(
                            "Connected to MySQL, but no database is selected. Check the database name in db.url.");
                }

                System.out.println("Connected database: " + databaseName);
            }

            System.out.println("Database connection test succeeded.");
        } catch (SQLException exception) {
            System.err.println("Database connection test failed: " + exception.getMessage());
            if (exception.getSQLState() != null) {
                System.err.println("SQLState: " + exception.getSQLState()
                        + ", vendor code: " + exception.getErrorCode());
            }
            System.err.println("Check db.properties, the MySQL server, and the troubleshooting steps in README.md.");
            System.exit(1);
        } finally {
            com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
        }
    }
}
