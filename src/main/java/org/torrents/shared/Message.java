package org.torrents.shared;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Map;
import java.util.Objects;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Message {
    private MessageType type;
    private Map<String, Object> payload;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return type == message.type && Objects.equals(payload, message.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, payload);
    }
}
