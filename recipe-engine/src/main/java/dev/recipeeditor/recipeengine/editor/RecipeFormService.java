package dev.recipeeditor.recipeengine.editor;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.ResourceJsonReader;
import java.util.*;

/** JSON-tree patches preserve unedited fields, including nested custom payloads. No recipe is exported here. */
public final class RecipeFormService {
    private final ObjectMapper mapper=new ObjectMapper();
    private final Set<String> supported=Set.of("crafting_shaped","crafting_shapeless","smelting","blasting","smoking","campfire_cooking","stonecutting","smithing_transform","smithing_trim");
    public List<String> types() { return supported.stream().sorted().map(type->"minecraft:"+type).toList(); }
    public String type(final String raw) { return ResourceLocation.parse(object(raw).path("type").asText()).toString(); }
    public boolean visualSupported(final String raw) {
        try { final var id=ResourceLocation.parse(type(raw)); return id.namespace().equals("minecraft")&&supported.contains(id.path()); }
        catch(RuntimeException error) { return false; }
    }
    public String template(final String type) {
        ResourceLocation.parse(type); final var root=mapper.createObjectNode(); root.put("type",type);
        return formatNode(root);
    }
    public String format(final String raw) { return formatNode(object(raw)); }
    public Map<String,String> fields(final String raw) {
        final var result=new LinkedHashMap<String,String>(); object(raw).fields().forEachRemaining(e->result.put(e.getKey(),e.getValue().toString())); return Map.copyOf(result);
    }
    public String removeResult(final String raw) { final var root=object(raw); root.remove("result"); if(type(raw).equals("minecraft:stonecutting")) root.remove("count"); return formatNode(root); }
    public String patch(final String raw,final String field,final String jsonValue) {
        final var root=object(raw);
        if(field.equals("type")) throw new IllegalArgumentException("Use explicit Raw JSON editing to change recipe type");
        try { final var value=new ResourceJsonReader().read(jsonValue); if(value==null) throw new IllegalArgumentException("JSON value is missing"); root.set(field,value); }
        catch(java.io.IOException error) { throw new IllegalArgumentException(error.getMessage(),error); }
        return formatNode(root);
    }
    public String result(final String raw,final ResourceLocation item,final int count) {
        if(count<1) throw new IllegalArgumentException("Positive output count required");
        final var root=object(raw); final String type=ResourceLocation.parse(type(raw)).path();
        if(type.equals("smithing_trim")) throw new IllegalArgumentException("Trim output is dynamic");
        if(Set.of("smelting","blasting","smoking","campfire_cooking").contains(type) && !root.path("result").isObject()) {
            if(count!=1)throw new IllegalArgumentException("Minecraft 1.20.1 cooking recipes produce one item");
            root.put("result",item.toString());
        }
        else if(type.equals("stonecutting")) { root.put("result",item.toString()); root.put("count",count); }
        else {
            final var previous=root.get("result");
            final ObjectNode output=previous!=null&&previous.isObject()?((ObjectNode)previous).deepCopy():mapper.createObjectNode();
            output.put("item",item.toString()); output.put("count",count); root.set("result",output);
        }
        return formatNode(root);
    }
    public List<String> grid(final String raw) {
        final var root=object(raw); final var result=new ArrayList<String>(Collections.nCopies(9,""));
        int row=0;
        for(final var pattern:root.path("pattern")) {
            if(row>=3) break;
            for(int col=0;col<Math.min(3,pattern.asText().length());col++) {
                final String symbol=pattern.asText().substring(col,col+1); final var value=root.path("key").get(symbol);
                if(!symbol.equals(" ") && value!=null) result.set(row*3+col,value.toString());
            } row++;
        } return List.copyOf(result);
    }
    public String shaped(final String raw,final List<String> slots) {
        if(slots.size()!=9) throw new IllegalArgumentException("Grid requires 9 slots");
        final var root=object(raw); final var keys=mapper.createObjectNode(); final var pattern=mapper.createArrayNode();
        final var encoded=new LinkedHashMap<JsonNode,String>(); final var cells=new String[9];
        for(int i=0;i<9;i++) {
            if(slots.get(i).isBlank()) { cells[i]=" "; continue; }
            final JsonNode ingredient;
            try { ingredient=new ResourceJsonReader().read(slots.get(i)); } catch(java.io.IOException error) { throw new IllegalArgumentException("Invalid ingredient",error); }
            final String symbol=encoded.computeIfAbsent(ingredient,value->String.valueOf((char)('A'+encoded.size()))); keys.set(symbol,ingredient); cells[i]=symbol;
        }
        int top=3,bottom=-1,left=3,right=-1;
        for(int i=0;i<9;i++) if(!cells[i].equals(" ")) { top=Math.min(top,i/3); bottom=Math.max(bottom,i/3); left=Math.min(left,i%3); right=Math.max(right,i%3); }
        if(bottom<0) { root.set("pattern",pattern); root.set("key",keys); return formatNode(root); }
        for(int row=top;row<=bottom;row++) { final var line=new StringBuilder(); for(int col=left;col<=right;col++) line.append(cells[row*3+col]); pattern.add(line.toString()); }
        root.set("pattern",pattern); root.set("key",keys); return formatNode(root);
    }
    public String ingredient(final ResourceLocation id,final boolean tag) { final var value=mapper.createObjectNode(); value.put(tag?"tag":"item",id.toString()); return value.toString(); }
    public String alternatives(final List<String> choices) {
        if(choices.isEmpty()) throw new IllegalArgumentException("Choose at least one ingredient");
        final var array=mapper.createArrayNode();
        for(final var choice:choices) try { array.add(new ResourceJsonReader().read(choice)); } catch(java.io.IOException error) { throw new IllegalArgumentException(error); }
        return array.toString();
    }
    public List<String> ingredientList(final String raw) {
        final var values=new ArrayList<String>(); object(raw).path("ingredients").forEach(value->values.add(value.toString())); return List.copyOf(values);
    }
    public String shapeless(final String raw,final List<String> values) { return patch(raw,"ingredients",values.isEmpty()?"[]":alternatives(values)); }
    private ObjectNode object(final String raw) {
        if(raw.length()>2*1024*1024) throw new IllegalArgumentException("JSON exceeds editor limit");
        try { final var json=new ResourceJsonReader().read(raw); if(!(json instanceof ObjectNode node)) throw new IllegalArgumentException("Recipe JSON must be an object"); return node; }
        catch(java.io.IOException error) { throw new IllegalArgumentException(error.getMessage(),error); }
    }
    private String formatNode(final JsonNode value) { try { return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value); } catch(java.io.IOException error) { throw new IllegalArgumentException(error); } }
}
