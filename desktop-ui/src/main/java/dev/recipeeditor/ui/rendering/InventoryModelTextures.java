package dev.recipeeditor.ui.rendering;

import dev.recipeeditor.registry.*;
import dev.recipeeditor.registry.rendering.*;
import java.util.*;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.io.*;
import javax.imageio.ImageIO;

/** Bounded shared texture I/O, active hashes and explicit per-index runtime tint evidence. No FX code. */
final class InventoryModelTextures {
    record Data(Map<ResourceSource,byte[]> images,Map<String,byte[]> tinted,boolean tintUnknown){}
    private final TextureResourceService frames;
    InventoryModelTextures(final TextureResourceService frames){this.frames=frames;}
    Data load(final InventoryItemModel model,final RegistryEntry entry,final Path root)throws Exception{
        final var colors=new HashMap<String,Set<Integer>>();final var hashes=new HashMap<String,String>();
        if(entry!=null){
            final var reader=new ResourceJsonReader();
            if(entry.metadata().containsKey("previewSprites"))for(final var sprite:reader.read(entry.metadata().get("previewSprites")))if(sprite.has("tint")&&sprite.has("tintIndex")){
                final String key=sprite.path("sprite").asText()+"#"+sprite.path("tintIndex").asInt(-1);colors.computeIfAbsent(key,k->new HashSet<>()).add((int)Long.parseUnsignedLong(sprite.path("tint").asText(),16));
            }
            if(entry.metadata().containsKey("textureHashes"))reader.read(entry.metadata().get("textureHashes")).fields().forEachRemaining(e->hashes.put(e.getKey(),e.getValue().asText()));
        }
        final var images=new HashMap<ResourceSource,byte[]>();final var tinted=new HashMap<String,byte[]>();boolean unknown=false;
        if(model.kind()==InventoryItemModel.Kind.GENERATED_2D){
            final var layers=new ArrayList<ResourceVisual.Layer>();int index=0;
            for(final var layer:model.layers().layers()){
                final String id=id(layer.source());final var color=colors.getOrDefault(id+"#"+index++,Set.of());
                layers.add(new ResourceVisual.Layer(layer.source(),color.size()==1?color.iterator().next():null,hashes.getOrDefault(id,"")));
            }
            final var visual=new ResourceVisual(ResourceVisual.Status.READY,layers,"Item model generated layers with available runtime tint evidence");
            final var result=frames.previewAsync(visual,root).get(5,TimeUnit.SECONDS);if(result.png().length==0)throw new IOException(result.explanation());images.put(layers.getFirst().source(),result.png());
        }else for(final var element:model.geometry().elements())for(final var face:element.faces().values()){
            if(!images.containsKey(face.source())){final var visual=new ResourceVisual(ResourceVisual.Status.TEXTURE_SAMPLE,List.of(new ResourceVisual.Layer(face.source(),null,hashes.getOrDefault(face.texture().toString(),""))),"Inventory model per-face texture");final var result=frames.previewAsync(visual,root).get(5,TimeUnit.SECONDS);if(result.png().length==0)throw new IOException(result.explanation());images.put(face.source(),result.png());}
            if(face.tintIndex()<0)continue;final String key=face.texture()+"#"+face.tintIndex();final var color=colors.getOrDefault(key,Set.of());
            if(color.size()!=1){unknown=true;continue;}if(!tinted.containsKey(key))tinted.put(key,color(images.get(face.source()),color.iterator().next()));
        }
        return new Data(Map.copyOf(images),Map.copyOf(tinted),unknown);
    }
    private String id(final ResourceSource source){final String[] parts=source.internalPath().split("/",4);return parts[1]+":"+parts[3].substring(0,parts[3].length()-4);}
    private byte[] color(final byte[] png,final int tint)throws IOException{
        final var image=ImageIO.read(new ByteArrayInputStream(png));if(image==null||image.getWidth()>256||image.getHeight()>256)throw new IOException("Invalid bounded tint texture");
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){final int pixel=image.getRGB(x,y);int output=((pixel>>>24)*(tint>>>24)/255)<<24;for(int shift=0;shift<=16;shift+=8)output|=(((pixel>>>shift)&255)*((tint>>>shift)&255)/255)<<shift;image.setRGB(x,y,output);}
        final var output=new ByteArrayOutputStream();ImageIO.write(image,"PNG",output);return output.toByteArray();
    }
}
