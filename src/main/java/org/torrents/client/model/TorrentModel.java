package org.torrents.client.model;

import lombok.Getter;
import org.torrents.client.DownloadManager;
import org.torrents.shared.Message;
import org.torrents.shared.MessageType;
import org.torrents.shared.ProtocolUtil;
import org.torrents.shared.schemas.FileInfo;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TorrentModel - содержит бизнес-логику и данные торрент-клиента
 */
public class TorrentModel {
    /**
     * -- GETTER --
     *  Получить менеджер загрузок
     */
    @Getter
    private final DownloadManager downloadManager;
    private Socket socket;
    private OutputStream out;
    private InputStream in;
    /**
     * -- GETTER --
     *  Проверить подключен ли клиент
     */
    @Getter
    private volatile boolean connected = false;

    public TorrentModel(String downloadDir) {
        this.downloadManager = new DownloadManager(downloadDir);
    }

    /**
     * Подключиться к серверу
     */
    public void connect(String host, int port) throws IOException {
        if (connected) {
            throw new IllegalStateException("Already connected");
        }

        socket = new Socket(host, port);
        out = socket.getOutputStream();
        in = socket.getInputStream();
        connected = true;
    }

    /**
     * Отключиться от сервера
     */
    public void disconnect() {
        connected = false;

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    /**
     * Зарегистрироваться на сервере
     */
    public void register() throws IOException {
        if (!connected) {
            throw new IllegalStateException("Not connected");
        }

        List<FileInfo> localFiles = downloadManager.getLocalFiles();
        Message registerMsg = new Message(MessageType.REGISTER, Map.of("files", localFiles));

        synchronized (out) {
            ProtocolUtil.sendMessage(out, registerMsg);
        }
    }

    /**
     * Добавить локальный файл для раздачи
     */
    public FileInfo addLocalFile(Path filePath) throws IOException {
        FileInfo fileInfo = downloadManager.addLocalFile(filePath);

        // Отправляем сообщение ADD_FILE серверу
        Map<String, String> partsMap = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> e : fileInfo.partChecksums().entrySet()) {
            partsMap.put(String.valueOf(e.getKey()), e.getValue());
        }

        Message addMsg = new Message(MessageType.ADD_FILE, Map.of(
                "fileId", fileInfo.fileId(),
                "size", fileInfo.size(),
                "partsCount", fileInfo.partsCount(),
                "parts", partsMap,
                "filename", fileInfo.filename()
        ));

        synchronized (out) {
            ProtocolUtil.sendMessage(out, addMsg);
        }

        return fileInfo;
    }

    /**
     * Запросить загрузку файла
     */
    public void downloadFile(FileInfo fileInfo) throws IOException {
        if (!connected) {
            throw new IllegalStateException("Not connected");
        }

        if (downloadManager.hasFile(fileInfo.fileId())) {
            throw new IllegalStateException("File already downloaded");
        }

        downloadManager.startDownload(fileInfo);

        List<Integer> neededParts = downloadManager.getNeededParts(fileInfo.fileId());
        String requestId = UUID.randomUUID().toString();

        Message requestMsg = new Message(MessageType.REQUEST_FILE, Map.of(
                "fileId", fileInfo.fileId(),
                "partsNeeded", neededParts,
                "requestId", requestId
        ));

        synchronized (out) {
            ProtocolUtil.sendMessage(out, requestMsg);
        }
    }

    /**
     * Запросить повторную загрузку части файла
     */
    public void downloadPart(String fileId, Integer partIndex) throws IOException {
        if (!connected) {
            throw new IllegalStateException("Not connected");
        }
        String requestId = UUID.randomUUID().toString();

        Message requestMsg = new Message(MessageType.REQUEST_FILE, Map.of(
                "fileId", fileId,
                "partsNeeded", List.of(partIndex),
                "requestId", requestId
        ));

        synchronized (out) {
            ProtocolUtil.sendMessage(out, requestMsg);
        }
    }

    /**
     * Получить входящий поток для чтения сообщений
     */
    public InputStream getInputStream() {
        return in;
    }


    /**
     * Отправить сообщение об ошибке
     */
    public void sendError(int code, String message) throws IOException {
        Message errorMsg = new Message(MessageType.ERROR, Map.of(
                "code", code,
                "message", message
        ));

        synchronized (out) {
            ProtocolUtil.sendMessage(out, errorMsg);
        }
    }

    /**
     * Обработать запрос части файла от другого пира
     */
    public void handleFileRequest(String fileId, List<Integer> partsNeeded, String requestId) throws IOException {
        for (int partIndex : partsNeeded) {
            try {
                byte[] chunkData = downloadManager.getLocalChunk(fileId, partIndex);

                Message chunkMsg = new Message(MessageType.SEND_CHUNK, Map.of(
                        "fileId", fileId,
                        "partIndex", partIndex,
                        "length", chunkData.length,
                        "requestId", requestId != null ? requestId : ""
                ));

                synchronized (out) {
                    ProtocolUtil.sendChunkData(out, chunkMsg, chunkData);
                }

            } catch (FileNotFoundException e) {
                sendError(404, "Chunk not found: " + fileId + ":" + partIndex);
            }
        }
    }
}

