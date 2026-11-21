package dev.recipeeditor.exporters;

import java.nio.file.Path;
import java.util.*;
public record ExportProfile(String id,String name,Path targetDirectory,ExportFormat preferredFormat,OverwritePolicy overwritePolicy,boolean backups,String minecraftVersion,String loader,KubeJsExportMode kubeJsMode) {
    public ExportProfile(final String id,final String name,final Path targetDirectory,final ExportFormat preferredFormat,final OverwritePolicy overwritePolicy,final boolean backups,final String minecraftVersion,final String loader){
        this(id,name,targetDirectory,preferredFormat,overwritePolicy,backups,minecraftVersion,loader,KubeJsExportMode.JAVASCRIPT);
    }
    public ExportProfile {
        if(!id.matches("[a-z0-9]{8,32}") || name.isBlank() || name.length()>120) throw new IllegalArgumentException("Invalid profile ID/name");
        Objects.requireNonNull(targetDirectory);Objects.requireNonNull(preferredFormat);Objects.requireNonNull(overwritePolicy);
        Objects.requireNonNull(kubeJsMode);
        if(!targetDirectory.isAbsolute())throw new IllegalArgumentException("Absolute output directory required");
        if(overwritePolicy==OverwritePolicy.OWNED_UNCHANGED&&!backups)throw new IllegalArgumentException("Replacement requires backups");
    }
    public ExportProfile withKubeJsMode(final KubeJsExportMode mode){return new ExportProfile(id,name,targetDirectory,preferredFormat,overwritePolicy,backups,minecraftVersion,loader,mode);}
}
