package org.torrents.client.viewmodel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torrents.client.DownloadProgressListener;
import org.torrents.client.model.TorrentModel;
import org.torrents.shared.Message;
import org.torrents.shared.ProtocolUtil;
import org.torrents.shared.schemas.ChunkData;
import org.torrents.shared.schemas.FileInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ViewModel слой MVVM
 */
public class ClientViewModel {
    private static final Logger logger = LoggerFactory.getLogger(ClientViewModel.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final TorrentModel model;
    private Thread messageHandlerThread;

    // Observable свойства для UI
    private final StringProperty connectionStatus = new SimpleStringProperty("Не подключено");
    private final BooleanProperty connected = new SimpleBooleanProperty(false);
    @Getter
    private final ObservableList<FileInfoViewModel> availableFiles = FXCollections.observableArrayList();
    private final Map<String, FileInfoViewModel> fileViewModelMap = new ConcurrentHashMap<>();
    @Setter
    private ErrorListener errorListener;
    @Setter
    private InfoListener infoListener;

    public ClientViewModel(String downloadDir) {
        this.model = new TorrentModel(downloadDir);
        setupDownloadProgressListener();
    }

    /**
     * Подключиться к серверу
     */
    public void connect(String host, int port) {
        new Thread(() -> {
            try {
                model.connect(host, port);

                // Запускаем поток для обработки входящих сообщений
                messageHandlerThread = new Thread(this::handleIncomingMessages, "MessageHandler");
                messageHandlerThread.start();

                model.register();

                Platform.runLater(() -> {
                    connected.set(true);
                    connectionStatus.set("Подключено к " + host + ":" + port);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (errorListener != null) {
                        errorListener.onError("Ошибка подключения",
                                "Не удалось подключиться к серверу: " + e.getMessage());
                    }
                });
            }
        }).start();
    }

    /**
     * Отключиться от сервера
     */
    public void disconnect() {
        model.disconnect();

        if (messageHandlerThread != null) {
            try {
                messageHandlerThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Platform.runLater(() -> {
            connected.set(false);
            connectionStatus.set("Отключено");
            availableFiles.clear();
            fileViewModelMap.clear();
        });
    }

    /**
     * Добавить локальный файл для раздачи
     */
    public void addLocalFile(Path filePath) {
        new Thread(() -> {
            try {
                FileInfo fileInfo = model.addLocalFile(filePath);

                Platform.runLater(() -> {
                    if (infoListener != null) {
                        infoListener.onInfo("Файл добавлен",
                                "Файл " + fileInfo.filename() + " добавлен для раздачи");
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (errorListener != null) {
                        errorListener.onError("Ошибка добавления файла",
                                "Не удалось добавить файл: " + e.getMessage());
                    }
                });
            }
        }).start();
    }

    /**
     * Скачать файл
     */
    public void downloadFile(FileInfoViewModel fileViewModel) {
        new Thread(() -> {
            try {
                FileInfo fileInfo = fileViewModel.getFileInfo();
                model.downloadFile(fileInfo);

                Platform.runLater(() -> {
                    fileViewModel.setStatus("Загрузка...");
                });

            } catch (IllegalStateException e) {
                Platform.runLater(() -> {
                    if (infoListener != null) {
                        infoListener.onInfo("Информация", e.getMessage());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (errorListener != null) {
                        errorListener.onError("Ошибка загрузки",
                                "Не удалось начать загрузку: " + e.getMessage());
                    }
                });
            }
        }).start();
    }

    /**
     * Обработка входящих сообщений от сервера
     */
    private void handleIncomingMessages() {
        try {
            while (model.isConnected()) {
                Message message = ProtocolUtil.receiveMessage(model.getInputStream());
                if (message == null) {
                    break;
                }

                handleMessage(message);
            }
        } catch (IOException e) {
            if (model.isConnected()) {
                Platform.runLater(() -> {
                    if (errorListener != null) {
                        errorListener.onError("Ошибка соединения",
                                "Потеряно соединение с сервером: " + e.getMessage());
                    }
                });
            }
        }
    }

    /**
     * Обработка конкретного сообщения
     */
    private void handleMessage(Message message) throws IOException {
        switch (message.getType()) {
            case FILE_LIST -> handleFileList(message);
            case SEND_CHUNK -> handleSendChunk(message);
            case REQUEST_FILE -> handleRequestFile(message);
            case ERROR -> handleError(message);
            default -> logger.warn("Unknown message type: {}", message.getType());
        }
    }

    /**
     * Обработка списка файлов от сервера
     */
    private void handleFileList(Message message) {
        Map<String, Object> payload = message.getPayload();
        Object filesObj = payload.get("files");

        if (filesObj == null) {
            return;
        }

        List<FileInfo> files = objectMapper.convertValue(filesObj, new TypeReference<List<FileInfo>>() {});

        Platform.runLater(() -> {
            // Обновляем список файлов
            availableFiles.clear();
            fileViewModelMap.clear();

            for (FileInfo fileInfo : files) {
                FileInfoViewModel viewModel = new FileInfoViewModel(fileInfo);

                // Проверяем, есть ли файл локально
                if (model.getDownloadManager().hasFile(fileInfo.fileId())) {
                    viewModel.setIsLocal(true);
                    viewModel.setProgress(1.0);
                } else {
                    // Проверяем, идет ли загрузка
                    int progress = model.getDownloadManager().getDownloadProgress(fileInfo.fileId());
                    if (progress > 0) {
                        viewModel.setProgress((double) progress / fileInfo.partsCount());
                        viewModel.setStatus("Загрузка...");
                    }
                }

                availableFiles.add(viewModel);
                fileViewModelMap.put(fileInfo.fileId(), viewModel);
            }
        });
    }

    /**
     * Обработка получения части файла
     */
    private void handleSendChunk(Message message) throws IOException {
        Map<String, Object> payload = message.getPayload();
        String fileId = (String) payload.get("fileId");
        int partIndex = ((Number) payload.get("partIndex")).intValue();
        int length = ((Number) payload.get("length")).intValue();

        byte[] data = ProtocolUtil.readChunkData(model.getInputStream(), length);
        if (data == null) {
            return;
        }

        model.getDownloadManager().saveChunk(new ChunkData(fileId, partIndex, data));
    }

    /**
     * Обработка запроса части файла от другого пира
     */
    private void handleRequestFile(Message message) throws IOException {
        Map<String, Object> payload = message.getPayload();
        String fileId = (String) payload.get("fileId");
        String requestId = (String) payload.get("requestId");
        List<Object> partsRaw = (List<Object>) payload.get("partsNeeded");

        if (partsRaw == null) {
            model.sendError(400, "partsNeeded is required");
            return;
        }

        List<Integer> partsNeeded = partsRaw.stream()
                .map(obj -> ((Number) obj).intValue())
                .toList();

        model.handleFileRequest(fileId, partsNeeded, requestId);
    }

    /**
     * Обработка сообщения об ошибке
     */
    private void handleError(Message message) {
        Map<String, Object> payload = message.getPayload();
        Number codeN = (Number) payload.get("code");
        int code = codeN != null ? codeN.intValue() : -1;
        String msg = (String) payload.get("message");

        Platform.runLater(() -> {
            if (errorListener != null) {
                errorListener.onError("Ошибка сервера [" + code + "]", msg);
            }
        });
    }

    /**
     * Настройка слушателя прогресса загрузки
     */
    private void setupDownloadProgressListener() {
        model.getDownloadManager().setProgressListener(new DownloadProgressListener() {
            @Override
            public void onProgressUpdate(String fileId, String filename, int downloadedParts, int totalParts) {
                Platform.runLater(() -> {
                    FileInfoViewModel viewModel = fileViewModelMap.get(fileId);
                    if (viewModel != null) {
                        double progress = (double) downloadedParts / totalParts;
                        viewModel.setProgress(progress);
                        viewModel.setStatus("Загрузка: " + downloadedParts + "/" + totalParts);
                    }
                });
            }

            @Override
            public void onDownloadComplete(String fileId, String filename) {
                Platform.runLater(() -> {
                    FileInfoViewModel viewModel = fileViewModelMap.get(fileId);
                    if (viewModel != null) {
                        viewModel.setProgress(1.0);
                        viewModel.setIsLocal(true);
                        viewModel.setStatus("Загружено");
                    }

                    if (infoListener != null) {
                        infoListener.onInfo("Загрузка завершена",
                                "Файл " + filename + " успешно загружен");
                    }
                });
            }

            @Override
            public void onDownloadError(String fileId, String filename, Integer partIndex, String error) {
                if (partIndex == null) {
                    // Общая ошибка или превышен лимит попыток для части
                    Platform.runLater(() -> {
                                FileInfoViewModel viewModel = fileViewModelMap.get(fileId);
                                if (viewModel != null) {
                                    viewModel.setStatus("Ошибка");
                                }

                                if (errorListener != null) {
                                    errorListener.onError("Ошибка загрузки",
                                            "Ошибка при загрузке " + filename + ": " + error);
                                }
                    });
                } else {
                    // Попытка повторной загрузки конкретной части
                    logger.info("Retrying download for chunk {} of {}: {}", partIndex, filename, error);
                    new Thread(() -> {
                        try {
                            model.downloadPart(fileId, partIndex);
                        } catch (IOException e) {
                            Platform.runLater(() -> {
                                if (errorListener != null) {
                                    errorListener.onError("Ошибка повторной загрузки",
                                            "Не удалось начать повторную загрузку части " + partIndex + ": " + e.getMessage());
                                }
                            });
                        }
                    }, "RetryDownload-" + fileId + "-" + partIndex).start();
                }
            }
        });
    }

    public StringProperty connectionStatusProperty() {
        return connectionStatus;
    }

    public BooleanProperty connectedProperty() {
        return connected;
    }

    // Интерфейсы для listeners
    public interface ErrorListener {
        void onError(String title, String message);
    }

    public interface InfoListener {
        void onInfo(String title, String message);
    }
}

