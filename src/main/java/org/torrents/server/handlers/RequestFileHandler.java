package org.torrents.server.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torrents.server.ClientHandler;
import org.torrents.server.service.FileTransferService;
import org.torrents.shared.Message;
import org.torrents.shared.schemas.RequestFile;

public class RequestFileHandler implements MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(RequestFileHandler.class);
    private final FileTransferService fileTransferService;

    public RequestFileHandler(FileTransferService fileTransferService) {
        this.fileTransferService = fileTransferService;
    }

    @Override
    public void handle(Message message, ClientHandler handler) {
        try {
            // Парсим сообщение
            RequestFile req = MessageParseHelper.parseFileRequest(message);
            logger.info("REQUEST_FILE from {}: {}", handler.getClientId(), req);

            // Отправляем файл клиенту
            fileTransferService.transferFile(handler, req);
        } catch (Exception e) {
            logger.error("Error processing REQUEST_FILE: {}", e.getMessage(), e);
        }
    }
}
