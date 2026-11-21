package dev.recipeeditor.registry;

import dev.recipeeditor.core.io.SafeFiles;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** One bounded worker; verified first-frame PNGs and composited previews use bounded LRUs. */
public final class TextureResourceService implements AutoCloseable {
    public record Preview(byte[] png, ResourceVisual.Status status, String explanation) {
        public Preview { png=png.clone(); }
        @Override public byte[] png() { return png.clone(); }
    }
    private record Key(Path root, ResourceVisual visual) { }
    private record FrameKey(Path root,ResourceSource source,String expectedHash) { }
    private record Archive(ZipFile zip,long modified,long size) { }
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(128),Thread.ofVirtual().name("texture-preview-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
    private final int maxBytes,maxDimension;
    private final LinkedHashMap<Key,Preview> cache=new LinkedHashMap<>(32,0.75f,true);
    private final LinkedHashMap<FrameKey,BufferedImage> frames=new LinkedHashMap<>(32,0.75f,true);
    private final LinkedHashMap<Path,Archive> archives=new LinkedHashMap<>(4,0.75f,true);
    private final Set<CompletableFuture<Preview>> pending=ConcurrentHashMap.newKeySet();
    private final Map<Path,Set<Path>> vanillaArchives=new LinkedHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong epoch=new java.util.concurrent.atomic.AtomicLong();
    public TextureResourceService() { this(2*1024*1024,2048); }
    public TextureResourceService(final int maxBytes,final int maxDimension) { this.maxBytes=maxBytes;this.maxDimension=maxDimension; }
    public CompletableFuture<Preview> previewAsync(final ResourceVisual visual,final Path root) {
        if(visual.layers().isEmpty())return CompletableFuture.completedFuture(new Preview(new byte[0],visual.status(),visual.explanation()));
        final var result=new CompletableFuture<Preview>();final long request=epoch.get();pending.add(result);
        result.whenComplete((value,error)->pending.remove(result));
        try { worker.execute(()->{if(result.isDone()||request!=epoch.get()){result.cancel(false);return;}result.complete(preview(visual,root));}); }
        catch (RejectedExecutionException error) { result.complete(new Preview(new byte[0],ResourceVisual.Status.RUNTIME_TEXTURE_REQUIRED,"Preview queue is busy; select resource to retry")); }
        return result;
    }
    public CompletableFuture<byte[]> loadAsync(final ResourceSource source,final Path root) {
        return previewAsync(ResourceVisual.legacy(List.of(source)),root).thenApply(Preview::png);
    }
    public byte[] load(final ResourceSource source,final Path root) { return preview(ResourceVisual.legacy(List.of(source)),root).png(); }
    public Preview preview(final ResourceVisual visual,final Path root) {
        if (visual.layers().isEmpty()) return new Preview(new byte[0],visual.status(),visual.explanation());
        final var key=new Key(root.toAbsolutePath().normalize(),visual);
        final long request=epoch.get();
        synchronized(cache) { if (cache.containsKey(key)) return cache.get(key); }
        Preview result;
        try {
            BufferedImage combined=null;
            for (final var layer:visual.layers()) {
                final var frame=frame(layer,root);
                if (combined==null) combined=new BufferedImage(Math.min(256,frame.getWidth()),Math.min(256,frame.getHeight()),BufferedImage.TYPE_INT_ARGB);
                composite(combined,frame,layer.argbTint());
            }
            final var output=new ByteArrayOutputStream();ImageIO.write(combined,"PNG",output);
            result=new Preview(output.toByteArray(),visual.status(),visual.explanation()+"; static frame preview");
        } catch (NoSuchFileException error) { result=new Preview(new byte[0],ResourceVisual.Status.TEXTURE_NOT_FOUND,"Texture not found: "+error.getMessage()); }
        catch (IOException | RuntimeException error) { result=new Preview(new byte[0],ResourceVisual.Status.INVALID_TEXTURE,"Invalid or inaccessible texture: "+error.getMessage()); }
        synchronized(cache) { if(request==epoch.get())cache.put(key,result);while(cache.size()>64) cache.remove(cache.keySet().iterator().next()); }
        return result;
    }
    private BufferedImage frame(final ResourceVisual.Layer layer,final Path root) throws IOException {
        final var source=layer.source();
        final var key=new FrameKey(root.toAbsolutePath().normalize(),source,layer.expectedSha256());final long request=epoch.get();
        synchronized(cache) { if(frames.containsKey(key))return frames.get(key); }
        final byte[] data=read(source,root,source.internalPath(),maxBytes,false);
        if(!layer.expectedSha256().isEmpty())try {
            if(!HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data)).equals(layer.expectedSha256()))throw new NoSuchFileException("Active runtime texture overrides indexed file; runtime/resource-pack texture required");
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        final BufferedImage image=decode(data);
        final byte[] metadata=read(source,root,source.internalPath()+".mcmeta",65536,true);
        if(metadata.length==0) {
            if(image.getHeight()>image.getWidth() && image.getHeight()%image.getWidth()==0)
                throw new IOException("Possible spritesheet has no animation metadata");
            return remember(key,image,request);
        }
        final var json=new ResourceJsonReader().read(new String(metadata,java.nio.charset.StandardCharsets.UTF_8));
        if(!json.has("animation")) return remember(key,image,request);
        final var animation=json.path("animation");
        if(!animation.isObject()) throw new IOException("Animation metadata is not an object");
        final boolean explicitWidth=animation.has("width"),explicitHeight=animation.has("height");
        final int width=animation.path("width").asInt(explicitHeight?image.getWidth():Math.min(image.getWidth(),image.getHeight()));
        final int height=animation.path("height").asInt(explicitWidth?image.getHeight():Math.min(image.getWidth(),image.getHeight()));
        if(width<1||height<1||image.getWidth()%width!=0||image.getHeight()%height!=0) throw new IOException("Invalid animation frame dimensions");
        int frame=0;
        if(animation.has("frames")) {
            if(!animation.path("frames").isArray()) throw new IOException("Invalid animation frames");
            if(!animation.path("frames").isEmpty()) {
                final var first=animation.path("frames").get(0);
                frame=first.isIntegralNumber()?first.intValue():first.path("index").asInt(-1);
            }
        }
        final int columns=image.getWidth()/width,rows=image.getHeight()/height;
        if(frame<0||frame>=columns*rows) throw new IOException("Animation frame outside texture");
        return remember(key,image.getSubimage(frame%columns*width,frame/columns*height,width,height),request);
    }
    private BufferedImage remember(final FrameKey key,final BufferedImage original,final long request) {
        // Copy/cap the frame: a subimage must not retain the entire spritesheet in the cache.
        final double scale=Math.min(1.0,256.0/Math.max(original.getWidth(),original.getHeight()));
        final var frame=new BufferedImage(Math.max(1,(int)Math.round(original.getWidth()*scale)),Math.max(1,(int)Math.round(original.getHeight()*scale)),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<frame.getHeight();y++)for(int x=0;x<frame.getWidth();x++)frame.setRGB(x,y,original.getRGB(x*original.getWidth()/frame.getWidth(),y*original.getHeight()/frame.getHeight()));
        synchronized(cache) { if(request==epoch.get())frames.put(key,frame);while(frames.size()>64)frames.remove(frames.keySet().iterator().next()); }
        return frame;
    }
    private byte[] read(final ResourceSource source,final Path root,final String path,final int limit,final boolean optional) throws IOException {
        if(!new ResourceScanner(new DiscoveryLimits(),ResourceLayout.minecraft1201()).validPath(path)) throw new IOException("Unsafe texture path");
        final Path container;
        if(source.type()==SourceType.VANILLA_ASSET) {
            synchronized(vanillaArchives) {
                final var selected=root.toAbsolutePath().normalize();
                if(!vanillaArchives.containsKey(selected))vanillaArchives.put(selected,new VanillaAssetLocator().approvedArchives(selected));
                while(vanillaArchives.size()>4)vanillaArchives.remove(vanillaArchives.keySet().iterator().next());
                container=source.container().toRealPath();
                if(!vanillaArchives.get(selected).contains(container))throw new IOException("Unapproved Vanilla asset archive");
            }
        } else container=SafeFiles.inside(root,source.container());
        if(Files.isDirectory(container)) {
            final var candidate=container.resolve(path);
            if(optional&&!Files.exists(candidate,LinkOption.NOFOLLOW_LINKS)) return new byte[0];
            final var file=SafeFiles.inside(root,candidate);
            return SafeFiles.read(root,file,limit);
        }
        final long size=Files.size(container),modified=Files.getLastModifiedTime(container).toMillis();
        if(size>256L*1024*1024) throw new IOException("Archive size limit");
        synchronized(archives) {
            var archive=archives.get(container);
            if(archive!=null&&(archive.modified()!=modified||archive.size()!=size)){archive.zip().close();archives.remove(container);archive=null;}
            if(archive==null){archive=new Archive(new ZipFile(container.toFile()),modified,size);archives.put(container,archive);}
            while(archives.size()>4){final var eldest=archives.keySet().iterator().next();archives.remove(eldest).zip().close();}
            final var zip=archive.zip();
            if(zip.size()>100000) throw new IOException("Archive entry limit");
            final var entry=zip.getEntry(path);
            if(entry==null) { if(optional)return new byte[0];throw new NoSuchFileException(path); }
            if(entry.getSize()>limit)throw new IOException("Texture byte limit");
            try(final var input=zip.getInputStream(entry)) { return SafeFiles.read(input,limit); }
        }
    }
    private BufferedImage decode(final byte[] bytes) throws IOException {
        if(bytes.length<24||bytes[0]!=(byte)137||bytes[1]!=80||bytes[2]!=78||bytes[3]!=71) throw new IOException("Not a PNG");
        try(final var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            final var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext()) throw new IOException("No PNG decoder");
            final var reader=readers.next();
            try {
                reader.setInput(stream,true,true);final int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1||height<1||width>maxDimension||height>maxDimension||(long)width*height>1048576) throw new IOException("Texture dimension limit");
                return Objects.requireNonNull(reader.read(0));
            } finally { reader.dispose(); }
        }
    }
    private void composite(final BufferedImage target,final BufferedImage source,final Integer tint) {
        final int color=tint==null?0xffffffff:tint;
        for(int y=0;y<target.getHeight();y++) for(int x=0;x<target.getWidth();x++) {
            final int pixel=source.getRGB(x*source.getWidth()/target.getWidth(),y*source.getHeight()/target.getHeight());
            final int alpha=(pixel>>>24)*(color>>>24)/255;
            final int old=target.getRGB(x,y),oldAlpha=old>>>24;
            final int outAlpha=alpha+oldAlpha*(255-alpha)/255;
            int out=outAlpha<<24;
            if(outAlpha>0) for(final int shift:new int[]{16,8,0}) {
                final int channel=((pixel>>>shift)&255)*((color>>>shift)&255)/255;
                out|=((channel*alpha+((old>>>shift)&255)*oldAlpha*(255-alpha)/255)/outAlpha)<<shift;
            }
            target.setRGB(x,y,out);
        }
    }
    public void clear() {
        synchronized(vanillaArchives){vanillaArchives.clear();}
        epoch.incrementAndGet();pending.forEach(future->future.cancel(false));worker.getQueue().clear();synchronized(cache){cache.clear();frames.clear();}
        try { worker.execute(this::closeArchives); } catch(RejectedExecutionException ignored) { /* close() releases remaining handles. */ }
    }
    private void closeArchives() {
        synchronized(archives){for(final var archive:archives.values())try{archive.zip().close();}catch(IOException ignored){ }archives.clear();}
    }
    @Override public void close() { clear();worker.shutdownNow();closeArchives(); }
}
