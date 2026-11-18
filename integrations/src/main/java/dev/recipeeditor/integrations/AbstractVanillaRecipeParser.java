package dev.recipeeditor.integrations;

import com.fasterxml.jackson.databind.JsonNode;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.core.importing.*;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.registry.ResourceJsonReader;
import java.util.*;

abstract class AbstractVanillaRecipeParser implements RecipeParser {
    private final Set<String> types;
    private final Set<String> fields;
    protected AbstractVanillaRecipeParser(final Set<String> types,final Set<String> fields) { this.types=Set.copyOf(types); this.fields=Set.copyOf(fields); }
    @Override public final boolean supports(final ResourceLocation type) { return type.namespace().equals("minecraft") && types.contains(type.path()); }
    @Override public final RecipeParseResult parse(final RecipeParseContext context) {
        final var draft=new Draft(); final var diagnostics=new ArrayList<ImportDiagnostic>();
        RecipeParseStatus status=RecipeParseStatus.PARSED;
        try {
            final var json=new ResourceJsonReader().read(context.rawJson());
            parseBody(json,draft,context);
            final var entries=json.fields();
            while(entries.hasNext()) {
                final var entry=entries.next();
                if (!fields.contains(entry.getKey()) && !Set.of("type","group","category","show_notification").contains(entry.getKey())) draft.partial=true;
                if (!Set.of("type","key","pattern","ingredients","ingredient","result","template","base","addition").contains(entry.getKey()))
                    draft.properties.add(new RecipeProperty(entry.getKey(),entry.getValue().toString()));
            }
            if (draft.partial) {
                status=RecipeParseStatus.PARTIALLY_PARSED;
                diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,context.source().internalPath(),"Custom fields, conditions or ingredient semantics are preserved but not evaluated; indexed matches are static candidates"));
            }
        } catch (Exception error) {
            status=RecipeParseStatus.INVALID; draft.inputs.clear(); draft.outputs.clear(); draft.slots.clear(); draft.pattern.clear();
            diagnostics.add(new ImportDiagnostic(ScanStatus.ERROR,context.source().archivePath()+"!"+context.source().internalPath(),"Invalid vanilla recipe: "+error.getMessage()));
        }
        final var recipe=new RecipeDefinition(context.id(),context.type(),draft.inputs,draft.outputs,new RecipeLayout(draft.pattern,draft.slots),
                draft.properties,context.source(),context.rawJson(),status,diagnostics);
        return new RecipeParseResult(recipe,diagnostics);
    }
    protected abstract void parseBody(JsonNode json,Draft draft,RecipeParseContext context);
    protected final IngredientDefinition ingredient(final JsonNode value,final Draft draft) {
        if (value.isArray()) {
            if (value.isEmpty()) throw new IllegalArgumentException("Empty ingredient alternatives");
            final var choices=new ArrayList<IngredientDefinition>();
            value.forEach(node->choices.add(ingredient(node,draft))); return new IngredientDefinition.Alternatives(choices);
        }
        if (!value.isObject()) throw new IllegalArgumentException("Ingredient must be an object or alternatives array");
        if (value.has("type") && !value.path("type").asText().equals("minecraft:item")) { draft.partial=true; return new IngredientDefinition.Unknown(value.toString()); }
        if (value.has("item")==value.has("tag")) throw new IllegalArgumentException("Ingredient requires exactly one of item/tag");
        if (value.size()>1 && !(value.size()==2 && value.has("type"))) draft.partial=true;
        final var node=value.has("item")?value.path("item"):value.path("tag");
        if (!node.isTextual()) throw new IllegalArgumentException("Ingredient ID must be text");
        final var id=ResourceLocation.parse(node.asText());
        return value.has("item")?new IngredientDefinition.Item(id):new IngredientDefinition.Tag(id);
    }
    protected final RecipeOutput.Fixed output(final JsonNode value,final boolean stringAllowed,final boolean copiesBase,final Draft draft) {
        if (value.isTextual() && stringAllowed) return new RecipeOutput.Fixed(ResourceLocation.parse(value.asText()),1,copiesBase,value.toString());
        if (!value.isObject() || !value.path("item").isTextual()) throw new IllegalArgumentException("Result requires item object");
        final int count=value.has("count")?positiveInteger(value.path("count"),"result count"):1;
        final var names=value.fieldNames(); while(names.hasNext()) if (!Set.of("item","count").contains(names.next())) draft.partial=true;
        return new RecipeOutput.Fixed(ResourceLocation.parse(value.path("item").asText()),count,copiesBase,value.toString());
    }
    protected final int positiveInteger(final JsonNode value,final String name) {
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue()<1) throw new IllegalArgumentException("Invalid "+name);
        return value.intValue();
    }
    protected static final class Draft {
        final List<IngredientDefinition> inputs=new ArrayList<>();
        final List<RecipeOutput> outputs=new ArrayList<>();
        final List<String> pattern=new ArrayList<>();
        final Map<String,IngredientDefinition> slots=new LinkedHashMap<>();
        final List<RecipeProperty> properties=new ArrayList<>();
        boolean partial;
    }
}
