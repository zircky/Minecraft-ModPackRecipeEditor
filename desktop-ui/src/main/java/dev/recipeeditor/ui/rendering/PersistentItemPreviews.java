package dev.recipeeditor.ui.rendering;

import dev.recipeeditor.companionprotocol.ItemPreviewResponse;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.imageio.ImageIO;

/** A bounded record per render identity, independent of the snapshot database. */
final class PersistentItemPreviews {
    enum Status { READY, NOT_COLLECTED, STALE, CORRUPTED, FAILED }
    record Entry(Status status, byte[] png, String diagnostic) {}
    private final Path root;

    PersistentItemPreviews(final Path root) { this.root=root; }

    Entry read(final String scope, final String key, final int size, final String currentContext) {
        final var path=file(scope,key);
        try(final var input=new DataInputStream(Files.newInputStream(path))) {
            final var context=input.readUTF();
            final var failure=input.readUTF();
            final int length=input.readInt();
            if(currentContext!=null&&!currentContext.equals(context))return new Entry(Status.STALE,null,path.toString());
            if(length==0&&input.read()==-1)return new Entry(Status.FAILED,null,failure);
            if(length<33||length>ItemPreviewResponse.MAX_PNG_BYTES)throw new IOException("Invalid PNG size");
            final var checksum=input.readUTF();
            final var bytes=input.readNBytes(length);
            if(bytes.length!=length||input.read()!=-1||!checksum.equals(hash(bytes)))throw new IOException("Preview checksum mismatch");
            ItemPreviewResponse.validatePng(bytes,size);
            return new Entry(Status.READY,bytes,path.toString());
        } catch(NoSuchFileException absent) {
            return new Entry(Status.NOT_COLLECTED,null,path.toString());
        } catch(IOException|IllegalArgumentException invalid) {
            return new Entry(Status.CORRUPTED,null,path+": "+invalid.getMessage());
        }
    }

    void write(final String scope, final String key, final String context, final int size, final byte[] png, final String failure) throws IOException {
        if(png.length>0) {
            ItemPreviewResponse.validatePng(png,size);
            final var decoded=ImageIO.read(new ByteArrayInputStream(png));
            if(decoded==null||decoded.getWidth()!=size||decoded.getHeight()!=size)throw new IOException("Preview PNG cannot be decoded");
        }
        final var path=file(scope,key);
        Files.createDirectories(path.getParent());
        final var temp=Files.createTempFile(path.getParent(),"preview-",".tmp");
        try {
            try(final var output=new DataOutputStream(Files.newOutputStream(temp))) {
                output.writeUTF(context);
                output.writeUTF(failure);
                output.writeInt(png.length);
                if(png.length>0){output.writeUTF(hash(png));output.write(png);}
            }
            try {Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unavailable){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temp);}
    }

    private Path file(final String scope,final String key) {
        final String id=hash(key.getBytes(StandardCharsets.UTF_8));
        return root.resolve(hash(scope.getBytes(StandardCharsets.UTF_8))).resolve(id.substring(0,2)).resolve(id+".bin");
    }

    private String hash(final byte[] bytes) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
