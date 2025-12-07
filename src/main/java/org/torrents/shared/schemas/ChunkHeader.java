package org.torrents.shared.schemas;

public record ChunkHeader(String fileId, int partIndex, int length, String requestId) {
}