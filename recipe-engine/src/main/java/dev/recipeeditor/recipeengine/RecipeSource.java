package dev.recipeeditor.recipeengine;

import dev.recipeeditor.registry.*;
import java.nio.file.Path;
import java.util.List;
/** A physical discovery location. modIds describe container metadata, never authorship or namespace. */
public record RecipeSource(SourceType type,List<String> modIds,Path archivePath,String internalPath,String discoveryMethod) {
    public RecipeSource { java.util.Objects.requireNonNull(type); modIds=List.copyOf(modIds); java.util.Objects.requireNonNull(archivePath); java.util.Objects.requireNonNull(internalPath); java.util.Objects.requireNonNull(discoveryMethod); }
    public List<String> archiveModIds() { return type==SourceType.MOD_JAR?modIds:List.of(); }
    public List<String> sourceModIds() { return type==SourceType.RUNTIME?List.of():modIds; }
    public String declaringSource() { return "UNKNOWN"; }
    public String physicalSource() { return type==SourceType.RUNTIME?"UNKNOWN (runtime observation from "+archivePath+")":archivePath.toString().isBlank()?"UNKNOWN (unsaved project)":archivePath.toString(); }
    public String originalFile() { return type==SourceType.RUNTIME||internalPath.isBlank()?"UNKNOWN":internalPath; }
    public String sourceFile() {
        if(type==SourceType.RUNTIME||archivePath.toString().isBlank())return "UNKNOWN";
        if(type==SourceType.MOD_JAR||archivePath.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip"))return archivePath+"!"+internalPath;
        // Project members are logical keys (including ':'), not Windows filesystem paths.
        return archivePath+(internalPath.startsWith("changes/")?"#":"/")+internalPath;
    }
    public ProvenanceConfidence confidence() { return type==SourceType.RUNTIME?ProvenanceConfidence.RUNTIME_OBSERVED_ORIGIN_UNKNOWN:ProvenanceConfidence.PHYSICAL_LOCATION_ONLY; }
    public static RecipeSource from(final ResourceSource source) { return new RecipeSource(source.type(),source.modIds(),source.container(),source.internalPath(),source.method()); }
}
