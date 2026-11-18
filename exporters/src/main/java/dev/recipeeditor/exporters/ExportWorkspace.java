package dev.recipeeditor.exporters;

import java.util.List;
public record ExportWorkspace(List<ExportProfile> profiles,List<ExportManifest> manifests) {
    public ExportWorkspace { profiles=List.copyOf(profiles);manifests=List.copyOf(manifests); }
    public ExportWorkspace() {this(List.of(),List.of());}
}
