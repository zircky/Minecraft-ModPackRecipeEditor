package dev.recipeeditor.registry.rendering;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MaterialColorizationTest {
    @Test void rgbDoesNotBecomeTransparentAndPreservesSourceAlpha() {
        assertEquals(0x80204080, MaterialColor.rgb(0x204080).multiply(0x80ffffff));
    }
    @Test void rgbaMultipliesAlphaOnceWithoutDarkeningStraightRgb() {
        assertEquals(0x40204080, MaterialColor.rgba(32,64,128,128).multiply(0x80ffffff));
    }
    @Test void grayscaleAndColorMasksMultiplyPerChannel() {
        assertEquals(0xff402010, MaterialColor.rgb(0x804020).multiply(0xff808080));
        assertEquals(0xff800000, MaterialColor.rgb(0xff0000).multiply(0xff804020));
    }
    @Test void transparentPixelRemainsTransparentAndInputsAreBounded() {
        assertEquals(0, MaterialColor.rgb(0xffffff).multiply(0));
        assertThrows(IllegalArgumentException.class, () -> MaterialColor.rgba(256,0,0,255));
    }
}
