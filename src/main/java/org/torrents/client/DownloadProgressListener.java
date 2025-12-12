package org.torrents.client;

/**
 * Интерфейс для уведомлений о прогрессе загрузки
 */
public interface DownloadProgressListener {
    void onProgressUpdate(String fileId, String filename, int downloadedParts, int totalParts);
    void onDownloadComplete(String fileId, String filename);
    void onDownloadError(String fileId, String filename, Integer partIndex, String error);
}

