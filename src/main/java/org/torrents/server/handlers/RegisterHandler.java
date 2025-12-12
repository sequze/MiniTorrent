package org.torrents.server.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torrents.server.ClientHandler;
import org.torrents.server.service.FileService;
import org.torrents.shared.Message;
import org.torrents.shared.MessageType;
import org.torrents.shared.schemas.FileInfo;

import java.util.List;
import java.util.Map;

public class RegisterHandler implements MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(RegisterHandler.class);
    private final FileService fileService;

    public RegisterHandler(FileService fileService) {
        this.fileService = fileService;
    }

    @Override
    public void handle(Message message, ClientHandler handler) {
        try {
            // Получаем файлы клиента
            List<FileInfo> files = MessageParseHelper.parseFileList(message);

            // Регистрируем клиента и его файлы
            fileService.registerNewPeer(handler.getClientId(), files);

            // Отправляем клиенту список всех доступных файлов
            List<FileInfo> availableFiles = fileService.getFiles();
            Message outMessage = new Message(MessageType.FILE_LIST, Map.of("files", availableFiles));
            handler.sendMessage(outMessage);

            logger.info("Client {} registered with {} files", handler.getClientId(), files.size());
        } catch (Exception e) {
            logger.error("Error registering client {}: {}", handler.getClientId(), e.getMessage(), e);
        }
    }
}
