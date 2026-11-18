package dev.recipeeditor.exporters;

import java.nio.file.Path;
import java.util.*;
public record ExportProfile(String id,String name,Path targetDirectory,ExportFormat preferredFormat,OverwritePolicy overwritePolicy,boolean backups,String minecraftVersion,String loader) {
    public ExportProfile {
        if(!id.matches("[a-z0-9]{8,32}") || name.isBlank() || name.length()>120) throw new IllegalArgumentException("Invalid profile ID/name");
        Objects.requireNonNull(targetDirectory);Objects.requireNonNull(preferredFormat);Objects.requireNonNull(overwritePolicy);
        if(!targetDirectory.isAbsolute())throw new IllegalArgumentException("Absolute output directory required");
        if(overwritePolicy==OverwritePolicy.OWNED_UNCHANGED&&!backups)throw new IllegalArgumentException("Replacement requires backups");
    }
}
