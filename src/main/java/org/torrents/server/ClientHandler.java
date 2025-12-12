package org.torrents.server;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torrents.server.handlers.MessageHandler;
import org.torrents.server.handlers.MessageHandlerFactory;
import org.torrents.server.service.FileTransferService;
import org.torrents.shared.Message;
import org.torrents.shared.MessageType;
import org.torrents.shared.ProtocolUtil;
import org.torrents.shared.schemas.ChunkData;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class ClientHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private final String clientId;
    private final OutputStream out;
    private final InputStream in;
    private final ClientListener clientListener;
    private final Map<String, CompletableFuture<ChunkData>> pendingRequests = new ConcurrentHashMap<>();
    private final FileTransferService fileTransferService;
    private final MessageHandlerFactory messageHandlerFactory;

    @Setter
    private volatile boolean running = true;

    public ClientHandler(String clientId, OutputStream out, InputStream in,
                         ClientListener clientListener, FileTransferService fileTransferService,
                         MessageHandlerFactory messageHandlerFactory) {
        this.clientId = clientId;
        this.out = out;
        this.in = in;
        this.clientListener = clientListener;
        this.fileTransferService = fileTransferService;
        this.messageHandlerFactory = messageHandlerFactory;
    }

    @Override
    public void run() {
        try {
            while (running) {
                Message message = ProtocolUtil.receiveMessage(in);
                if (message == null) {
                    // Клиент разорвал соединение
                    logger.info("Client {} disconnected (EOF)", clientId);
                    break;
                }

                // Обработка сообщения
                MessageHandler handler = messageHandlerFactory.getHandler(message.getType());
                handler.handle(message, this);
            }
        } catch (IOException e) {
            logger.error("Error handling client {}: {}", clientId, e.getMessage(), e);
        } finally {
            try {
                if (in != null) in.close();
            } catch (IOException ignored) {
            }
            try {
                if (out != null) out.close();
            } catch (IOException ignored) {
            }
            // Уведомляем об отключении клиента
            if (clientListener != null) {
                clientListener.onClientDisconnected(clientId);
            }
        }
    }

    /**
     * Отправка сообщения клиенту.
     * Используется synchronized блок для обеспечения потокобезопасности при записи в OutputStream
     */
    public void sendMessage(Message message) throws IOException {
        synchronized (out) {
            ProtocolUtil.sendMessage(out, message);
        }
    }

    /**
     * Отправка чанка данных клиенту.
     */
    public void sendChunkData(ChunkData chunkData) throws IOException {
        synchronized (out) {
            Message msg = new Message(MessageType.SEND_CHUNK, Map.of(
                    "fileId", chunkData.fileId(),
                    "partIndex", chunkData.partIndex(),
                    "length", chunkData.data().length
            ));
            ProtocolUtil.sendChunkData(out, msg, chunkData.data());
        }
    }

    /**
     * Отправка запроса чанка данных клиенту и получение CompletableFuture для ожидания ответа.
     */
    public CompletableFuture<ChunkData> sendChunkRequest(Message message, String requestId) throws IOException {
        CompletableFuture<ChunkData> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);
        synchronized (out) {
            ProtocolUtil.sendMessage(out, message);
        }
        return future;
    }
}
