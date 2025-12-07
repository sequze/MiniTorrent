package org.torrents.shared;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

@AllArgsConstructor@Getter@ToString
public class Message {
    private MessageType type;
    private Map<String, Object> payload;
}
