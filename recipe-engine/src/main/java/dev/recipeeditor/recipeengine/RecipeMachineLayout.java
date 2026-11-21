package dev.recipeeditor.recipeengine;

import java.util.*;

/** Machine presentation schema without JavaFX or a dependency on a particular mod API. */
public record RecipeMachineLayout(Style style,List<Group> groups,Map<String,String> properties,Optional<BrewDefinition> brew) {
    public enum Style { LINEAR, BATCH, BREWING }
    public enum Direction { INPUT, OUTPUT, AUXILIARY }
    public enum SlotKind { ITEM, BLOCK, FLUID, CHEMICAL }
    public record Slot(SlotKind kind,String role,String rawJson) {
        public Slot { Objects.requireNonNull(kind);Objects.requireNonNull(role);Objects.requireNonNull(rawJson); }
    }
    public record Group(String label,Direction direction,List<Slot> slots) {
        public Group {Objects.requireNonNull(label);Objects.requireNonNull(direction);slots=List.copyOf(slots);}
    }
    public RecipeMachineLayout {Objects.requireNonNull(style);groups=List.copyOf(groups);properties=Collections.unmodifiableMap(new LinkedHashMap<>(properties));Objects.requireNonNull(brew);}
}
