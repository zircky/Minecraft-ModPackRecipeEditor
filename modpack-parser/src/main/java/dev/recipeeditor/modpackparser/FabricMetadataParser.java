package dev.recipeeditor.modpackparser;

import java.io.InputStream;
public final class FabricMetadataParser implements ModMetadataParser {
    @Override public boolean supports(final String path) { return path.equals("fabric.mod.json"); }
    @Override public ModMetadataParseResult parse(final InputStream input, final MetadataContext context) {
        return new JsonMetadataReader().parse(input, context, false);
    }
}
