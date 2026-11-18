package dev.recipeeditor.companionprotocol;

import com.google.gson.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.*;

/** Bounded fragments of length-prefixed JSON records; a tag/recipe may span frames. */
public final class RecordTransfer {
    private final long byteLimit;
    public RecordTransfer(){this(ProtocolCodec.MAX_DATASET_BYTES);}
    public RecordTransfer(final long byteLimit){if(byteLimit<0)throw new IllegalArgumentException("Negative byte budget");this.byteLimit=Math.min(byteLimit,ProtocolCodec.MAX_DATASET_BYTES);}

    @FunctionalInterface public interface Sender { void send(ProtocolMessage message)throws IOException; }
    @FunctionalInterface public interface Receiver { ProtocolMessage read()throws IOException; }
    public record Received<T>(List<T> records, JsonObject completion) { public Received { records=List.copyOf(records); } }
    public void send(final Sender sender,final String requestId,final MessageType type,final String snapshotId,
            final Iterable<?> rows,final JsonObject metadata,final BooleanSupplier cancelled)throws IOException {
        final var digest=digest();final var buffer=new ByteArrayOutputStream(49152);final long[] total={0};final int[] sequence={0};int count=0;
        for(final var row:rows) {
            if(cancelled.getAsBoolean())throw new InterruptedIOException("Synchronization cancelled");
            final byte[] bytes=ProtocolCodec.GSON.toJson(row).getBytes(StandardCharsets.UTF_8);
            if(bytes.length>ProtocolCodec.MAX_RECORD_BYTES)throw new ProtocolException("Runtime record size limit");
            final byte[] size=ByteBuffer.allocate(4).putInt(bytes.length).array();
            append(sender,requestId,type,snapshotId,size,buffer,digest,total,sequence,cancelled);
            append(sender,requestId,type,snapshotId,bytes,buffer,digest,total,sequence,cancelled);count++;
        }
        flush(sender,requestId,type,snapshotId,buffer,sequence);
        final var done=metadata.deepCopy();done.addProperty("snapshotId",snapshotId);done.addProperty("chunks",sequence[0]);done.addProperty("count",count);done.addProperty("bytes",total[0]);done.addProperty("sha256",HexFormat.of().formatHex(digest.digest()));
        sender.send(new ProtocolMessage(MessageType.SYNC_COMPLETED,requestId,done));
    }
    private void append(final Sender sender,final String request,final MessageType type,final String snapshot,final byte[] bytes,
            final ByteArrayOutputStream buffer,final MessageDigest digest,final long[] total,final int[] sequence,final BooleanSupplier cancelled)throws IOException {
        total[0]+=bytes.length;if(total[0]>byteLimit)throw new ProtocolException("Dataset byte limit");digest.update(bytes);
        int offset=0;while(offset<bytes.length){if(cancelled.getAsBoolean())throw new InterruptedIOException("Cancelled");final int size=Math.min(49152-buffer.size(),bytes.length-offset);buffer.write(bytes,offset,size);offset+=size;if(buffer.size()==49152)flush(sender,request,type,snapshot,buffer,sequence);}
    }
    private void flush(final Sender sender,final String request,final MessageType type,final String snapshot,final ByteArrayOutputStream buffer,final int[] sequence)throws IOException {
        if(buffer.size()==0)return;final var payload=new JsonObject();payload.addProperty("snapshotId",snapshot);payload.addProperty("sequence",sequence[0]++);payload.addProperty("data",Base64.getEncoder().encodeToString(buffer.toByteArray()));buffer.reset();sender.send(new ProtocolMessage(type,request,payload));
    }
    public <T> Received<T> receive(final Receiver receiver,final String request,final MessageType type,final Class<T> recordType,final Consumer<JsonObject> progress)throws IOException {
        final var decoder=new Decoder<T>(recordType);final var digest=digest();int sequence=0;long total=0;String snapshot=null;
        while(true) {
            final var message=receiver.read();if(!message.requestId().equals(request))throw new ProtocolException("Unexpected response request ID");final var p=message.payload();
            if(message.messageType()==MessageType.ERROR)throw new ProtocolException("Companion error: "+p);
            if(message.messageType()==MessageType.SYNC_PROGRESS){progress.accept(p);continue;}
            if(message.messageType()==MessageType.SYNC_COMPLETED) {
                final String id=ProtocolCodec.required(p,"snapshotId");if(snapshot!=null&&!id.equals(snapshot))throw new ProtocolException("Mixed snapshot IDs");
                if(p.get("chunks").getAsInt()!=sequence||p.get("count").getAsInt()!=decoder.rows.size()||p.get("bytes").getAsLong()!=total||!HexFormat.of().formatHex(digest.digest()).equals(ProtocolCodec.required(p,"sha256"))||decoder.pending())throw new ProtocolException("Incomplete/corrupt dataset");
                return new Received<>(decoder.rows,p);
            }
            if(message.messageType()!=type)throw new ProtocolException("Unexpected dataset message");
            final String id=ProtocolCodec.required(p,"snapshotId");if(snapshot==null)snapshot=id;if(!snapshot.equals(id)||p.get("sequence").getAsInt()!=sequence++)throw new ProtocolException("Out-of-order/mixed dataset");
            final byte[] bytes;try{bytes=Base64.getDecoder().decode(ProtocolCodec.required(p,"data"));}catch(IllegalArgumentException bad){throw new ProtocolException("Invalid chunk encoding");}
            if(bytes.length>49152||(total+=bytes.length)>byteLimit)throw new ProtocolException("Chunk/dataset size limit");digest.update(bytes);decoder.accept(bytes);
        }
    }
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static final class Decoder<T> {
        private final Class<T> type;private final List<T> rows=new ArrayList<>();private final ByteBuffer size=ByteBuffer.allocate(4);private byte[] body;private int offset;
        private Decoder(final Class<T> type){this.type=type;}
        private boolean pending(){return body!=null||size.position()!=0;}
        private void accept(final byte[] bytes)throws IOException {
            int at=0;while(at<bytes.length){
                if(body==null){final int n=Math.min(size.remaining(),bytes.length-at);size.put(bytes,at,n);at+=n;if(size.hasRemaining())continue;size.flip();final int length=size.getInt();size.clear();if(length<2||length>ProtocolCodec.MAX_RECORD_BYTES)throw new ProtocolException("Invalid record size");body=new byte[length];offset=0;}
                final int n=Math.min(body.length-offset,bytes.length-at);System.arraycopy(bytes,at,body,offset,n);offset+=n;at+=n;
                if(offset==body.length){try{final T row=Objects.requireNonNull(ProtocolCodec.GSON.fromJson(ProtocolCodec.parse(body),type));if(rows.size()>=200000)throw new ProtocolException("Record count limit");rows.add(row);}catch(RuntimeException bad){throw new ProtocolException("Invalid runtime record",bad);}body=null;}
            }
        }
    }
}
