package dev.recipeeditor.recipeengine;

public enum RecipeFilterMode {
    RECIPE_NAMESPACE, SOURCE_MOD;
    @Override public String toString() {return this==RECIPE_NAMESPACE?"Recipe Namespace":"Source Mod";}
    public boolean matches(final RecipeDefinition recipe,final String value) {
        return this==RECIPE_NAMESPACE?recipe.id().namespace().equals(value)
                :recipe.source().sourceModIds().contains(value)||(value.equals("UNKNOWN")&&recipe.source().sourceModIds().isEmpty());
    }
}
