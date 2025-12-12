package org.torrents.server.handlers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.torrents.shared.Message;
import org.torrents.shared.schemas.ChunkHeader;
import org.torrents.shared.schemas.ErrorInfo;
import org.torrents.shared.schemas.FileInfo;
import org.torrents.shared.schemas.RequestFile;

import java.util.List;
import java.util.Map;

public class MessageParseHelper {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static List<FileInfo> parseFileList(Message message) {
        Map<String, Object> payload = message.getPayload();
        if (payload == null) throw new IllegalArgumentException("payload is null");

        Object filesObj = payload.get("files");
        if (filesObj == null) return List.of();

        return objectMapper.convertValue(filesObj, new TypeReference<List<FileInfo>>() {});
    }

    public static RequestFile parseFileRequest(Message message) {
        Map<String, Object> payload = message.getPayload();
        if (payload == null) throw new IllegalArgumentException("REQUEST_FILE payload is null");

        return objectMapper.convertValue(payload, RequestFile.class);
    }

    public static ChunkHeader parseSendChunkHeader(Message message) {
        Map<String, Object> payload = message.getPayload();
        if (payload == null) throw new IllegalArgumentException("SEND_CHUNK payload is null");

        return objectMapper.convertValue(payload, ChunkHeader.class);
    }

    public static ErrorInfo parseError(Message message) {
        Map<String, Object> payload = message.getPayload();
        if (payload == null) throw new IllegalArgumentException("ERROR payload is null");

        return objectMapper.convertValue(payload, ErrorInfo.class);
    }

    /**
     * Парсинг сообщения ADD_FILE
     */
    public static FileInfo parseAddFile(Message message) {
        Map<String, Object> payload = message.getPayload();
        if (payload == null) throw new IllegalArgumentException("ADD_FILE payload is null");

        String fileId = (String) payload.get("fileId");
        Number sizeNum = (Number) payload.get("size");
        long size = sizeNum != null ? sizeNum.longValue() : 0L;
        Number partsCountNum = (Number) payload.get("partsCount");
        int partsCount = partsCountNum != null ? partsCountNum.intValue() : 0;

        Map<String, String> partsMap = (Map<String, String>) payload.get("parts");

        // Преобразуем Map<String, String> в Map<Integer, String>
        Map<Integer, String> partChecksums = new java.util.HashMap<>();
        if (partsMap != null) {
            for (Map.Entry<String, String> entry : partsMap.entrySet()) {
                partChecksums.put(Integer.parseInt(entry.getKey()), entry.getValue());
            }
        }

        // Создаем список всех частей
        List<Integer> parts = new java.util.ArrayList<>();
        for (int i = 0; i < partsCount; i++) {
            parts.add(i);
        }

        String filename = payload.get("filename") != null ? (String) payload.get("filename") : "unknown";

        return new FileInfo(fileId, size, partsCount, parts, partChecksums, filename);
    }
}
