package dev.recipeeditor.exporters;

import dev.recipeeditor.core.importing.ModLoaderType;
import java.util.Set;

final class KubeJsCompatibility {
    String check(final ExportContext context){
        if(!context.modpack().minecraftVersion().value().equals("1.20.1")||context.modpack().loader().type()!=ModLoaderType.FORGE)return "KubeJS adapter requires Minecraft 1.20.1 Forge";
        final String version=context.modVersion("kubejs");
        return Set.of("2001.6.4-build.120","2001.6.5-build.26").contains(version)?"":"Unsupported or missing KubeJS version: "+version+"; checked versions: 2001.6.4-build.120, 2001.6.5-build.26";
    }
}
