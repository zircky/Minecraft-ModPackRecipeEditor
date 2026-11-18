package dev.recipeeditor.modpackparser;

/** Hard limits apply to untrusted input, not to extracted files (archives are never extracted). */
public record ImportLimits(int maxJars, long maxJarBytes, int maxEntries, int maxMetadataBytes, long maxTotalMetadataBytes) {
    public ImportLimits {
        if (maxJars < 1 || maxJarBytes < 1 || maxEntries < 1 || maxMetadataBytes < 1 || maxTotalMetadataBytes < 1)
            throw new IllegalArgumentException("Import limits must be positive");
    }
    public ImportLimits() { this(4096, 256L * 1024 * 1024, 50000, 512 * 1024, 64L * 1024 * 1024); }
}
