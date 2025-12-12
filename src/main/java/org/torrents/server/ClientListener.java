package org.torrents.server;

/**
 * Интерфейс для обработки событий жизненного цикла клиента
 */
public interface ClientListener {
    /**
     * Вызывается при отключении клиента
     * @param clientId идентификатор отключившегося клиента
     */
    void onClientDisconnected(String clientId);
}

