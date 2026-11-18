package dev.recipeeditor.exporters;

import com.fasterxml.jackson.databind.*;
import dev.recipeeditor.registry.ResourceJsonReader;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
/** Data is serialized, never inserted as executable user-supplied source. */
final class ExportText {
    private final ObjectMapper mapper=new ObjectMapper();
    String quote(final String value) {
        try {return mapper.writeValueAsString(value).replace("\u2028","\\u2028").replace("\u2029","\\u2029");}
        catch(java.io.IOException error){throw new IllegalArgumentException(error);}
    }
    JsonNode json(final String value) {try{return new ResourceJsonReader().read(value);}catch(java.io.IOException error){throw new IllegalArgumentException(error.getMessage(),error);}}
    String pretty(final String value) {try{return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json(value));}catch(java.io.IOException error){throw new IllegalArgumentException(error);}}
    static String hash(final String value) {return hash(value.getBytes(StandardCharsets.UTF_8));}
    static String hash(final byte[] bytes) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException error){throw new IllegalStateException(error);}}
}
