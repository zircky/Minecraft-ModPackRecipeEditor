package dev.recipeeditor.registry;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
final class TextureResourceServiceTest {
    @Test void boundsPreviewWithoutChangingAspectRatio()throws Exception {
        ImageIO.write(new BufferedImage(512,128,BufferedImage.TYPE_INT_ARGB),"PNG",root.resolve("wide.png").toFile());
        try(final var service=new TextureResourceService()) {
            final var image=ImageIO.read(new java.io.ByteArrayInputStream(service.load(source("wide.png"),root)));
            assertEquals(256,image.getWidth());assertEquals(64,image.getHeight());
        }
    }
    @TempDir Path root;
    private ResourceSource source(final String path) { return new ResourceSource(SourceType.LOCAL_RESOURCE,List.of(),root,path,"test"); }
    @Test void validatesPngCachesAndClears() throws Exception {
        final var path=root.resolve("a.png"); ImageIO.write(new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB),"PNG",path.toFile());
        try(final var service=new TextureResourceService()) {
            final var data=service.load(source("a.png"),root); assertTrue(data.length>0);
            Files.writeString(path,"corrupt"); assertArrayEquals(data,service.load(source("a.png"),root));
            service.clear();assertEquals(0,service.load(source("a.png"),root).length);
        }
        try(final var service=new TextureResourceService()) { assertEquals(0,service.load(source("a.png"),root).length); }
    }
    @Test void missingAndCorruptImagesHavePlaceholder() throws Exception {
        Files.writeString(root.resolve("bad.png"),"not a png");
        try(final var service=new TextureResourceService()) {
            assertEquals(0,service.load(source("missing.png"),root).length); assertEquals(0,service.load(source("bad.png"),root).length);
        }
    }
    @Test void dimensionAndByteLimits() throws Exception {
        ImageIO.write(new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB),"PNG",root.resolve("large.png").toFile());
        try(final var service=new TextureResourceService(10000,32)) { assertEquals(0,service.load(source("large.png"),root).length); }
        try(final var service=new TextureResourceService(10,128)) { assertEquals(0,service.load(source("large.png"),root).length); }
    }
    @Test void traversalReferenceIsRejected() {
        try(final var service=new TextureResourceService()) { assertEquals(0,service.load(source("../outside.png"),root).length); }
    }
    @Test void pngInsideJarIsReadWithoutExtraction() throws Exception {
        final var bytes=new java.io.ByteArrayOutputStream(); ImageIO.write(new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB),"PNG",bytes);
        final var jar=root.resolve("texture.jar");
        try(final var zip=new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("assets/test/textures/item/a.png")); zip.write(bytes.toByteArray()); zip.closeEntry();
        }
        final var source=new ResourceSource(SourceType.MOD_JAR,List.of("test"),jar,"assets/test/textures/item/a.png","test");
        try(final var service=new TextureResourceService()) { assertTrue(service.load(source,root).length>0); }
        assertFalse(Files.exists(root.resolve("assets"))); Files.move(jar,root.resolve("released.jar"));
    }
}
