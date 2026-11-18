package dev.recipeeditor.registry;

import dev.recipeeditor.core.io.SafeFiles;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Bounded LRU of verified PNG bytes; UI decoding happens only after dimensions have been checked. */
public final class TextureResourceService implements AutoCloseable {
    private final java.util.concurrent.ExecutorService worker=new java.util.concurrent.ThreadPoolExecutor(1,1,0,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<>(128),Thread.ofVirtual().name("texture-preview-",0).factory(),new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    private final int maxBytes;
    private final int maxDimension;
    private final LinkedHashMap<ResourceSource,byte[]> cache=new LinkedHashMap<>(32,0.75f,true);
    public TextureResourceService() { this(2*1024*1024,2048); }
    public TextureResourceService(final int maxBytes,final int maxDimension) { this.maxBytes=maxBytes; this.maxDimension=maxDimension; }
    public java.util.concurrent.CompletableFuture<byte[]> loadAsync(final ResourceSource source,final Path selectedRoot) {
        try { return java.util.concurrent.CompletableFuture.supplyAsync(()->load(source,selectedRoot),worker); }
        catch (java.util.concurrent.RejectedExecutionException error) { return java.util.concurrent.CompletableFuture.completedFuture(new byte[0]); }
    }
    public synchronized byte[] load(final ResourceSource source,final Path selectedRoot) {
        if (cache.containsKey(source)) return cache.get(source).clone();
        byte[] data=new byte[0];
        try {
            if (!new ResourceScanner(new DiscoveryLimits(),ResourceLayout.minecraft1201()).validPath(source.internalPath())) return data;
            final var container=SafeFiles.inside(selectedRoot,source.container());
            if (Files.isDirectory(container)) data=SafeFiles.read(selectedRoot,container.resolve(source.internalPath()),maxBytes);
            else {
                if (Files.size(container)>256L*1024*1024) return data;
                try (final var zip=new ZipFile(container.toFile())) {
                    if (zip.size()>100000) return data;
                    final var entry=zip.getEntry(source.internalPath());
                    if (entry==null || entry.getSize()>maxBytes) return data;
                    try (final var input=zip.getInputStream(entry)) { data=SafeFiles.read(input,maxBytes); }
                }
            }
            if (!validPng(data)) data=new byte[0];
        } catch (IOException | RuntimeException error) { data=new byte[0]; }
        cache.put(source,data);
        while (cache.size()>64) cache.remove(cache.keySet().iterator().next());
        return data.clone();
    }
    private boolean validPng(final byte[] bytes) throws IOException {
        if (bytes.length<24 || bytes[0]!=(byte)137 || bytes[1]!=80 || bytes[2]!=78 || bytes[3]!=71) return false;
        try (final var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            final var readers=ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return false;
            final var reader=readers.next();
            try {
                reader.setInput(stream,true,true);
                final int width=reader.getWidth(0),height=reader.getHeight(0);
                if (width<1 || height<1 || width>maxDimension || height>maxDimension || (long)width*height>1048576) return false;
                return reader.read(0)!=null;
            } finally { reader.dispose(); }
        }
    }
    @Override public synchronized void close() { worker.shutdownNow(); cache.clear(); }
}
