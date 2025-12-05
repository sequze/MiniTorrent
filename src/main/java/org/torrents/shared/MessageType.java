package org.torrents.shared;

/**
 * Enum representing all message types in the protocol
 */
public enum MessageType {
    REGISTER,
    FILE_LIST,
    REQUEST_FILE,
    SEND_CHUNK,
    COMPLETE,
    ERROR
}

