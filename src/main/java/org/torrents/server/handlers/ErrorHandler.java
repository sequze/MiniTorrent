package org.torrents.server.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torrents.server.ClientHandler;
import org.torrents.shared.Message;
import org.torrents.shared.schemas.ErrorInfo;

public class ErrorHandler implements MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(ErrorHandler.class);

    @Override
    public void handle(Message message, ClientHandler handler) {
        try {
            ErrorInfo err = MessageParseHelper.parseError(message);
            logger.error("Error received from client {}: {}", handler.getClientId(), err);
        } catch (Exception e) {
            logger.error("Error processing ERROR message from {}: {}", handler.getClientId(), e.getMessage(), e);
        }
    }
}
