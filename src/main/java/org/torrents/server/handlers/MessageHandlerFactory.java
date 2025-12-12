package org.torrents.server.handlers;

import org.torrents.server.service.BroadcastService;
import org.torrents.server.service.FileService;
import org.torrents.server.service.FileTransferService;
import org.torrents.shared.MessageType;

import java.util.HashMap;
import java.util.Map;

public class MessageHandlerFactory {

    // Реестр обработчиков - каждому типу сообщения соответствует свой handler
    private final Map<MessageType, MessageHandler> handlers = new HashMap<>();

    public MessageHandlerFactory(FileService fileService, BroadcastService broadcastService, FileTransferService fileTransferService) {
        // Регистрируем обработчики для каждого типа сообщения
        handlers.put(MessageType.REGISTER, new RegisterHandler(fileService));
        handlers.put(MessageType.ADD_FILE, new AddFileHandler(fileService, broadcastService));
        handlers.put(MessageType.REQUEST_FILE, new RequestFileHandler(fileTransferService));
        handlers.put(MessageType.SEND_CHUNK, new SendChunkHandler());
        handlers.put(MessageType.ERROR, new ErrorHandler());
    }

    public MessageHandler getHandler(MessageType messageType) {
        MessageHandler handler = handlers.get(messageType);

        if (handler == null) {
            throw new IllegalArgumentException("No handler found for message type: " + messageType);
        }
        return handler;
    }

    public boolean hasHandler(MessageType messageType) {
        return handlers.containsKey(messageType);
    }
}