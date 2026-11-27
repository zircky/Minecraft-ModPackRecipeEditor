package dev.recipeeditor.integrations.gregtech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.Set;

/** Patches one known GTCEu 7.1.4 slot. Existing chance and wrapper metadata survive replacement. */
public final class GregTechSlotEditing {
    public String replace(final String raw, final String role, final String capability, final int index,
                          final int declaredLimit, final String selection, final int amount) {
        check(role,capability,index);
        if(amount<1)throw new IllegalArgumentException("Amount must be positive");
        final var mapper=new GregTechRecipeMapper();
        final var reference=mapper.object(selection);
        if(reference.size()!=1 || !(reference.has("item")||reference.has("fluid")||reference.has("tag")))
            throw new IllegalArgumentException("Choose one registry reference; use Raw JSON for alternatives");
        final var root=(ObjectNode)mapper.object(raw).deepCopy();
        final var array=array(root,role,capability);
        if(index>array.size() || index>=declaredLimit)throw new IllegalArgumentException("Target slot exceeds the captured capacity");
        final ObjectNode slot;
        if(index==array.size())slot=root.objectNode();
        else {
            if(!array.get(index).isObject() || flat(array.get(index).path("content"),capability).isEmpty())
                throw new IllegalArgumentException("Complex existing content is preserved. Edit it explicitly in Raw JSON.");
            slot=(ObjectNode)array.get(index);
        }
        final var ref=root.objectNode();
        ref.set(reference.has("tag")?"tag":capability,reference.elements().next());
        if(capability.equals("fluid")) {
            final var fluid=root.objectNode();fluid.put("amount",amount);fluid.set("value",ref);slot.set("content",fluid);
        } else if(amount!=1) {
            final var sized=root.objectNode();sized.put("type","gtceu:sized");sized.put("count",amount);sized.set("ingredient",ref);slot.set("content",sized);
        } else slot.set("content",ref);
        if(index==array.size())array.add(slot);
        return root.toString();
    }

    public String remove(final String raw, final String role, final String capability, final int index) {
        check(role,capability,index);
        final var root=(ObjectNode)new GregTechRecipeMapper().object(raw).deepCopy();
        final var array=array(root,role,capability);
        if(index>=array.size())throw new IllegalArgumentException("No such occupied slot");
        array.remove(index);
        return root.toString();
    }

    public String flat(final JsonNode content,final String capability) {
        if(!content.isObject())return "";
        JsonNode reference=content;
        long amount=1;
        if(capability.equals("fluid")) {
            if(content.size()!=2||!content.path("amount").isIntegralNumber()||!content.has("value"))return "";
            amount=content.get("amount").asLong();reference=content.get("value");
        } else if(content.path("type").asText().equals("gtceu:sized")) {
            if(content.size()!=3||!content.path("count").isIntegralNumber()||!content.has("ingredient"))return "";
            amount=content.get("count").asLong();reference=content.get("ingredient");
        }
        if(!reference.isObject()||reference.size()!=1||amount<1)return "";
        final String field=reference.has("tag")?"tag":capability;
        if(!reference.path(field).isTextual())return "";
        final var data=new JsonNodeFactory(false).objectNode();
        data.put(field,reference.get(field).asText());data.put("amount",amount);
        return data.toString();
    }

    private ArrayNode array(final ObjectNode root,final String role,final String capability) {
        if(!root.has(role))root.putObject(role);
        final var group=root.get(role);
        if(!(group instanceof ObjectNode object))throw new IllegalArgumentException("Expected "+role+" object");
        if(!object.has(capability))object.putArray(capability);
        if(!(object.get(capability) instanceof ArrayNode array))throw new IllegalArgumentException("Expected slot array");
        return array;
    }

    private void check(final String role,final String capability,final int index) {
        if(!Set.of("inputs","outputs").contains(role)||!Set.of("item","fluid").contains(capability)||index<0)
            throw new IllegalArgumentException("Unsupported machine slot");
    }
}
