package dev.recipeeditor.recipeengine.editor;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.registry.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** The existing version-specific parsers remain the semantic authority. */
public final class RecipeValidator {
    private final StaticRecipeScanner scanner;
    private final ResourceLayout layout;
    private volatile RegistryIndex registry;
    public RecipeValidator(final List<RecipeParser> parsers,final RegistryIndex registry) {
        this(parsers,registry,ResourceLayout.minecraft1201());
    }
    public RecipeValidator(final List<RecipeParser> parsers,final RegistryIndex registry,final ResourceLayout layout) {
        this.layout=Objects.requireNonNull(layout);
        scanner=new StaticRecipeScanner(parsers,layout);
        this.registry=Objects.requireNonNull(registry);
    }
    public void updateRegistry(final RegistryIndex registry) {this.registry=Objects.requireNonNull(registry);}
    public RecipeValidationResult validate(final ResourceLocation id,final String raw,final RecipeSource source) {
        Objects.requireNonNull(id); Objects.requireNonNull(raw); Objects.requireNonNull(source);
        if(raw.getBytes(StandardCharsets.UTF_8).length>2*1024*1024) throw new IllegalArgumentException("Recipe JSON exceeds 2 MiB");
        final var resource=new ResourceSource(source.type(),source.modIds(),source.archivePath(),layout.recipePath(id),source.discoveryMethod());
        final var parsed=scanner.scan(List.of(new ResourceDocument(resource,raw)),new ArrayList<>());
        if(parsed.isEmpty()) throw new IllegalArgumentException("Invalid recipe path");
        final var original=parsed.getFirst();
        final var recipe=new RecipeDefinition(id,original.type(),original.inputs(),original.outputs(),original.layout(),original.properties(),source,raw,original.status(),original.diagnostics());
        final var issues=new ArrayList<ValidationIssue>();
        recipe.diagnostics().forEach(d->issues.add(new ValidationIssue(recipe.status()==RecipeParseStatus.INVALID?ValidationIssue.Severity.ERROR:ValidationIssue.Severity.WARNING,"JSON",d.message())));
        if(recipe.type().location().isEmpty()) issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR,"type","Valid recipe type is required"));
        recipe.inputs().forEach(input->check(input,issues));
        recipe.outputs().forEach(output->{ if(output instanceof RecipeOutput.Fixed fixed) warn(fixed.id(),"result",issues); });
        return new RecipeValidationResult(recipe,issues);
    }
    public RecipeValidationResult validate(final String id,final String raw,final RecipeSource source) { return validate(ResourceLocation.parse(id),raw,source); }
    private void check(final IngredientDefinition ingredient,final List<ValidationIssue> issues) {
        if(ingredient instanceof IngredientDefinition.Item item) warn(item.id(),"ingredient",issues);
        else if(ingredient instanceof IngredientDefinition.Tag tag) issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING,"ingredient","Tag membership and runtime availability are not confirmed: #"+tag.id()));
        else if(ingredient instanceof IngredientDefinition.Sized sized) check(sized.ingredient(),issues);
        else if(ingredient instanceof IngredientDefinition.Alternatives alternatives) alternatives.choices().forEach(choice->check(choice,issues));
    }
    private void warn(final ResourceLocation id,final String field,final List<ValidationIssue> issues) {
        final boolean confirmed=registry.find(id).stream().anyMatch(e->e.confidence()==DiscoveryConfidence.CONFIRMED_BY_RUNTIME);
        if(!confirmed) issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING,field,"Item "+id+" is not confirmed by runtime registry"+(registry.find(id).isEmpty()?" (not in static index)":"")));
    }
}
