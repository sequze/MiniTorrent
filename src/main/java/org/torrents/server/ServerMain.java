package org.torrents.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torrents.server.db.DatabaseManager;

public class ServerMain {
    private static final Logger logger = LoggerFactory.getLogger(ServerMain.class);
    private static final String DEFAULT_DB_PATH = "server.db";
    private static final boolean DEFAULT_CLEAR_ON_START = true;
    private static final int DEFAULT_PORT = 6969;

    public static void main(String[] args) {
        // Разбор аргументов командной строки
        String dbPath = getArgOrEnv(args, 0, "DB_PATH", DEFAULT_DB_PATH);
        boolean clearOnStart = getBooleanArgOrEnv(args, 1, "DB_CLEAR_ON_START", DEFAULT_CLEAR_ON_START);
        int port = getIntArgOrEnv(args, 2, "SERVER_PORT", DEFAULT_PORT);

        Server server = new Server(port);

        // Регистрируем shutdown hook для graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown-hook"));

        try {
            logger.info("Starting server with database: {}", dbPath);
            logger.info("Clear on start: {}", clearOnStart);
            logger.info("Server port: {}", port);

            DatabaseManager.initPool(dbPath, clearOnStart, DatabaseManager.PoolConfig.fromEnvironment());
            DatabaseManager.runMigrations();
            server.start();
        } catch (Exception e) {
            logger.error("Server failed to start: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private static String getArgOrEnv(String[] args, int index, String envKey, String defaultValue) {
        // Сначала проверяем аргументы командной строки
        if (args.length > index && args[index] != null && !args[index].isEmpty()) {
            return args[index];
        }
        // Затем переменные окружения
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        // Если ничего не указано, возвращаем значение по умолчанию
        return defaultValue;
    }

    private static boolean getBooleanArgOrEnv(String[] args, int index, String envKey, boolean defaultValue) {
        String arg = getArgOrEnv(args, index, envKey, Boolean.toString(defaultValue));
        return Boolean.parseBoolean(arg);
    }

    private static int getIntArgOrEnv(String[] args, int index, String envKey, int defaultValue) {
        String arg = getArgOrEnv(args, index, envKey, Integer.toString(defaultValue));
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
