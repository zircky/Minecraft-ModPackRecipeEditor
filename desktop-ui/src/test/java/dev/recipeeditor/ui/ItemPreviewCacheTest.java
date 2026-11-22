package dev.recipeeditor.ui;

import dev.recipeeditor.ui.rendering.ItemPreviewCache;
import dev.recipeeditor.registry.ResourceKind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ItemPreviewCacheTest {
    private ItemPreviewCache.Key key(final String nbt,final String context,final int size){return new ItemPreviewCache.Key("test:machine",ResourceKind.ITEM,"nbt",nbt,"1.20.1",context,size,"renderer-1");}
    @Test void nbtResourcePacksSizeAndRendererContextCannotShareAnIcon(){final var cache=new ItemPreviewCache(4,100);final var image=new ItemPreviewCache.Image(new byte[]{1},"CLIENT","test");cache.put(key("red","pack-a",64),image);assertTrue(cache.get(key("blue","pack-a",64)).isEmpty());assertTrue(cache.get(key("red","pack-b",64)).isEmpty());assertTrue(cache.get(key("red","pack-a",128)).isEmpty());cache.clear();assertEquals(0,cache.size());}
    @Test void byteBudgetEvictsOldImagesAndDefensiveCopiesProtectOtherScreens(){final byte[] data={1,2,3};final var image=new ItemPreviewCache.Image(data,"CLIENT","test");data[0]=99;final var cache=new ItemPreviewCache(8,5);cache.put(key("a","pack",64),image);assertEquals(1,cache.get(key("a","pack",64)).orElseThrow().png()[0]);cache.put(key("b","pack",64),image);assertEquals(1,cache.size());assertTrue(cache.get(key("a","pack",64)).isEmpty());assertTrue(cache.bytes()<=5);}
}
