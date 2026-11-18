package dev.recipeeditor.core.importing;

import java.util.List;
public record MinecraftVersion(String value, List<String> sources) {
    public MinecraftVersion { sources = List.copyOf(sources); }
    public boolean known() { return !value.equals("UNKNOWN"); }
}
