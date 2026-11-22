package dev.recipeeditor.registry.rendering;

/** Straight-alpha color multiplication. RGB input is explicitly opaque; RGBA is never guessed. */
public record MaterialColor(int argb) {
    public static MaterialColor rgb(final int rgb) { return new MaterialColor(0xff000000 | (rgb & 0xffffff)); }
    public static MaterialColor rgba(final int red, final int green, final int blue, final int alpha) {
        for (final int channel : new int[]{red, green, blue, alpha})
            if (channel < 0 || channel > 255) throw new IllegalArgumentException("Color channel outside 0..255");
        return new MaterialColor(alpha << 24 | red << 16 | green << 8 | blue);
    }
    public int multiply(final int pixel) {
        int result = (pixel >>> 24) * (argb >>> 24) / 255 << 24;
        for (int shift = 0; shift <= 16; shift += 8)
            result |= ((pixel >>> shift & 255) * (argb >>> shift & 255) / 255) << shift;
        return result;
    }
}
