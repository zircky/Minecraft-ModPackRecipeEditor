package dev.recipeeditor.integrations;

import com.fasterxml.jackson.databind.JsonNode;
import dev.recipeeditor.recipeengine.*;
import java.util.*;
public final class ShapedRecipeParser extends AbstractVanillaRecipeParser {
    public ShapedRecipeParser() { super(Set.of("crafting_shaped"),Set.of("pattern","key","result")); }
    @Override protected void parseBody(final JsonNode json,final Draft draft,final RecipeParseContext context) {
        final var pattern=json.path("pattern"); final var keys=json.path("key");
        if (!pattern.isArray() || pattern.isEmpty() || pattern.size()>3 || !keys.isObject()) throw new IllegalArgumentException("Invalid shaped pattern/key");
        int width=-1;
        for (final var row:pattern) {
            if (!row.isTextual() || row.asText().isEmpty() || row.asText().length()>3) throw new IllegalArgumentException("Invalid pattern row");
            if (width!=-1 && width!=row.asText().length()) throw new IllegalArgumentException("Pattern rows differ in width");
            width=row.asText().length(); draft.pattern.add(row.asText());
        }
        final var fields=keys.fields();
        while(fields.hasNext()) { final var field=fields.next();
            if (field.getKey().length()!=1 || field.getKey().equals(" ")) throw new IllegalArgumentException("Invalid pattern symbol");
            draft.slots.put(field.getKey(),ingredient(field.getValue(),draft));
        }
        final var used=new HashSet<String>();
        for (final var row:draft.pattern) for (final char symbol:row.toCharArray()) if(symbol!=' ') {
            final String key=String.valueOf(symbol); final var ingredient=draft.slots.get(key);
            if (ingredient==null) throw new IllegalArgumentException("Undefined pattern symbol: "+key);
            used.add(key); draft.inputs.add(ingredient);
        }
        if (used.isEmpty() || !used.equals(draft.slots.keySet())) throw new IllegalArgumentException("Empty pattern or unused symbols");
        draft.outputs.add(output(json.path("result"),false,false,draft));
    }
}
