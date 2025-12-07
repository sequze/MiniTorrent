package org.torrents.shared.schemas;

public record ChunkData(
        String fileId, int partIndex, byte[] data) {
}
