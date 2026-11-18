package dev.recipeeditor.registry;

public record DiscoveryLimits(int maxDocuments, int maxJsonBytes, long maxTotalBytes, int maxArchiveEntries, long maxArchiveBytes) {
    public DiscoveryLimits {
        if (maxDocuments<1 || maxJsonBytes<1 || maxTotalBytes<1 || maxArchiveEntries<1 || maxArchiveBytes<1) throw new IllegalArgumentException("Positive limits required");
    }
    public DiscoveryLimits() { this(150000,2*1024*1024,192L*1024*1024,100000,256L*1024*1024); }
}
