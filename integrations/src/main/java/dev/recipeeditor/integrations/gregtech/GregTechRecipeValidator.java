package dev.recipeeditor.integrations.gregtech;
import dev.recipeeditor.integrations.api.IntegrationRecipeValidator;
import dev.recipeeditor.recipeengine.editor.ValidationIssue;
import java.util.*;
public final class GregTechRecipeValidator implements IntegrationRecipeValidator<GregTechRecipeDefinition> {
    @Override public List<ValidationIssue> validate(final GregTechRecipeDefinition recipe) {
        final var issues = new ArrayList<ValidationIssue>();
        if (recipe.duration().isEmpty() || recipe.duration().getAsInt() < 1) error(issues, "duration", "Positive integral duration is required");
        for (final var content : recipe.contents()) {
            ingredient(content.ingredient(),content.role(),issues);
            if (content.amount().isPresent() && content.amount().getAsLong() < 1) error(issues, content.role(), "Positive content amount required");
            if (content.chance().isPresent() && content.chance().getAsInt() < 0) error(issues, "chance", "Negative chance");
            if (content.maxChance().isPresent() && content.maxChance().getAsInt() < 1) error(issues, "maxChance", "Positive maxChance required");
            if (content.chance().isPresent() && content.maxChance().isPresent() && content.chance().getAsInt() > content.maxChance().getAsInt()) error(issues, "chance", "Chance exceeds declared maxChance");
            if (content.tierChanceBoost().isPresent() && content.tierChanceBoost().getAsInt() < 0) error(issues, "tierChanceBoost", "Negative boost");
            if (content.capability().equals("eu")) {
                final var value = new GregTechRecipeMapper().object(content.rawJson()).path("content");
                if (value.isIntegralNumber()) { if (!value.canConvertToLong() || value.longValue() < 0) error(issues, content.role(), "EU must be nonnegative integral long"); }
                else if (!value.isObject() || !value.path("voltage").isIntegralNumber() || !value.path("voltage").canConvertToLong() || value.path("voltage").longValue() < 0 || (value.has("amperage") && (!value.path("amperage").isIntegralNumber() || value.path("amperage").longValue() < 1))) error(issues, content.role(), "Invalid energy stack");
            }
        }
        for (final var condition : recipe.conditions()) {
            final var value = new GregTechRecipeMapper().object(condition.rawJson());
            if (!value.path("type").isTextual() || value.path("type").asText().isBlank()) error(issues, "conditions", "Condition type required");
            else issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, "conditions", "Condition requires matching installed GT serializer: " + value.path("type").asText()));
        }
        issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, "machine", "Machine/config-specific IO limits and registry availability are not proven by JSON; no universal slot limit applied"));
        return List.copyOf(issues);
    }
    private void ingredient(final dev.recipeeditor.recipeengine.IngredientDefinition value, final String field, final List<ValidationIssue> issues) {
        if(value instanceof dev.recipeeditor.recipeengine.IngredientDefinition.Alternatives alternatives) {
            if(alternatives.choices().isEmpty()) error(issues,field,"Empty ingredient alternatives are invalid");
            alternatives.choices().forEach(choice->ingredient(choice,field,issues));
        } else if(value instanceof dev.recipeeditor.recipeengine.IngredientDefinition.Sized sized) ingredient(sized.ingredient(),field,issues);
        else if(value instanceof dev.recipeeditor.recipeengine.IngredientDefinition.Custom custom && !custom.type().equals("eu"))
            issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING,field,"Custom ingredient representation preserved; semantics require its codec: "+custom.type()));
    }
    private void error(final List<ValidationIssue> issues, final String field, final String message) { issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, field, message)); }
}
