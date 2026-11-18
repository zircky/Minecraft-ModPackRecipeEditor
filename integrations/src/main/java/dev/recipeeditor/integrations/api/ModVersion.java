package dev.recipeeditor.integrations.api;
import java.util.regex.Pattern;
/** Parsed numeric version and separate prerelease/build identity. No SNAPSHOT range inference. */
public record ModVersion(int major, int minor, int patch, String qualifier, String build) {
    public static ModVersion parse(final String text) {
        final var matcher = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([^+]+))?(?:\\+(.+))?$").matcher(text);
        if (!matcher.matches()) throw new IllegalArgumentException("Unknown mod version: " + text);
        return new ModVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)), matcher.group(4) == null ? "" : matcher.group(4),
                matcher.group(5) == null ? "" : matcher.group(5));
    }
    public boolean release(final int major, final int minor, final int patch) {
        return this.major == major && this.minor == minor && this.patch == patch && qualifier.isEmpty() && build.isEmpty();
    }
}
