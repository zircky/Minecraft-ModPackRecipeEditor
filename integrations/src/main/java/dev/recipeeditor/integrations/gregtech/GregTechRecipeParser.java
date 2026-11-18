package dev.recipeeditor.integrations.gregtech;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.core.importing.*;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.ValidationIssue;
import java.util.*;
/** Syntax/model validation only. Exact environment capability is checked at editing/export boundaries. */
public final class GregTechRecipeParser implements RecipeParser {
    @Override public boolean supports(final ResourceLocation type) { return type.namespace().equals("gtceu"); }
    @Override public RecipeParseResult parse(final RecipeParseContext context) {
        final var root = new GregTechRecipeMapper().object(context.rawJson());
        if (!root.has("duration") && java.util.stream.Stream.of("inputs","outputs","tickInputs","tickOutputs").noneMatch(root::has)) {
            final var diagnostic=new ImportDiagnostic(ScanStatus.WARNING,context.source().internalPath(),"No verified GT machine structure; original JSON preserved, semantic editing/export unavailable");
            return new RecipeParseResult(new RecipeDefinition(context.id(),context.type(),List.of(),List.of(),new RecipeLayout(),List.of(),context.source(),context.rawJson(),RecipeParseStatus.UNSUPPORTED,List.of(diagnostic)),List.of(diagnostic));
        }
        final var model = new GregTechRecipeMapper().map(context.id(), context.rawJson(), false);
        final var validation = new GregTechRecipeValidator().validate(model);
        final var diagnostics = validation.stream().map(issue -> new ImportDiagnostic(issue.severity() == ValidationIssue.Severity.ERROR ? ScanStatus.ERROR : ScanStatus.WARNING, context.source().internalPath(), issue.field() + ": " + issue.message())).toList();
        final var inputs = model.contents().stream().filter(c -> c.role().equals("inputs") && !c.capability().equals("eu")).map(GregTechContent::ingredient).toList();
        final var outputs = new ArrayList<RecipeOutput>();
        model.contents().stream().filter(c -> c.role().equals("outputs")).forEach(c -> outputs.add(new RecipeOutput.Capability(c.capability(),c.ingredient(),c.rawJson())));
        final boolean invalid = validation.stream().anyMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR);
        final var definition = new RecipeDefinition(context.id(), context.type(), inputs, outputs, new RecipeLayout(),
                List.of(new RecipeProperty("gregtech", model.rawJson())), context.source(), context.rawJson(), invalid ? RecipeParseStatus.INVALID : RecipeParseStatus.PARTIALLY_PARSED, diagnostics);
        return new RecipeParseResult(definition, diagnostics);
    }
}
