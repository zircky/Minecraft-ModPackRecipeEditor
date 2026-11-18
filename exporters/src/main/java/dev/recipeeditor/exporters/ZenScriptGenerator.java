package dev.recipeeditor.exporters;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
/** Known JSON -> data literals, never arbitrary ZenScript expressions. Null is deliberately unsupported. */
public final class ZenScriptGenerator {
    public String data(final String raw){return literal(new ExportText().json(raw));}
    private String literal(final JsonNode node){
        if(node.isTextual())return new ExportText().quote(node.asText());
        if(node.isBoolean())return node.asBoolean()?"true":"false";
        if(node.isIntegralNumber()){if(!node.canConvertToInt())throw new IllegalArgumentException("ZenScript integer outside supported range");return node.asText();}
        if(node.isFloatingPointNumber()){if(!Double.isFinite(node.doubleValue()))throw new IllegalArgumentException("Non-finite number");return "("+node.asText()+" as float)";}
        if(node.isArray()){final var values=new ArrayList<String>();node.forEach(value->values.add(literal(value)));return "["+String.join(", ",values)+"]";}
        if(node.isObject()){final var values=new ArrayList<String>();node.fields().forEachRemaining(e->values.add(new ExportText().quote(e.getKey())+": "+literal(e.getValue())));return "{"+String.join(", ",values)+"}";}
        throw new IllegalArgumentException("Null or unknown ZenScript data value is unsupported");
    }
}
