package dev.recipeeditor.registry.rendering;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.registry.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static dev.recipeeditor.registry.rendering.BlockModelException.Code.*;

/** Strict GT8 e24b238 runtime-evidenced templates. Never derives materials, colors or ore bases from IDs. */
public final class GregTechMaterialPreview {
    public record Definition(InventoryItemModel model, Map<ResourceSource,ResourceVisual> textures, String identity) {
        public Definition { textures=Map.copyOf(textures); }
    }
    private final RegistryIndex index;
    private final ResourceJsonReader json=new ResourceJsonReader();
    public GregTechMaterialPreview(final RegistryIndex index){this.index=index;}
    public Definition resolve(final ResourceLocation item){
        final var registered=index.find(item).stream().filter(e->e.kind()==ResourceKind.ITEM&&e.confidence()==DiscoveryConfidence.CONFIRMED_BY_RUNTIME).toList();
        if(registered.size()!=1)throw error("Item registration not unambiguous: "+item);
        final var materials=index.materials().stream().filter(m->m.forms().stream().anyMatch(f->f.kind()==ResourceKind.ITEM&&f.resourceId().equals(item))).toList();
        if(materials.size()!=1)throw error("No unique runtime Material form: "+item);
        final var material=materials.getFirst();final var metadata=material.metadata();
        if(!"gtceu-materials-8-e24b238".equals(metadata.get("adapter"))||!"gtceu-e24b238-layer-argb-v1".equals(metadata.get("preview.rules")))throw error("Fresh GT8 material color/template snapshot required");
        final var forms=read(metadata.get("preview.forms"));final var form=forms.path(item.toString());
        final String type=form.path("iconType").asText();if(!type.matches("[a-z0-9_]+"))throw error("Missing verified MaterialIconType");
        final boolean ore=form.has("oreBaseModel");final var sets=read(metadata.get("preview.iconSets"));
        if(!sets.isArray()||sets.isEmpty()||sets.size()>32)throw error("Invalid icon set chain");
        ResourceLocation template=null;
        for(final var set:sets){final var id=ResourceLocation.parse(set.asText());final var candidate=new ResourceLocation(id.namespace(),(ore?"block/":"item/")+"material_sets/"+id.path()+"/"+type);final var docs=index.assetCatalog().modelDefinitions(candidate);if(docs.size()>1)throw error("Ambiguous material template: "+candidate);if(docs.size()==1){template=candidate;break;}}
        if(template==null)throw error("Material icon set template unavailable");
        final var colorNodes=read(metadata.get("preview.layerArgb"));if(!colorNodes.isArray()||colorNodes.size()!=10)throw error("Missing actual getLayerARGB colors");
        final var colors=new ArrayList<Integer>();for(final var value:colorNodes){if(!value.asText().matches("[a-fA-F0-9]{8}"))throw error("Invalid material ARGB");colors.add((int)Long.parseUnsignedLong(value.asText(),16));}
        final var documents=new ArrayList<>(index.assets().documents());final var templateDoc=index.assetCatalog().modelDefinitions(template).getFirst();
        final var itemModel=new ResourceLocation(item.namespace(),"item/"+item.path());
        if(!index.assetCatalog().modelDefinitions(itemModel).isEmpty())throw error("Physical Item Model exists; do not override its renderer");
        final var textures=new HashMap<ResourceSource,ResourceVisual>();
        final String identity=material.id()+"/"+template+"/"+colors+"/"+form;
        if(!ore){
            documents.add(document(templateDoc.source(),itemModel,"{\"parent\":\""+template+"\"}"));
            final var model=new InventoryItemModelResolver(catalog(documents)).resolve(item);
            if(model.kind()!=InventoryItemModel.Kind.GENERATED_2D)throw error("Material item needs native non-generated renderer");
            final var layers=new ArrayList<ResourceVisual.Layer>();int layer=0;
            for(final var texture:model.layers().layers()){if(layer>=colors.size())throw error("Material color layer limit");layers.add(new ResourceVisual.Layer(texture.source(),colors.get(layer++),texture.expectedSha256()));}
            textures.put(layers.getFirst().source(),new ResourceVisual(ResourceVisual.Status.READY,layers,"GT8 actual material layers; colored once"));
            return new Definition(model,textures,identity);
        }
        final var composite=read(templateDoc.text());
        if(!"forge:composite".equals(composite.path("loader").asText())||!composite.path("item_render_order").toString().equals("[\"base_stone\",\"ore_texture\"]"))throw error("Unsupported ore composition order");
        final var children=composite.path("children");if(children.size()!=2||!children.has("base_stone")||!children.has("ore_texture"))throw error("Unsupported ore children");
        final var base=ResourceLocation.parse(form.path("oreBaseModel").asText());
        documents.add(document(templateDoc.source(),itemModel,"{\"parent\":\""+base+"\"}"));
        final var overlayId=new ResourceLocation(item.namespace(),"mre_material_overlay/"+item.path());documents.add(document(templateDoc.source(),overlayId,children.path("ore_texture").toString()));
        final var assets=catalog(documents);final var model=new InventoryItemModelResolver(assets).resolve(item);final var overlays=new BlockModelParser(assets).parse(overlayId);
        if(model.geometry().elements().size()!=1||overlays.elements().size()>7)throw error("Unsupported ore geometry; native renderer required");
        final var stone=model.geometry().elements().getFirst();if(stone.rotation()!=null)throw error("Rotated ore base requires client");
        for(final var face:stone.faces().entrySet()){
            if(face.getValue().tintIndex()>=0)throw error("Tinted base rock needs native context");
            final var layers=new ArrayList<ResourceVisual.Layer>();layers.add(new ResourceVisual.Layer(face.getValue().source(),null));
            for(final var overlay:overlays.elements()){
                if(!overlay.from().equals(stone.from())||!overlay.to().equals(stone.to())||overlay.rotation()!=null)throw error("Ore overlay geometry differs from base");
                final var layer=overlay.faces().get(face.getKey());if(layer==null)continue;
                if(!layer.uv().equals(face.getValue().uv())||layer.rotation()!=face.getValue().rotation())throw error("Ore overlay UV requires native renderer");
                final int tint=layer.tintIndex();if(tint< -1||tint>=colors.size())throw error("Unsupported emissive ore tint");layers.add(new ResourceVisual.Layer(layer.source(),tint<0?null:colors.get(tint)));
            }
            final var visual=new ResourceVisual(ResourceVisual.Status.READY,layers,"GT8 actual rock + material overlay layers; base remains untinted");
            final var old=textures.putIfAbsent(face.getValue().source(),visual);if(old!=null&&!old.equals(visual))throw error("Different ore faces share texture source; native renderer required");
        }
        return new Definition(model,textures,identity);
    }
    private ResourceAssetCatalog catalog(final List<ResourceDocument> documents){return new ResourceAssetCatalog(new ResourceScanResult(documents,index.assets().textures(),List.of()));}
    private ResourceDocument document(final ResourceSource physical,final ResourceLocation id,final String text){return new ResourceDocument(new ResourceSource(physical.type(),physical.modIds(),physical.container(),"assets/"+id.namespace()+"/models/"+id.path()+".json","GT8 runtime-evidenced delegated template, reconstructed; not a physical file"),text);}
    private JsonNode read(final String text){try{return json.read(Objects.requireNonNull(text));}catch(Exception failure){throw error("Invalid/missing runtime material metadata");}}
    private BlockModelException error(final String reason){return new BlockModelException(UNSUPPORTED_RENDERER,"GT_MATERIAL_RUNTIME_REQUIRED: "+reason);}
}
