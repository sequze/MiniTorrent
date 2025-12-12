package org.torrents.server.db;

import org.torrents.shared.schemas.FileInfo;
import org.torrents.shared.schemas.FilePart;

import java.util.List;

public interface Repository {
    public void registerPeer(String peerId, List<FileInfo> file);
    public void createIfNotExistsFile(FileInfo file);
    public void addFileForPeer(String peerId, FileInfo file);
    public List<FileInfo> getFiles();
    public FileInfo getFile(String fileId);
    public FilePart getFilePartWithPeers(String fileId, int partIndex);
}
