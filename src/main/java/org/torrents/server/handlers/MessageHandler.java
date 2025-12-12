package org.torrents.server.handlers;

import org.torrents.server.ClientHandler;
import org.torrents.shared.Message;

public interface MessageHandler {
    public void handle(Message message, ClientHandler handler);
}
