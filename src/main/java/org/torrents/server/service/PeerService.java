package org.torrents.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torrents.server.ClientHandler;
import org.torrents.server.ClientListener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PeerService implements ClientListener {
    private static final Logger logger = LoggerFactory.getLogger(PeerService.class);
    Map<String, ClientHandler> peers;
    Set<String> busyPeers;

    public PeerService() {
        busyPeers = Collections.newSetFromMap(new ConcurrentHashMap<>());
        peers = new ConcurrentHashMap<>();
    }

    public void addPeer(String id, ClientHandler handler) {
        peers.put(id, handler);
    }

    public ClientHandler getPeer(String id) {
        return peers.get(id);
    }

    public String chooseFreePeer(List<String> candidates) {
        for (String id : candidates) {
            if (busyPeers.add(id)) {
                return id;
            }
        }
        return null;
    }

    public void releasePeer(String id) {
        busyPeers.remove(id);
    }

    public void onPeerDisconnect(String id) {
        busyPeers.remove(id);
        peers.remove(id);
    }

    public List<ClientHandler> getAllPeers() {
        return new ArrayList<>(peers.values());
    }

    /**
     * Graceful shutdown всех подключенных пиров
     */
    public void shutdownAllPeers() {
        List<ClientHandler> handlers = new ArrayList<>(peers.values());
        for (ClientHandler handler : handlers) {
            try {
                handler.setRunning(false);
                logger.debug("Stopped peer: {}", handler.getClientId());
            } catch (Exception e) {
                logger.error("Error stopping peer {}: {}", handler.getClientId(), e.getMessage());
            }
        }

        peers.clear();
        busyPeers.clear();
        logger.info("All peer connections shut down");
    }

    @Override
    public void onClientDisconnected(String clientId) {
        logger.info("Peer disconnected and removed: {}", clientId);
        onPeerDisconnect(clientId);
    }
}
