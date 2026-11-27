package dev.recipeeditor.registry.rendering;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import dev.recipeeditor.registry.rendering.BlockGeometryModel.Vector;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static dev.recipeeditor.registry.rendering.BlockModelException.Code.*;

/** Resolves the actual item model, never an arbitrary blockstate or particle texture. */
public final class InventoryItemModelResolver {
    private final ResourceAssetCatalog assets;
    private final ResourceJsonReader json=new ResourceJsonReader();
    public InventoryItemModelResolver(final ResourceAssetCatalog assets){this.assets=Objects.requireNonNull(assets);}
    public InventoryItemModel resolve(final ResourceLocation item){
        final var id=new ResourceLocation(item.namespace(),"item/"+item.path());var current=id;final var sources=new ArrayList<ResourceDocument>();final var seen=new HashSet<ResourceLocation>();
        GuiDisplayTransform gui=null;String light=null;boolean generated=false;ObjItemMesh obj=null;
        for(int depth=0;;depth++){
            if(depth>=32)throw new BlockModelException(INVALID_GEOMETRY,"Item parent depth limit");if(!seen.add(current))throw new BlockModelException(PARENT_CYCLE,current.toString());
            if(current.toString().equals("minecraft:builtin/generated")){generated=true;break;}
            if(current.path().startsWith("builtin/"))throw new BlockModelException(UNSUPPORTED_RENDERER,current.toString());
            final var definitions=assets.modelDefinitions(current);
            if(definitions.isEmpty()&&obj!=null&&current.toString().equals("minecraft:block/block"))break;
            if(definitions.isEmpty())throw new BlockModelException(depth==0?MISSING_MODEL:MISSING_PARENT,current.toString());if(definitions.size()!=1)throw new BlockModelException(AMBIGUOUS_SOURCE,current.toString());
            final var doc=definitions.getFirst();sources.add(doc);final var node=read(doc);
            if(node.has("loader")){
                if(!node.path("loader").asText().equals("forge:obj")||obj!=null)
                    throw new BlockModelException(UNSUPPORTED_RENDERER,"Unsupported custom model loader: "+id);
                obj=ObjItemMesh.load(doc,node,assets);
            }
            if(node.has("forge_data")||node.has("overrides")&&!node.path("overrides").isEmpty())throw new BlockModelException(UNSUPPORTED_RENDERER,"Custom ItemStack predicates require Minecraft Client: "+id);
            if(gui==null&&node.path("display").has("gui"))gui=transform(node.path("display").get("gui"));
            if(light==null&&node.has("gui_light")){light=node.path("gui_light").asText();if(!Set.of("front","side").contains(light))throw new BlockModelException(INVALID_GEOMETRY,"Invalid gui_light");}
            if(!node.has("parent"))break;if(!node.get("parent").isTextual())throw new BlockModelException(INVALID_GEOMETRY,"Invalid item parent");current=ResourceLocation.parse(node.get("parent").textValue());
        }
        if(obj!=null)return new InventoryItemModel(item,InventoryItemModel.Kind.OBJ,null,null,
                gui==null?new GuiDisplayTransform(new Vector(30,225,0),new Vector(0,0,0),new Vector(.625,.625,.625),new Vector(0,0,0)):gui,
                light==null?"side":light,sources.stream().map(ResourceDocument::source).toList(),obj);
        if(gui==null)gui=GuiDisplayTransform.identity();if(light==null)light="side";
        final var provenance=sources.stream().map(ResourceDocument::source).toList();
        if(generated){
            final var visual=new ModelTextureResolver(assets).model(id.toString());if(visual.layers().isEmpty()||visual.status()!=ResourceVisual.Status.READY)throw new BlockModelException(MISSING_TEXTURE,visual.explanation());
            return new InventoryItemModel(item,InventoryItemModel.Kind.GENERATED_2D,null,visual,gui,light,provenance);
        }
        return new InventoryItemModel(item,InventoryItemModel.Kind.GEOMETRY,new BlockModelParser(assets).parse(id),null,gui,light,provenance);
    }
    private JsonNode read(final ResourceDocument doc){try{final var node=json.read(doc.text());if(!node.isObject())throw new IllegalArgumentException("Expected object");return node;}catch(Exception error){throw new BlockModelException(INVALID_GEOMETRY,error.getMessage());}}
    private GuiDisplayTransform transform(final JsonNode node){
        if(!node.isObject())throw new BlockModelException(INVALID_GEOMETRY,"Invalid display.gui");
        final var rotation=vector(node,"rotation",new Vector(0,0,0));final var translation=vector(node,"translation",new Vector(0,0,0));final var scale=vector(node,"scale",new Vector(1,1,1));final var right=vector(node,"right_rotation",new Vector(0,0,0));
        return new GuiDisplayTransform(rotation,new Vector(clamp(translation.x()/16,5),clamp(translation.y()/16,5),clamp(translation.z()/16,5)),new Vector(clamp(scale.x(),4),clamp(scale.y(),4),clamp(scale.z(),4)),right);
    }
    private Vector vector(final JsonNode node,final String key,final Vector fallback){if(!node.has(key))return fallback;final var values=node.get(key);if(!values.isArray()||values.size()!=3)throw new BlockModelException(INVALID_GEOMETRY,"Expected GUI vector: "+key);final double[] v=new double[3];for(int i=0;i<3;i++){if(!values.get(i).isNumber())throw new BlockModelException(INVALID_GEOMETRY,"Invalid GUI number");v[i]=values.get(i).doubleValue();}return new Vector(v[0],v[1],v[2]);}
    private double clamp(final double value,final double limit){return Math.max(-limit,Math.min(limit,value));}
}
