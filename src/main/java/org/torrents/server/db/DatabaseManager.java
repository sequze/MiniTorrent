package org.torrents.server.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static HikariDataSource dataSource;

    public static void initPool(String dbPath, boolean clearOnStart) {
        if (clearOnStart) {
            try {
                if (!dbPath.startsWith(":") && !dbPath.startsWith("jdbc:")) {
                    Path p = Paths.get(dbPath);
                    Files.deleteIfExists(p);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to clear database file: " + dbPath, e);
            }
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000); // 30 seconds
        // Оптимизации
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("busy_timeout", "5000");
        config.addDataSourceProperty("foreign_keys", "ON");
        dataSource = new HikariDataSource(config);
    }

    public static void runMigrations() {
        try (Connection conn = getConnection()) {
            // TODO: вынести все настройки в ENV-file или конфиг
            String sql = loadSql("/db/migration.sql");

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sql);
            }

        } catch (Exception e) {
            throw new RuntimeException("DB migration error", e);
        }
    }

    private static String loadSql(String resourcePath) {
        try (InputStream is = DatabaseManager.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Migration file not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void closePool() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}