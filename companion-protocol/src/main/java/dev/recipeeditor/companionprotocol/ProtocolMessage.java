package dev.recipeeditor.companionprotocol;
import com.google.gson.JsonObject;
import java.util.Objects;
public record ProtocolMessage(int protocolVersion, MessageType messageType, String requestId, JsonObject payload) {
    public ProtocolMessage {
        Objects.requireNonNull(messageType); Objects.requireNonNull(payload);
        if (requestId==null || !requestId.matches("[a-zA-Z0-9-]{1,64}")) throw new IllegalArgumentException("Invalid request ID");
    }
    public ProtocolMessage(final MessageType type, final String requestId, final JsonObject payload) { this(1,type,requestId,payload); }
}
