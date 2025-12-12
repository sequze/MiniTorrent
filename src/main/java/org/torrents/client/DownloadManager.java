package org.torrents.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torrents.client.viewmodel.ClientViewModel;
import org.torrents.shared.schemas.ChunkData;
import org.torrents.shared.schemas.FileInfo;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер загрузки файлов - управляет скачиванием файлов по частям
 */
public class DownloadManager {
    private static final Logger logger = LoggerFactory.getLogger(ClientViewModel.class);
    private static final int CHUNK_SIZE = 256 * 1024; // 256KB на часть
    private final Path downloadDir;
    // Map для учета загружаемых файлов
    private final Map<String, FileDownload> activeDownloads = new ConcurrentHashMap<>();
    // Map доступных локально файлов
    private final Map<String, FileInfo> localFiles = new ConcurrentHashMap<>();
    private DownloadProgressListener progressListener;

    public DownloadManager(String downloadDir) {
        this.downloadDir = Paths.get(downloadDir);
        try {
            Files.createDirectories(this.downloadDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create download directory", e);
        }
    }

    /**
     * Начать загрузку файла
     */
    public synchronized void startDownload(FileInfo fileInfo) {
        // Если файл уже загружается, игнорируем запрос
        if (activeDownloads.containsKey(fileInfo.fileId())) {
            logger.debug("Download already active for file: {}", fileInfo.filename());
            return;
        }

        FileDownload download = new FileDownload(fileInfo);
        activeDownloads.put(fileInfo.fileId(), download);
        logger.info("Started download: {} ({} bytes, {} parts)", fileInfo.filename(), fileInfo.size(), fileInfo.partsCount());
    }

    /**
     * Сохранить полученную часть файла
     */
    public synchronized void saveChunk(ChunkData chunk) throws IOException {
        FileDownload download = activeDownloads.get(chunk.fileId());
        if (download == null) {
            logger.info("No active download for file: {}", chunk.fileId());
            return;
        }

        // Сохраняем чанк
        download.saveChunk(chunk.partIndex(), chunk.data());
        logger.info("Saved chunk {}/{} for {}", chunk.partIndex(), download.fileInfo.partsCount(), download.fileInfo.filename());

        // Уведомляем слушателя о прогрессе
        if (progressListener != null) {
            progressListener.onProgressUpdate(chunk.fileId(), download.fileInfo.filename(), download.chunks.size(), download.fileInfo.partsCount());
        }

        // Если загрузка завершена, собираем файл
        if (download.isComplete()) {
            completeDownload(download);
        }
    }

    /**
     * Завершить загрузку - собрать все части в один файл
     */
    private void completeDownload(FileDownload download) throws IOException {
        Path filePath = downloadDir.resolve(download.fileInfo.filename());

        // Сначала проверяем все части перед сборкой
        List<Integer> corruptedParts = new ArrayList<>();

        for (int i = 0; i < download.fileInfo.partsCount(); i++) {
            byte[] chunk = download.getChunk(i);
            if (chunk == null) {
                corruptedParts.add(i);
                logger.error("Chunk {} is missing for {}", i, download.fileInfo.filename());
                continue;
            }

            // Проверяем checksum части (если доступны)
            if (download.fileInfo.partChecksums() != null) {
                String expectedChecksum = download.fileInfo.partChecksums().get(i);
                if (expectedChecksum != null && !expectedChecksum.isEmpty()) {
                    String actualChecksum = calculateChecksum(chunk);
                    if (!actualChecksum.equals(expectedChecksum)) {
                        corruptedParts.add(i);
                        logger.warn("Checksum mismatch for chunk {} of {}: expected {}, got {}", i,
                                download.fileInfo.filename(), expectedChecksum, actualChecksum);
                    }
                }
            }
        }

        // Если есть поврежденные части, удаляем их и уведомляем о необходимости retry
        if (!corruptedParts.isEmpty()) {
            if (progressListener != null) {
                for (Integer partIndex : corruptedParts) {
                    // Удаляем поврежденную часть из кэша
                    download.removeCorruptedChunk(partIndex);

                    // Инкрементируем счетчик попыток
                    download.incrementRetryAttempt(partIndex);

                    // Проверяем, можно ли повторить попытку
                    if (download.canRetry(partIndex)) {
                        progressListener.onDownloadError(download.fileInfo.fileId(),
                                download.fileInfo.filename(), partIndex,
                                "Chunk corrupted or missing (attempt " + download.getRetryAttempt(partIndex) + "): " + partIndex);
                    } else {
                        // Превышен лимит попыток
                        progressListener.onDownloadError(
                                download.fileInfo.fileId(), download.fileInfo.filename(), null,
                                "Failed to download chunk " + partIndex + " after " + download.getRetryAttempt(partIndex) + " attempts");
                    }
                }
            }
            return; // Не собираем файл, если есть поврежденные части
        }

        // Все части валидны, собираем файл
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            for (int i = 0; i < download.fileInfo.partsCount(); i++) {
                byte[] chunk = download.getChunk(i);
                fos.write(chunk);
            }
        } catch (IOException e) {
            // Уведомляем слушателя об ошибке
            if (progressListener != null) {
                progressListener.onDownloadError(download.fileInfo.fileId(), download.fileInfo.filename(), null, e.getMessage());
            }
            throw e;
        }

        logger.info("Download complete: {} saved to {}", download.fileInfo.filename(), filePath);

        // Очистить временные данные и зарегистрировать файл как доступный
        activeDownloads.remove(download.fileInfo.fileId());
        localFiles.put(download.fileInfo.fileId(), download.fileInfo);

        // Уведомляем слушателя о завершении
        if (progressListener != null) {
            progressListener.onDownloadComplete(download.fileInfo.fileId(), download.fileInfo.filename());
        }
    }

    /**
     * Вычислить SHA-256 checksum для данных
     */
    private String calculateChecksum(byte[] data) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(data);
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("Failed to calculate checksum", e);
        }
    }

    /**
     * Получить список частей, которые нужно скачать для файла
     */
    public List<Integer> getNeededParts(String fileId) {
        FileDownload download = activeDownloads.get(fileId);
        if (download == null) {
            return Collections.emptyList();
        }
        return download.getNeededParts();
    }

    /**
     * Загрузить файл с диска и разбить на части
     */
    public FileInfo addLocalFile(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("File not found: " + filePath);
        }

        String fileId = UUID.randomUUID().toString();
        long size = Files.size(filePath);
        int partsCount = (int) Math.ceil((double) size / CHUNK_SIZE);
        String filename = filePath.getFileName().toString();

        // Скопируем файл в директорию загрузок, чтобы клиент мог отдавать части другим пирам
        Path targetPath = downloadDir.resolve(filename);
        Files.copy(filePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // Вычисляем checksums для каждой части
        Map<Integer, String> partChecksums = calculatePartChecksums(targetPath, partsCount, size);

        // Создаем список частей
        List<Integer> parts = new ArrayList<>();
        for (int i = 0; i < partsCount; i++) {
            parts.add(i);
        }

        FileInfo fileInfo = new FileInfo(fileId, size, partsCount, parts, partChecksums, filename);
        localFiles.put(fileId, fileInfo);

        logger.info("Added local file: {} (id: {}, {} parts)", filename, fileId, partsCount);
        return fileInfo;
    }

    /**
     * Вычислить контрольные суммы для всех частей файла
     */
    private Map<Integer, String> calculatePartChecksums(Path filePath, int partsCount, long fileSize) throws IOException {
        Map<Integer, String> checksums = new LinkedHashMap<>();
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            byte[] buffer = new byte[CHUNK_SIZE];
            for (int i = 0; i < partsCount; i++) {
                // Вычисляем сколько байтов нужно прочитать
                int toRead = (int) Math.min(CHUNK_SIZE, fileSize - (long) i * CHUNK_SIZE);
                // offset
                raf.seek((long) i * CHUNK_SIZE);
                raf.readFully(buffer, 0, toRead);

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(buffer, 0, toRead);
                byte[] hashBytes = digest.digest();
                StringBuilder sb = new StringBuilder();
                for (byte b : hashBytes) {
                    sb.append(String.format("%02x", b));
                }
                checksums.put(i, sb.toString());
            }
        } catch (Exception e) {
            throw new IOException("Failed to calculate part checksums", e);
        }

        return checksums;
    }

    /**
     * Получить данные части файла с диска
     */
    public byte[] getLocalChunk(String fileId, int partIndex) throws IOException {
        FileInfo fileInfo = localFiles.get(fileId);
        if (fileInfo == null) {
            throw new FileNotFoundException("File not found: " + fileId);
        }

        Path filePath = downloadDir.resolve(fileInfo.filename());
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("File not found on disk: " + filePath);
        }

        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            long offset = (long) partIndex * CHUNK_SIZE;
            raf.seek(offset);

            int chunkSize = (int) Math.min(CHUNK_SIZE, fileInfo.size() - offset);
            byte[] data = new byte[chunkSize];
            raf.readFully(data);

            return data;
        }
    }

    /**
     * Получить список всех локальных файлов
     */
    public List<FileInfo> getLocalFiles() {
        return new ArrayList<>(localFiles.values());
    }

    /**
     * Проверить, есть ли файл локально
     */
    public boolean hasFile(String fileId) {
        return localFiles.containsKey(fileId);
    }

    /**
     * Установить слушатель прогресса загрузки
     */
    public void setProgressListener(DownloadProgressListener listener) {
        this.progressListener = listener;
    }

    /**
     * Получить прогресс загрузки файла
     */
    public int getDownloadProgress(String fileId) {
        FileDownload download = activeDownloads.get(fileId);
        if (download == null) {
            return 0;
        }
        return download.chunks.size();
    }


    /**
     * Внутренний класс для отслеживания загрузки файла
     */
    private static class FileDownload {
        private static final int MAX_RETRY_ATTEMPTS = 3;

        private final FileInfo fileInfo;
        private final Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();
        private final Map<Integer, Integer> retryAttempts = new ConcurrentHashMap<>();

        public FileDownload(FileInfo fileInfo) {
            this.fileInfo = fileInfo;
        }

        public void saveChunk(int partIndex, byte[] data) {
            chunks.put(partIndex, data);
        }

        public byte[] getChunk(int partIndex) {
            return chunks.get(partIndex);
        }

        public boolean isComplete() {
            return chunks.size() == fileInfo.partsCount();
        }

        public List<Integer> getNeededParts() {
            List<Integer> needed = new ArrayList<>();
            for (int i = 0; i < fileInfo.partsCount(); i++) {
                if (!chunks.containsKey(i)) {
                    needed.add(i);
                }
            }
            return needed;
        }

        public boolean canRetry(int partIndex) {
            int attempts = retryAttempts.getOrDefault(partIndex, 0);
            return attempts < MAX_RETRY_ATTEMPTS;
        }

        public void incrementRetryAttempt(int partIndex) {
            retryAttempts.put(partIndex, retryAttempts.getOrDefault(partIndex, 0) + 1);
        }

        public int getRetryAttempt(int partIndex) {
            return retryAttempts.getOrDefault(partIndex, 0);
        }

        public void removeCorruptedChunk(int partIndex) {
            chunks.remove(partIndex);
        }
    }
}
