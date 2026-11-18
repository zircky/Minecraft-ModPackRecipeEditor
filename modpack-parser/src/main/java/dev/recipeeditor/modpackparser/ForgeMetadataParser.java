package dev.recipeeditor.modpackparser;

import dev.recipeeditor.core.importing.ModLoaderType;
import java.io.InputStream;
public final class ForgeMetadataParser implements ModMetadataParser {
    @Override public boolean supports(final String path) { return path.equals("META-INF/mods.toml"); }
    @Override public ModMetadataParseResult parse(final InputStream input, final MetadataContext context) {
        return new TomlMetadataReader().parse(input, context, ModLoaderType.FORGE);
    }
}
