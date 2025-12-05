package org.torrents.shared;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@AllArgsConstructor@Getter
public class Message {
    private MessageType type;
    private Map<String, Object> payload;
}
