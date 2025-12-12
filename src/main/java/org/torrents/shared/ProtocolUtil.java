package org.torrents.shared;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
/**
 * Общий класс для обработки сообщений между клиентом и сервером
 */
public class ProtocolUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static byte[] serializeMessage(Message message) throws IOException {
        String json = objectMapper.writeValueAsString(message);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static Message deserializeMessage(byte[] jsonBytes) throws IOException {
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        return objectMapper.readValue(json, Message.class);
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
        try {
            int length = dis.readInt();
            if (length <= 0 || length > 1_000_000)
                throw new IOException("Invalid message length " + length);

            byte[] jsonBytes = new byte[length];
            dis.readFully(jsonBytes);
            return deserializeMessage(jsonBytes);
        } catch (EOFException e) {
            // Клиент отключился
            return null;
        }
    }

    /**
     * Отправить сообщение с данными чанка
     */
    public static void sendChunkData(OutputStream out, Message message, byte[] data) throws IOException {
        String json = objectMapper.writeValueAsString(message);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(jsonBytes.length);
        dos.write(jsonBytes);
        dos.write(data);
        dos.flush();
    }

    public static byte[] readChunkData(InputStream in, int length) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        byte[] data = new byte[length];
        try {
            dis.readFully(data);
            return data;
        } catch (EOFException e) {
            return null;
        }
    }

}
