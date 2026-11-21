package dev.recipeeditor.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Keeps opaque fields and alternatives until the user explicitly replaces a choice. */
public final class IngredientSelectionModel {
    private final ObjectMapper mapper=new ObjectMapper();
    private final List<JsonNode> choices=new ArrayList<>();
    private boolean array;
    public IngredientSelectionModel(final String json) {
        if(json.isBlank())return;
        try{final var node=mapper.readTree(json);array=node.isArray();if(array)node.forEach(choices::add);else choices.add(node);}
        catch(java.io.IOException error){throw new IllegalArgumentException("Invalid ingredient JSON",error);}
    }
    public List<String> choices(){return choices.stream().map(JsonNode::toString).toList();}
    public void replace(final int index,final String json){final var value=read(json);if(index<0||index>=choices.size())choices.add(value);else choices.set(index,value);}
    public void add(final String json){choices.add(read(json));array=true;}
    public void remove(final int index){choices.remove(index);}
    public String json(){if(choices.isEmpty())throw new IllegalArgumentException("Select at least one ingredient");if(!array&&choices.size()==1)return choices.getFirst().toString();final var result=mapper.createArrayNode();choices.forEach(result::add);return result.toString();}
    private JsonNode read(final String json){try{return mapper.readTree(json);}catch(java.io.IOException error){throw new IllegalArgumentException("Invalid ingredient JSON",error);}}
}
