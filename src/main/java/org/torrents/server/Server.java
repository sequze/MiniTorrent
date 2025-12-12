package org.torrents.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torrents.server.db.RepositoryImpl;
import org.torrents.server.handlers.MessageHandlerFactory;
import org.torrents.server.service.BroadcastService;
import org.torrents.server.service.FileService;
import org.torrents.server.service.FileTransferService;
import org.torrents.server.service.PeerService;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;

public class Server {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static final int DEFAULT_PORT = 6969;
    private ServerSocket serverSocket;
    private BroadcastService broadcastService;
    private PeerService peerService;
    private final int port;
    private volatile boolean running = false;

    public Server() {
        this(DEFAULT_PORT);
    }

    public Server(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        if (running) {
            throw new IllegalStateException("Server is already running");
        }
        RepositoryImpl repository = new RepositoryImpl();
        FileService fileService = new FileService(repository);
        serverSocket = new ServerSocket(port);
        peerService = new PeerService();
        FileTransferService fileTransferService = new FileTransferService(peerService, repository);
        broadcastService = new BroadcastService(peerService, fileService);
        MessageHandlerFactory messageHandlerFactory = new MessageHandlerFactory(fileService, broadcastService, fileTransferService);
        running = true;

        logger.info("Server started on port {}", port);

        while (running) {
            try {
                Socket socket = serverSocket.accept();

                // Проверяем флаг после accept
                if (!running) {
                    socket.close();
                    break;
                }

                try {
                    OutputStream out = socket.getOutputStream();
                    InputStream in = socket.getInputStream();
                    String clientId = UUID.randomUUID().toString();
                    ClientHandler client = new ClientHandler(clientId, out, in, peerService, fileTransferService, messageHandlerFactory);

                    peerService.addPeer(clientId, client);

                    new Thread(client).start();

                    logger.info("New client connected: {}", clientId);
                } catch (Exception e) {
                    logger.error("Error creating client handler: {}", e.getMessage(), e);
                    try {
                        socket.close();
                    } catch (IOException closeEx) {
                        logger.error("Error closing socket: {}", closeEx.getMessage());
                    }
                }
            } catch (IOException e) {
                if (running) {
                    logger.error("Error accepting connection: {}", e.getMessage(), e);
                }
            }
        }

        logger.info("Server stopped accepting new connections");
    }


    /**
     * Остановить сервер с graceful shutdown
     */
    public void stop() {
        if (!running) {
            return;
        }

        logger.info("Stopping server...");
        running = false;

        // Закрываем серверный сокет
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                logger.debug("Server socket closed");
            } catch (IOException e) {
                logger.error("Error closing server socket: {}", e.getMessage());
            }
        }

        // Останавливаем BroadcastService
        if (broadcastService != null) {
            broadcastService.shutdown();
        }

        // Закрываем все клиентские соединения
        if (peerService != null) {
            peerService.shutdownAllPeers();
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("Server stopped gracefully");
    }

    /**
     * Проверить, запущен ли сервер
     */
    public boolean isRunning() {
        return running;
    }
}
