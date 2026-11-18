package dev.recipeeditor.recipeengine.editor;

import com.fasterxml.jackson.databind.JsonNode;
import dev.recipeeditor.registry.ResourceJsonReader;
import java.util.*;
/** Structural JSON paths include all unknown fields; whitespace/key-order changes are not semantic edits. */
public final class RecipeDiffService {
    public record Difference(String path,String before,String after) { }
    public List<Difference> compare(final String before,final String after) {
        try { final var reader=new ResourceJsonReader(); final var result=new ArrayList<Difference>(); diff("$",reader.read(before),reader.read(after),result); return List.copyOf(result); }
        catch(java.io.IOException error) { throw new IllegalArgumentException("Cannot compare invalid JSON",error); }
    }
    private void diff(final String path,final JsonNode before,final JsonNode after,final List<Difference> result) {
        if(Objects.equals(before,after)) return;
        if(before!=null && after!=null && before.isObject() && after.isObject()) {
            final var fields=new TreeSet<String>(); before.fieldNames().forEachRemaining(fields::add); after.fieldNames().forEachRemaining(fields::add);
            fields.forEach(key->diff(path+"/"+key.replace("~","~0").replace("/","~1"),before.get(key),after.get(key),result));
        } else if(before!=null && after!=null && before.isArray() && after.isArray()) {
            for(int i=0;i<Math.max(before.size(),after.size());i++) diff(path+"/"+i,before.get(i),after.get(i),result);
        } else result.add(new Difference(path,before==null?"<absent>":before.toString(),after==null?"<absent>":after.toString()));
    }
}
