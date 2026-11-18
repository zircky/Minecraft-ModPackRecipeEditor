package dev.recipeeditor.integrations.api;
import java.util.*;
public record IntegrationCapabilities(SupportLevel compatibility, String adapterVersion,
                                     Map<String, SupportLevel> features, List<String> diagnostics) {
    public IntegrationCapabilities { features = Map.copyOf(features); diagnostics = List.copyOf(diagnostics); }
    public boolean enabled(final String feature) { return features.getOrDefault(feature, SupportLevel.UNSUPPORTED) == SupportLevel.SUPPORTED; }
}
