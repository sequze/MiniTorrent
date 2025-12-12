package org.torrents.server.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torrents.server.ClientHandler;
import org.torrents.server.service.BroadcastService;
import org.torrents.server.service.FileService;
import org.torrents.shared.Message;
import org.torrents.shared.schemas.FileInfo;

public class AddFileHandler implements MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(AddFileHandler.class);
    private final FileService fileService;
    private final BroadcastService broadcastService;

    public AddFileHandler(FileService fileService, BroadcastService broadcastService) {
        this.fileService = fileService;
        this.broadcastService = broadcastService;
    }

    @Override
    public void handle(Message message, ClientHandler handler) {
        try {
            // Парсим информацию о файле из сообщения
            FileInfo fileInfo = MessageParseHelper.parseAddFile(message);
            logger.info("ADD_FILE received from {}: {}", handler.getClientId(), fileInfo.filename());

            // Добавляем файл в список доступных файлов
            fileService.addFileForPeer(handler.getClientId(), fileInfo);

            // Уведомляем всех клиентов об обновлении списка файлов
            broadcastService.broadcastFileList();

            logger.info("File added and broadcasted: {}", fileInfo.filename());
        } catch (Exception e) {
            logger.error("Error processing ADD_FILE: {}", e.getMessage(), e);
        }
    }
}
