package dev.recipeeditor.ui.rendering;

import dev.recipeeditor.registry.ResourceKind;
import java.util.*;
/** LRU bounded by both count and encoded bytes; cache identity includes every rendering input. */
public final class ItemPreviewCache {
    public record Key(String resourceId,ResourceKind kind,String variant,String nbtDigest,String minecraftVersion,String resourcePackContext,int size,String rendererVersion){}
    public record Image(byte[] png,String source,String diagnostic){public Image{png=png.clone();Objects.requireNonNull(source);Objects.requireNonNull(diagnostic);}@Override public byte[] png(){return png.clone();}}
    private final int capacity,maxBytes;
    private int bytes;
    private final LinkedHashMap<Key,Image> entries=new LinkedHashMap<>(32,0.75f,true);
    public ItemPreviewCache(final int capacity,final int maxBytes){if(capacity<1||maxBytes<1)throw new IllegalArgumentException("Positive cache bounds required");this.capacity=capacity;this.maxBytes=maxBytes;}
    public synchronized Optional<Image> get(final Key key){return Optional.ofNullable(entries.get(key));}
    public synchronized void put(final Key key,final Image image){final var previous=entries.remove(key);if(previous!=null)bytes-=previous.png.length;if(image.png.length>maxBytes)return;entries.put(key,image);bytes+=image.png.length;while(entries.size()>capacity||bytes>maxBytes){final var oldest=entries.pollFirstEntry();bytes-=oldest.getValue().png.length;}}
    public synchronized void clear(){entries.clear();bytes=0;}
    synchronized void clearSession(){
        final var iterator=entries.entrySet().iterator();
        while(iterator.hasNext()) {
            final var entry=iterator.next();
            if(entry.getKey().resourcePackContext().startsWith("snapshot-preview:"))continue;
            bytes-=entry.getValue().png.length;
            iterator.remove();
        }
    }
    public synchronized int size(){return entries.size();}public synchronized int bytes(){return bytes;}
}
