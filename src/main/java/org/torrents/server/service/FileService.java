package org.torrents.server.service;

import org.torrents.server.db.Repository;
import org.torrents.shared.schemas.FileInfo;
import org.torrents.shared.schemas.FilePart;

import java.util.List;

/**
 * Сервис для работы с файлами
 */
public class FileService {
    private final Repository repository;

    public FileService(Repository repository) {
        this.repository = repository;
    }

    /**
     * Добавить новый файл для существующего пира
     */
    public void addFileForPeer(String peerId, FileInfo file) {
        repository.addFileForPeer(peerId, file);
    }

    public List<FileInfo> getFiles() {
        return repository.getFiles();
    }

    public FileInfo getFile(String fileId) {
        return repository.getFile(fileId);
    }

    /**
     * Получить часть файла вместе со списком пиров, у которых она есть
     */
    public FilePart getFilePartWithPeers(String fileId, int partIndex) {
        return repository.getFilePartWithPeers(fileId, partIndex);
    }

    /**
     * Зарегистрировать новый пир вместе с его файлами
     */
    public void registerNewPeer(String peerId, List<FileInfo> files) {
        repository.registerPeer(peerId, files);
    }
}