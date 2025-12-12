package org.torrents.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torrents.server.ClientHandler;
import org.torrents.shared.Message;
import org.torrents.shared.MessageType;
import org.torrents.shared.schemas.FileInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class BroadcastService {
    private static final Logger logger = LoggerFactory.getLogger(BroadcastService.class);
    private final PeerService peerService;
    private final FileService fileService;
    private final ExecutorService broadcastExecutor;

    public BroadcastService(PeerService peerService, FileService fileService) {
        this.peerService = peerService;
        this.fileService = fileService;
        this.broadcastExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * Рассылка обновленного списка файлов всем подключенным клиентам
     */
    public void broadcastFileList() {
        broadcastExecutor.submit(() -> {
            List<ClientHandler> clients = peerService.getAllPeers();
            if (clients == null) return;

            List<FileInfo> availableFiles = fileService.getFiles();
            Message outMessage = new Message(MessageType.FILE_LIST, Map.of("files", availableFiles));

            for (ClientHandler client : new ArrayList<>(clients)) {
                try {
                    client.sendMessage(outMessage);
                } catch (Exception e) {
                    logger.error("Failed to send FILE_LIST to client {}: {}", client.getClientId(), e.getMessage());
                }
            }
        });
    }

    /**
     * Остановить сервис рассылки
     */
    public void shutdown() {
        // Запрещаем новые задачи
        broadcastExecutor.shutdown();

        try {
            // Ждём завершения текущих
            if (!broadcastExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                // Принудительное завершение
                broadcastExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            broadcastExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("Broadcast service shut down successfully");
    }
}

