package dev.recipeeditor.registry.rendering;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import java.nio.file.Path;
import java.util.*;

final class MaterialPreviewFixtures {
    static final ResourceLocation ITEM=ResourceLocation.parse("gtceu:fixture_form");
    static ResourceSource source(String path){return new ResourceSource(SourceType.MOD_JAR,List.of("gtceu"),Path.of("fixture.jar"),path,"Synthetic GT ABI fixture, not Minecraft evidence");}
    static ResourceDocument doc(String path,String text){return new ResourceDocument(source("assets/"+path),text);}
    static RegistryIndex index(boolean ore,boolean fresh,String base){
        final var documents=new ArrayList<ResourceDocument>();
        documents.add(doc("minecraft/models/item/generated.json","{\"parent\":\"builtin/generated\"}"));
        documents.add(doc("minecraft/models/block/block.json","{\"display\":{\"gui\":{\"rotation\":[30,225,0],\"scale\":[0.625,0.625,0.625]}}}"));
        documents.add(doc("gtceu/models/item/material_sets/dull/ingot.json","{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"gtceu:item/template\",\"layer1\":\"gtceu:item/highlight\"}}"));
        documents.add(doc("minecraft/models/block/rock.json",cube("minecraft:block/"+base,-1)));
        documents.add(doc("gtceu/models/block/material_sets/dull/ore.json","{\"parent\":\"minecraft:block/block\",\"loader\":\"forge:composite\",\"children\":{\"base_stone\":{},\"ore_texture\":"+cube("gtceu:block/overlay",0)+"},\"item_render_order\":[\"base_stone\",\"ore_texture\"]}"));
        final var entry=new RegistryEntry(ITEM,ResourceKind.ITEM,"Fixture","",Map.of(),List.of(),List.of(),DiscoveryConfidence.CONFIRMED_BY_RUNTIME,"Synthetic fixture");
        final var metadata=new HashMap<String,String>();metadata.put("adapter","gtceu-materials-8-e24b238");if(fresh)metadata.put("preview.rules","gtceu-e24b238-layer-argb-v1");
        metadata.put("preview.iconSets","[\"gtceu:shiny\",\"gtceu:dull\"]");metadata.put("preview.layerArgb","[\"ff20aa40\",\"ffffffff\",\"ffffffff\",\"ffffffff\",\"ffffffff\",\"ffffffff\",\"ffffffff\",\"ffffffff\",\"ffffffff\",\"ffffffff\"]");
        metadata.put("preview.forms","{\""+ITEM+"\":{\"iconType\":\""+(ore?"ore":"ingot")+"\""+(ore?",\"oreBaseModel\":\"minecraft:block/rock\"":"")+"}}");
        final var material=new MaterialDefinition(ResourceLocation.parse("gtceu:fixture_material"),"Fixture","",Map.of(),Map.of(),List.of(),List.of(new MaterialDefinition.Form("gtceu:fixture",ResourceKind.ITEM,ITEM)),metadata);
        final var textures=List.of(source("assets/gtceu/textures/item/template.png"),source("assets/gtceu/textures/item/highlight.png"),source("assets/gtceu/textures/block/overlay.png"),source("assets/minecraft/textures/block/"+base+".png"));
        return new RegistryIndex(List.of(entry),new ResourceScanResult(documents,textures,List.of()),List.of(material));
    }
    static String cube(String texture,int tint){
        final var faces=new ArrayList<String>();for(final var face:List.of("north","south","east","west","up","down"))faces.add("\""+face+"\":{\"texture\":\"#all\",\"tintindex\":"+tint+"}");
        return "{\"parent\":\"minecraft:block/block\",\"textures\":{\"all\":\""+texture+"\"},\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{"+String.join(",",faces)+"}}]}";
    }
}
