package dev.recipeeditor.recipeengine;
import dev.recipeeditor.registry.LocalizationService;
/** Applied on the indexing worker, reusing the already parsed localization evidence. */
public final class RecipeSemanticLocalization {
    public RecipeDefinition localize(final RecipeDefinition recipe,final LocalizationService localization) {
        if(recipe.outputs().stream().noneMatch(RecipeOutput.Brew.class::isInstance))return recipe;
        final var outputs=recipe.outputs().stream().map(output->{if(output instanceof RecipeOutput.Brew value){final var brew=value.definition();return (RecipeOutput)new RecipeOutput.Brew(new BrewDefinition(brew.id(),brew.translationKey(),localization.translations(brew.translationKey())));}return output;}).toList();
        final var machine=recipe.layout().machine().map(value->new RecipeMachineLayout(value.style(),value.groups(),value.properties(),outputs.stream().filter(RecipeOutput.Brew.class::isInstance).map(RecipeOutput.Brew.class::cast).map(RecipeOutput.Brew::definition).findFirst()));
        final var layout=new RecipeLayout(recipe.layout().pattern(),recipe.layout().slots(),machine);
        return new RecipeDefinition(recipe.id(),recipe.type(),recipe.inputs(),outputs,layout,recipe.properties(),recipe.source(),recipe.rawJson(),recipe.status(),recipe.diagnostics());
    }
}
