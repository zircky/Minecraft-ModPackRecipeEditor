package dev.recipeeditor.registry.rendering;

import com.fasterxml.jackson.databind.JsonNode;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import java.util.*;
import dev.recipeeditor.registry.rendering.BlockGeometryModel.Vector;
import static dev.recipeeditor.registry.rendering.BlockGeometryModel.*;
import static dev.recipeeditor.registry.rendering.BlockModelException.Code.*;

/** Resolves vanilla JSON parents and assets without inventing load order or cube geometry. */
public final class BlockModelParser {
    private final ResourceAssetCatalog catalog;
    private final ResourceJsonReader json=new ResourceJsonReader();
    public BlockModelParser(final ResourceAssetCatalog catalog){this.catalog=Objects.requireNonNull(catalog);}
    public BlockGeometryModel parse(final ResourceLocation id) {
        final var chain=new ArrayList<ResourceDocument>();final var seen=new HashSet<ResourceLocation>();var current=id;
        for(int depth=0;;depth++){
            if(!seen.add(current))throw new BlockModelException(PARENT_CYCLE,current.toString());
            if(depth>=32)throw new BlockModelException(INVALID_GEOMETRY,"Parent depth limit");
            final var definitions=catalog.modelDefinitions(current);
            if(definitions.isEmpty())throw new BlockModelException(depth==0?MISSING_MODEL:MISSING_PARENT,current.toString());
            if(definitions.size()!=1)throw new BlockModelException(AMBIGUOUS_SOURCE,current.toString());
            final var document=definitions.getFirst();chain.add(document);final var node=read(document);
            rejectExtensions(node);if(!node.has("parent"))break;
            final String parent=node.path("parent").asText();if(parent.contains("builtin/")||parent.equals("item/generated"))throw new BlockModelException(UNSUPPORTED_RENDERER,parent);
            current=location(parent);
        }
        final var textures=new HashMap<String,String>();JsonNode elements=null;
        for(int i=chain.size()-1;i>=0;i--){final var node=read(chain.get(i));node.path("textures").fields().forEachRemaining(e->{if(!e.getValue().isTextual())throw new BlockModelException(MISSING_TEXTURE,e.getKey());textures.put(e.getKey(),e.getValue().textValue());});if(node.has("elements"))elements=node.get("elements");}
        if(elements==null||!elements.isArray()||elements.isEmpty()||elements.size()>256)throw new BlockModelException(UNSUPPORTED_RENDERER,"No bounded JSON elements: "+id);
        final var result=new ArrayList<Element>();for(final var element:elements)result.add(element(element,textures));
        return new BlockGeometryModel(id,result,chain.stream().map(ResourceDocument::source).toList());
    }
    private JsonNode read(final ResourceDocument document){try{final var node=json.read(document.text());if(!node.isObject())throw new IllegalArgumentException("Not an object");return node;}catch(Exception error){throw new BlockModelException(INVALID_GEOMETRY,document.source().internalPath()+": "+error.getMessage());}}
    private void rejectExtensions(final JsonNode node){if(node.has("loader")||node.has("forge_data"))throw new BlockModelException(UNSUPPORTED_RENDERER,"Custom model loader/face data requires a runtime provider");}
    private Element element(final JsonNode node,final Map<String,String> textures){
        rejectExtensions(node);final var from=vector(node.path("from"));final var to=vector(node.path("to"));
        Rotation rotation=null;if(node.has("rotation")){final var value=node.get("rotation");try{rotation=new Rotation(vector(value.path("origin")),Axis.valueOf(value.path("axis").asText().toUpperCase(Locale.ROOT)),number(value.path("angle")),bool(value,"rescale",false));}catch(BlockModelException error){throw error;}catch(IllegalArgumentException error){throw new BlockModelException(INVALID_GEOMETRY,"Invalid rotation axis");}}
        final var faces=new EnumMap<Direction,Face>(Direction.class);final var definitions=node.path("faces");if(!definitions.isObject())throw new BlockModelException(INVALID_GEOMETRY,"Missing faces");
        definitions.fields().forEachRemaining(entry->{
            final var direction=direction(entry.getKey());final var face=entry.getValue();rejectExtensions(face);
            final String texture=resolve(face.path("texture").asText(),textures);final var sources=catalog.texture(texture);
            if(sources.isEmpty())throw new BlockModelException(MISSING_TEXTURE,texture);if(sources.size()!=1)throw new BlockModelException(AMBIGUOUS_SOURCE,texture);
            final var uv=face.has("uv")?uv(face.get("uv")):defaults(direction,from,to);
            final int turn=integer(face,"rotation",0);final int tint=integer(face,"tintindex",-1);
            faces.put(direction,new Face(location(texture),sources.getFirst(),uv,turn,face.has("cullface")?direction(face.get("cullface").asText()):null,tint));
        });
        return new Element(from,to,faces,rotation,bool(node,"shade",true));
    }
    private String resolve(final String value,final Map<String,String> textures){var next=value;final var seen=new HashSet<String>();while(next.startsWith("#")){if(!seen.add(next)||seen.size()>32)throw new BlockModelException(MISSING_TEXTURE,"Cyclic texture reference "+value);next=textures.get(next.substring(1));if(next==null)throw new BlockModelException(MISSING_TEXTURE,value);}if(next.isBlank())throw new BlockModelException(MISSING_TEXTURE,"Empty texture reference");return location(next).toString();}
    private ResourceLocation location(final String value){try{return ResourceLocation.parse(value);}catch(IllegalArgumentException error){throw new BlockModelException(INVALID_GEOMETRY,"Invalid resource ID: "+value);}}
    private Direction direction(final String value){try{return Direction.valueOf(value.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException error){throw new BlockModelException(INVALID_GEOMETRY,"Invalid face direction: "+value);}}
    private Vector vector(final JsonNode node){if(!node.isArray()||node.size()!=3)throw new BlockModelException(INVALID_GEOMETRY,"Expected three coordinates");return new Vector(number(node.get(0)),number(node.get(1)),number(node.get(2)));}
    private double number(final JsonNode node){if(!node.isNumber()||!Double.isFinite(node.doubleValue()))throw new BlockModelException(INVALID_GEOMETRY,"Expected finite number");return node.doubleValue();}
    private int integer(final JsonNode node,final String key,final int fallback){if(!node.has(key))return fallback;if(!node.get(key).isIntegralNumber()||!node.get(key).canConvertToInt())throw new BlockModelException(INVALID_UV,"Expected integer "+key);return node.get(key).intValue();}
    private boolean bool(final JsonNode node,final String key,final boolean fallback){if(!node.has(key))return fallback;if(!node.get(key).isBoolean())throw new BlockModelException(INVALID_GEOMETRY,"Expected boolean "+key);return node.get(key).booleanValue();}
    private UV uv(final JsonNode node){if(!node.isArray()||node.size()!=4)throw new BlockModelException(INVALID_UV,"Expected four UV coordinates");try{return new UV(number(node.get(0)),number(node.get(1)),number(node.get(2)),number(node.get(3)));}catch(BlockModelException error){throw new BlockModelException(INVALID_UV,error.getMessage());}}
    private UV defaults(final Direction face,final Vector a,final Vector b){return switch(face){
        case DOWN->new UV(a.x(),16-b.z(),b.x(),16-a.z());case UP->new UV(a.x(),a.z(),b.x(),b.z());
        case NORTH->new UV(16-b.x(),16-b.y(),16-a.x(),16-a.y());case SOUTH->new UV(a.x(),16-b.y(),b.x(),16-a.y());
        case WEST->new UV(a.z(),16-b.y(),b.z(),16-a.y());case EAST->new UV(16-b.z(),16-b.y(),16-a.z(),16-a.y());};}
}
