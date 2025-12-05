package org.torrents.shared;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ProtocolUtil {

    private static final Gson gson = new GsonBuilder().create();

    private static byte[] serializeMessage(Message message) {
        String json = gson.toJson(message);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static Message deserializeMessage(byte[] jsonBytes) {
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        return gson.fromJson(json, Message.class);
    }
    public static void sendMessage(OutputStream out, Message message) throws IOException {
        byte[] jsonBytes = serializeMessage(message);
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(jsonBytes.length);
        dos.write(jsonBytes);
        dos.flush();
    }

    public static Message receiveMessage(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);

        int length = dis.readInt();
        if (length <= 0 || length > 1_000_000)
            throw new IOException("Invalid message length " + length);

        byte[] jsonBytes = new byte[length];
        dis.readFully(jsonBytes);
        return deserializeMessage(jsonBytes);
    }

    public static void sendChunkData(OutputStream out, String fileId, int partIndex, byte[] data) throws IOException {
        Message header = new Message(MessageType.SEND_CHUNK, Map.of(
                "fileId", fileId,
                "partIndex", partIndex,
                "length", data.length
        ));

        String json = gson.toJson(header);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(jsonBytes.length);
        dos.write(jsonBytes);
        dos.write(data);
        dos.flush();
    }

    public static ChunkData receiveChunkData(InputStream in) throws IOException {

        DataInputStream dis = new DataInputStream(in);

        int jsonLength = dis.readInt();
        byte[] jsonBytes = new byte[jsonLength];
        dis.readFully(jsonBytes);

        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        Map<String, Object> header = gson.fromJson(json, Map.class);

        Map<String, Object> payload = (Map<String, Object>) header.get("payload");

        String fileId = (String) payload.get("fileId");
        int partIndex = ((Double) payload.get("partIndex")).intValue();
        int length = ((Double) payload.get("length")).intValue();

        byte[] data = new byte[length];
        dis.readFully(data);

        return new ChunkData(fileId, partIndex, data);
    }

    // Hold chunk
    @AllArgsConstructor
    @Getter
    public static class ChunkData {
        private final String fileId;
        private final int partIndex;
        private final byte[] data;
    }
}
