package dev.recipeeditor.exporters;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
public record ExportManifest(String profileId,Path targetDirectory,Set<ExportFormat> formats,String minecraftVersion,Instant timestamp,Map<String,String> fileHashes,List<String> recipeIds,String status) {
    public ExportManifest { formats=Set.copyOf(formats); fileHashes=Map.copyOf(fileHashes); recipeIds=List.copyOf(recipeIds); }
}
