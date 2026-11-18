package dev.recipeeditor.companionprotocol;
import java.io.IOException;
public final class ProtocolException extends IOException {
    public ProtocolException(final String message) { super(message); }
    public ProtocolException(final String message, final Throwable cause) { super(message,cause); }
}
