package dev.recipeeditor.companionprotocol;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.util.*;

/** Length-prefixed UTF-8. Rejects duplicate fields, excess nesting and oversized frames before allocation. */
public final class ProtocolCodec {
    public static final int MAX_FRAME_BYTES=1024*1024;
    public static final int MAX_RECORD_BYTES=8*1024*1024;
    public static final long MAX_DATASET_BYTES=128L*1024*1024;
    public static final Gson GSON=new GsonBuilder().disableHtmlEscaping().create();
    public void write(final OutputStream stream,final ProtocolMessage message)throws IOException {
        final byte[] bytes=GSON.toJson(message).getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_FRAME_BYTES)throw new ProtocolException("Frame size limit");
        synchronized(stream){final var out=new DataOutputStream(stream);out.writeInt(bytes.length);out.write(bytes);out.flush();}
    }
    public ProtocolMessage read(final InputStream stream)throws IOException {
        final var in=new DataInputStream(stream);final int size=in.readInt();
        if(size<2||size>MAX_FRAME_BYTES)throw new ProtocolException("Invalid frame size");
        final byte[] bytes=in.readNBytes(size);if(bytes.length!=size)throw new EOFException("Truncated frame");
        try {
            final var root=parse(bytes).getAsJsonObject();
            if(!root.keySet().equals(Set.of("protocolVersion","messageType","requestId","payload")))throw new IllegalArgumentException("Invalid message fields");
            if(!root.get("protocolVersion").isJsonPrimitive()||!root.getAsJsonPrimitive("protocolVersion").isNumber()||!root.get("protocolVersion").getAsString().equals("1"))throw new IllegalArgumentException("Incompatible protocol version");
            return new ProtocolMessage(1,MessageType.valueOf(required(root,"messageType")),required(root,"requestId"),root.getAsJsonObject("payload"));
        }catch(RuntimeException bad){throw new ProtocolException("Invalid protocol message: "+bad.getMessage(),bad);}
    }
    public static String required(final JsonObject json,final String key) {
        final var value=json.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||value.getAsString().isBlank())throw new IllegalArgumentException("Missing string: "+key);return value.getAsString();
    }
    public static JsonElement parse(final byte[] bytes)throws IOException {
        try {
            final String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            try(final var reader=new JsonReader(new StringReader(text))) {
                reader.setLenient(false);final var result=value(reader,0);if(reader.peek()!=JsonToken.END_DOCUMENT)throw new ProtocolException("Trailing JSON");return result;
            }
        }catch(RuntimeException bad){throw new ProtocolException("Invalid JSON",bad);}
    }
    private static JsonElement value(final JsonReader reader,final int depth)throws IOException {
        if(depth>48)throw new ProtocolException("JSON nesting limit");
        return switch(reader.peek()) {
            case BEGIN_OBJECT -> {reader.beginObject();final var o=new JsonObject();while(reader.hasNext()){final var key=reader.nextName();if(key.length()>8192||o.has(key)||o.size()>=200000)throw new ProtocolException("Duplicate/oversized JSON field");o.add(key,value(reader,depth+1));}reader.endObject();yield o;}
            case BEGIN_ARRAY -> {reader.beginArray();final var a=new JsonArray();while(reader.hasNext()){if(a.size()>=200000)throw new ProtocolException("JSON array limit");a.add(value(reader,depth+1));}reader.endArray();yield a;}
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> {final var n=reader.nextString();if(n.length()>128)throw new ProtocolException("Number limit");yield new JsonPrimitive(new java.math.BigDecimal(n));}
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {reader.nextNull();yield JsonNull.INSTANCE;}
            default -> throw new ProtocolException("Invalid JSON token");
        };
    }
}
