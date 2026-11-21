package dev.recipeeditor.registry;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class FluidTextureResolverTest {
    @Test void malformedTintAndHashArePlaceholdersInsteadOfImportFailures() {
        final var resolver=new FluidTextureResolver(new ResourceAssetCatalog(new ResourceScanResult(List.of(),List.of(texture()),List.of())));
        assertEquals(ResourceVisual.Status.INVALID_TEXTURE,resolver.resolve(Map.of("stillTexture","test:block/fluid","tintColor","ffffffffffffffff")).status());
        assertEquals(ResourceVisual.Status.INVALID_TEXTURE,resolver.resolve(Map.of("stillTexture","test:block/fluid","textureHashes","{\"test:block/fluid\":\"bad\"}")).status());
    }
    @TempDir Path root;
    private ResourceSource texture() { return new ResourceSource(SourceType.LOCAL_RESOURCE,List.of(),root,"assets/test/textures/block/fluid.png","fixture"); }
    @Test void actualSpriteReferenceAndTintAreAppliedToSelectedAnimationFrame() throws Exception {
        final var image=new BufferedImage(2,4,BufferedImage.TYPE_INT_ARGB);
        for(int x=0;x<2;x++)for(int y=0;y<4;y++)image.setRGB(x,y,y<2?0xffff0000:0xffffffff);
        final var path=root.resolve(texture().internalPath());Files.createDirectories(path.getParent());ImageIO.write(image,"PNG",path.toFile());
        Files.writeString(path.resolveSibling("fluid.png.mcmeta"),"{\"animation\":{\"frames\":[{\"index\":1,\"time\":2},0]}}");
        final var atlas=ResourceFixtures.document("assets/test/atlases/blocks.json","{\"sources\":[{\"type\":\"single\",\"resource\":\"test:block/fluid\",\"sprite\":\"test:fluid_sprite\"}]}");
        final var resolver=new FluidTextureResolver(new ResourceAssetCatalog(new ResourceScanResult(List.of(atlas),List.of(texture()),List.of())));
        final var visual=resolver.resolve(Map.of("stillTexture","test:fluid_sprite","tintColor","ff4080c0"));
        try(final var service=new TextureResourceService()) {
            final var preview=service.preview(visual,root);assertTrue(preview.png().length>0,preview.explanation());
            final var frame=ImageIO.read(new ByteArrayInputStream(preview.png()));assertEquals(2,frame.getHeight());assertEquals(2,frame.getWidth());assertEquals(0xff4080c0,frame.getRGB(0,0));
        }
    }
    @Test void absentReferenceDoesNotGuessFromPngOrFluidId() {
        final var resolver=new FluidTextureResolver(new ResourceAssetCatalog(new ResourceScanResult(List.of(),List.of(texture()),List.of())));
        assertEquals(ResourceVisual.Status.RUNTIME_TEXTURE_REQUIRED,resolver.resolve(Map.of()).status());
        assertEquals(ResourceVisual.Status.TEXTURE_NOT_FOUND,resolver.resolve(Map.of("stillTexture","test:missing")).status());
    }
    @Test void invalidAnimationFrameAndMissingMetadataCannotShowWholeStrip() throws Exception {
        final var path=root.resolve(texture().internalPath());Files.createDirectories(path.getParent());ImageIO.write(new BufferedImage(2,4,BufferedImage.TYPE_INT_ARGB),"PNG",path.toFile());
        try(final var service=new TextureResourceService()) { assertEquals(0,service.load(texture(),root).length); }
        Files.writeString(path.resolveSibling("fluid.png.mcmeta"),"{\"animation\":{\"frames\":[9]}}");
        try(final var service=new TextureResourceService()) { assertEquals(ResourceVisual.Status.INVALID_TEXTURE,service.preview(ResourceVisual.legacy(List.of(texture())),root).status()); }
    }
    @Test void activeResourcePackHashMismatchDoesNotShowAnUnrelatedJarTexture() throws Exception {
        final var path=root.resolve(texture().internalPath());Files.createDirectories(path.getParent());ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB),"PNG",path.toFile());
        final var visual=new ResourceVisual(ResourceVisual.Status.TEXTURE_SAMPLE,List.of(new ResourceVisual.Layer(texture(),null,"0".repeat(64))),"runtime sprite");
        try(final var service=new TextureResourceService()) { final var preview=service.preview(visual,root);assertEquals(0,preview.png().length);assertEquals(ResourceVisual.Status.TEXTURE_NOT_FOUND,preview.status()); }
    }
}
