package dev.recipeeditor.recipeengine;

import dev.recipeeditor.core.ResourceLocation;
import java.util.*;

/** Overrides are scoped: a project overlay does not prove a Minecraft load-order winner. */
public record RecipeProvenance(ResourceLocation recipeId, Scope scope,
        List<RecipeSource> discoveredDefinitions, Optional<RecipeSource> activeDefinition,
        List<RecipeSource> overriddenDefinitions, ProvenanceConfidence confidence) {
    public enum Scope { STATIC_DISCOVERY, PROJECT, PROJECT_CONFLICT, RUNTIME }
    public RecipeProvenance {
        Objects.requireNonNull(recipeId); Objects.requireNonNull(scope); Objects.requireNonNull(confidence);
        discoveredDefinitions=List.copyOf(discoveredDefinitions); activeDefinition=Objects.requireNonNull(activeDefinition);
        overriddenDefinitions=List.copyOf(overriddenDefinitions);
    }
    public String recipeNamespace() { return recipeId.namespace(); }
    public String declaringSource() { return activeDefinition.map(RecipeSource::declaringSource).orElse("UNKNOWN"); }
    public Set<String> sourceModIds() {
        if(scope==Scope.RUNTIME)return Set.copyOf(activeDefinition.orElseThrow().sourceModIds());
        return candidateSourceModIds();
    }
    public Set<String> candidateSourceModIds() {
        final var result=new TreeSet<String>();
        discoveredDefinitions.forEach(source->result.addAll(source.sourceModIds()));
        return Collections.unmodifiableSet(result);
    }
    public String activeSource() {
        return switch(scope) {
            case STATIC_DISCOVERY -> "UNKNOWN — static candidates; load order not established";
            case PROJECT -> activeDefinition.isPresent()?"Project overlay — runtime winner UNKNOWN":"Deleted in project — runtime winner UNKNOWN";
            case PROJECT_CONFLICT -> "UNKNOWN — project change blocked by source conflict";
            case RUNTIME -> "Runtime observed — declaring source UNKNOWN";
        };
    }
    public String describe() {
        final var text=new StringBuilder("Recipe ID: "+recipeId+"\nRecipe Namespace: "+recipeNamespace()
                +"\nDeclaring Source: "+declaringSource()+"\nActive Source: "+activeSource()
                +"\nScope: "+scope+"\nProvenance Confidence: "+confidence+"\n");
        activeDefinition.ifPresent(source->append(text,"Active definition location",source));
        text.append("\nAll discovered / derived-from definitions (not a load-order ranking):\n");
        discoveredDefinitions.forEach(source->append(text,"Candidate",source));
        text.append("\nOverridden definitions in ").append(scope).append(": ").append(overriddenDefinitions.size()).append('\n');
        overriddenDefinitions.forEach(source->append(text,"Overridden",source));
        return text.toString();
    }
    private void append(final StringBuilder text,final String label,final RecipeSource source) {
        text.append(label).append(":\n  Source Mod IDs (container metadata): ").append(source.sourceModIds().isEmpty()?"UNKNOWN":source.sourceModIds())
                .append("\n  Archive Mod IDs: ").append(source.archiveModIds())
                .append("\n  Declaring Source: ").append(source.declaringSource())
                .append("\n  Physical Source: ").append(source.physicalSource())
                .append("\n  Source File: ").append(source.sourceFile())
                .append("\n  Original File: ").append(source.originalFile())
                .append("\n  Source Type: ").append(source.type())
                .append("\n  Discovery Method: ").append(source.discoveryMethod())
                .append("\n  Confidence: ").append(source.confidence()).append('\n');
    }
}
