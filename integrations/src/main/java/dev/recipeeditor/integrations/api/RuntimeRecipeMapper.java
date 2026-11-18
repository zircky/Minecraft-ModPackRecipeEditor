package dev.recipeeditor.integrations.api;
import dev.recipeeditor.companionprotocol.RuntimeData;
public interface RuntimeRecipeMapper<T> {
    T map(RuntimeData.Recipe recipe);
}
