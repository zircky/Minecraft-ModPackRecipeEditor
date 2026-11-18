package dev.recipeeditor.modpackparser;

import java.io.InputStream;
public interface ModMetadataParser {
    boolean supports(String metadataPath);
    ModMetadataParseResult parse(InputStream input, MetadataContext context);
}
