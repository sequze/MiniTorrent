package org.torrents.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torrents.server.ClientHandler;
import org.torrents.server.db.Repository;
import org.torrents.shared.Message;
import org.torrents.shared.MessageType;
import org.torrents.shared.schemas.ChunkData;
import org.torrents.shared.schemas.FileInfo;
import org.torrents.shared.schemas.FilePart;
import org.torrents.shared.schemas.RequestFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Сервис для передачи файлов между пирами
 */
public class FileTransferService {
    private static final Logger logger = LoggerFactory.getLogger(FileTransferService.class);
    PeerService peerService;
    Repository repository;

    public FileTransferService(PeerService peerService, Repository repository) {
        this.peerService = peerService;
        this.repository = repository;
    }

    /**
     * Получить все доступные части файла и отправить клиенту
     */
    public void transferFile(ClientHandler client, RequestFile clientRequest) {
        // Определяем, нужны ли все части файла
        boolean allFileNeeded = clientRequest.partsNeeded() == null || clientRequest.partsNeeded().isEmpty();

        // Получаем информацию о файле
        FileInfo fileInfo = repository.getFile(clientRequest.fileId());
        List<Integer> partsToSend = allFileNeeded ? fileInfo.parts() : clientRequest.partsNeeded();
        logger.info("Starting file transfer for client {}: fileId={}, parts={}",
                client.getClientId(), clientRequest.fileId(), partsToSend.size());

        for (int partIndex : partsToSend) {
            // Получаем информацию о пирах, у которых есть данная часть файла
            FilePart partInfo = repository.getFilePartWithPeers(clientRequest.fileId(), partIndex);
            List<String> peers = partInfo.peers();
            String chosenPeer = null;
            try {
                // Ожидаем освобождения какого-нибудь пира из списка
                chosenPeer = waitForFreePeer(peers, 10000);
                logger.debug("Chosen peer {} for part {} of file {}", chosenPeer, partIndex, clientRequest.fileId());

                // Запрашиваем часть файла у выбранного пира
                ClientHandler peerHandler = peerService.getPeer(chosenPeer);
                String requestId = UUID.randomUUID().toString();
                Map<String, Object> payload = Map.of(
                        "fileId", clientRequest.fileId(),
                        "partsNeeded", List.of(partIndex),
                        "requestId", requestId
                );
                Message requestMessage = new Message(MessageType.REQUEST_FILE, payload);
                CompletableFuture<ChunkData> chunk = peerHandler.sendChunkRequest(requestMessage, requestId);

                // Ожидаем получения части файла с таймаутом
                ChunkData chunkData = chunk.get(15, TimeUnit.SECONDS);

                // Отправляем полученную часть файла клиенту
                client.sendChunkData(chunkData);
            } catch (InterruptedException | IOException | ExecutionException | TimeoutException e) {
                logger.error("Error transferring part {} to client {}: {}", partIndex, client.getClientId(), e.getMessage(), e);
                throw new RuntimeException(e);
            } finally {
                // Освобождаем пир
                if (chosenPeer != null) {
                    peerService.releasePeer(chosenPeer);
                }
            }
        }
    }

    /**
     * Ожидание освобождения какого-нибудь пира из списка
     * Если пир не освободится за timeoutMs миллисекунд, ожидание прерывается с ошибкой
     */
    private String waitForFreePeer(List<String> peers, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String chosen = null;
        while (chosen == null && System.currentTimeMillis() < deadline) {
            chosen = peerService.chooseFreePeer(peers);
            if (chosen == null) {
                Thread.sleep(10);
            }
        }
        if (chosen == null) {
            logger.error("No free peers available within timeout for peers: {}", peers);
            throw new InterruptedException("No free peers available within timeout");
        }
        return chosen;
    }

}
