package org.torrents.server.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    public static void initPool(String dbPath, boolean clearOnStart) {
        initPool(dbPath, clearOnStart, new PoolConfig());
    }

    public static void initPool(String dbPath, boolean clearOnStart, PoolConfig poolConfig) {
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
        config.setMaximumPoolSize(poolConfig.maxPoolSize);
        config.setMinimumIdle(poolConfig.minIdle);
        config.setConnectionTimeout(poolConfig.connectionTimeout);
        // Оптимизации
        config.addDataSourceProperty("journal_mode", poolConfig.journalMode);
        config.addDataSourceProperty("busy_timeout", String.valueOf(poolConfig.busyTimeout));
        config.addDataSourceProperty("foreign_keys", poolConfig.foreignKeys ? "ON" : "OFF");
        dataSource = new HikariDataSource(config);
    }

    public static class PoolConfig {
        public int maxPoolSize = 10;
        public int minIdle = 2;
        public int connectionTimeout = 30000; // 30 seconds
        public String journalMode = "WAL";
        public int busyTimeout = 5000;
        public boolean foreignKeys = true;

        public PoolConfig() {}

        public PoolConfig(int maxPoolSize, int minIdle, int connectionTimeout,
                          String journalMode, int busyTimeout, boolean foreignKeys) {
            this.maxPoolSize = maxPoolSize;
            this.minIdle = minIdle;
            this.connectionTimeout = connectionTimeout;
            this.journalMode = journalMode;
            this.busyTimeout = busyTimeout;
            this.foreignKeys = foreignKeys;
        }

        public static PoolConfig fromEnvironment() {
            PoolConfig config = new PoolConfig();
            config.maxPoolSize = getEnvInt("DB_MAX_POOL_SIZE", 10);
            config.minIdle = getEnvInt("DB_MIN_IDLE", 2);
            config.connectionTimeout = getEnvInt("DB_CONNECTION_TIMEOUT", 30000);
            config.journalMode = getEnv("DB_JOURNAL_MODE", "WAL");
            config.busyTimeout = getEnvInt("DB_BUSY_TIMEOUT", 5000);
            config.foreignKeys = getEnvBoolean("DB_FOREIGN_KEYS", true);
            return config;
        }

        private static int getEnvInt(String key, int defaultValue) {
            String value = System.getenv(key);
            if (value != null) {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    logger.error("Invalid integer value for {}: {}, using default: {}", key, value, defaultValue);
                }
            }
            return defaultValue;
        }

        private static String getEnv(String key, String defaultValue) {
            String value = System.getenv(key);
            return value != null ? value : defaultValue;
        }

        private static boolean getEnvBoolean(String key, boolean defaultValue) {
            String value = System.getenv(key);
            if (value != null) {
                return Boolean.parseBoolean(value);
            }
            return defaultValue;
        }
    }

    public static void runMigrations() {
        try (Connection conn = getConnection()) {
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