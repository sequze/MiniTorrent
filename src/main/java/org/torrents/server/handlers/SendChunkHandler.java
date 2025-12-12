package org.torrents.server.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torrents.server.ClientHandler;
import org.torrents.shared.Message;
import org.torrents.shared.ProtocolUtil;
import org.torrents.shared.schemas.ChunkData;
import org.torrents.shared.schemas.ChunkHeader;

import java.util.concurrent.CompletableFuture;

public class SendChunkHandler implements MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(SendChunkHandler.class);

    @Override
    public void handle(Message message, ClientHandler handler) {
        try {
            ChunkHeader header = MessageParseHelper.parseSendChunkHeader(message);
            byte[] data = ProtocolUtil.readChunkData(handler.getIn(), header.length());
            if (data == null) {
                logger.warn("Received EOF while reading chunk body from client {}", handler.getClientId());
                handler.setRunning(false);
                return;
            }
            String requestId = header.requestId();
            CompletableFuture<ChunkData> f = handler.getPendingRequests().remove(requestId);
            if (f != null) {
                f.complete(new ChunkData(header.fileId(), header.partIndex(), data));
            }
            logger.debug("Received chunk data from {}: fileId={}, partIndex={}",
                handler.getClientId(), header.fileId(), header.partIndex());
        } catch (Exception e) {
            logger.error("Error processing SEND_CHUNK from {}: {}", handler.getClientId(), e.getMessage(), e);
        }
    }
}
