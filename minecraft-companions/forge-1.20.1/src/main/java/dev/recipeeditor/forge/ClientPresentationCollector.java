package dev.recipeeditor.forge;

import dev.recipeeditor.companionprotocol.ProtocolCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** All client APIs run on the client thread, with a 4ms / 64-entry budget per tick. */
@Mod.EventBusSubscriber(modid="mre_companion",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ClientPresentationCollector {
    private final Map<String,Map<String,String>> rows=new HashMap<>();
    private Iterator<Runnable> jobs;
    private CompletableFuture<Map<String,Map<String,String>>> future;
    private final Map<String,String> activeHashes=new HashMap<>(),activePacks=new HashMap<>();
    @SubscribeEvent public static void setup(final FMLClientSetupEvent event) {
        event.enqueueWork(()->{
            final var collector=new ClientPresentationCollector();
            MinecraftForge.EVENT_BUS.addListener(collector::tick);
            RuntimePresentation.install(collector::capture);
        });
    }
    private CompletableFuture<Map<String,Map<String,String>>> capture() {
        final var result=new CompletableFuture<Map<String,Map<String,String>>>();
        Minecraft.getInstance().execute(()->{
            if(future!=null&&!future.isDone()) { result.completeExceptionally(new IllegalStateException("Client presentation collection busy"));return; }
            rows.clear();activeHashes.clear();activePacks.clear();final var work=new ArrayList<Runnable>();
            ForgeRegistries.ITEMS.forEach(item->work.add(()->{
                final var stack=item.getDefaultInstance();final var data=name(item.getName(stack));
                final var model=Minecraft.getInstance().getItemRenderer().getModel(stack,null,null,0);
                sprites(data,model,null,stack);rows.put("ITEM:"+ForgeRegistries.ITEMS.getKey(item),Map.copyOf(data));
            }));
            ForgeRegistries.BLOCKS.forEach(block->work.add(()->{
                final var state=block.defaultBlockState();final var data=name(block.getName());
                sprites(data,Minecraft.getInstance().getBlockRenderer().getBlockModel(state),state,ItemStack.EMPTY);
                rows.put("BLOCK:"+ForgeRegistries.BLOCKS.getKey(block),Map.copyOf(data));
            }));
            ForgeRegistries.FLUIDS.forEach(fluid->work.add(()->{
                final var stack=new FluidStack(fluid,1000);final var data=name(fluid.getFluidType().getDescription(stack));
                final var extension=IClientFluidTypeExtensions.of(fluid);
                final var still=extension.getStillTexture(stack);
                final var flowing=extension.getFlowingTexture(stack);
                if(still!=null)data.put("stillTexture",still.toString());if(flowing!=null)data.put("flowingTexture",flowing.toString());
                recordTexture(data,still==null?List.of():List.of(still.toString()));
                data.put("tintColor",String.format(Locale.ROOT,"%08x",extension.getTintColor(stack)));
                data.put("textureAtlas","minecraft:blocks");
                rows.put("FLUID:"+ForgeRegistries.FLUIDS.getKey(fluid),Map.copyOf(data));
            }));
            jobs=work.iterator();future=result;
        });
        return result;
    }
    private Map<String,String> name(final Component component) {
        final var data=new TreeMap<String,String>();data.put("nameComponent",Component.Serializer.toJson(component));
        if(component.getContents() instanceof TranslatableContents translated)data.put("localizationKey",translated.getKey());
        final String language=Minecraft.getInstance().getLanguageManager().getSelected();
        if(language.equals("en_us")||language.equals("ru_ru"))data.put("name."+language,component.getString());
        data.put("runtimeNameLanguage",language);return data;
    }
    private void sprites(final Map<String,String> data,final BakedModel model,final BlockState state,final ItemStack stack) {
        data.put("previewMode",!stack.isEmpty()&&!model.isGui3d()&&!model.isCustomRenderer()?"LAYERS":"SAMPLE");
        final var layers=new LinkedHashMap<String,Map<String,String>>();
        for(int side=-1;side<6;side++)for(final var quad:model.getQuads(state,side<0?null:Direction.values()[side],RandomSource.create(42))) {
            final String sprite=quad.getSprite().contents().name().toString();
            if(sprite.equals("minecraft:missingno"))continue;
            final var layer=new TreeMap<String,String>();layer.put("sprite",sprite);
            if(quad.isTinted()&&!stack.isEmpty())layer.put("tint",String.format(Locale.ROOT,"%08x",0xff000000|Minecraft.getInstance().getItemColors().getColor(stack,quad.getTintIndex())));
            // Block biome tint depends on position: do not substitute an invented color.
            if(quad.isTinted()&&stack.isEmpty())data.put("tintStatus","Biome/position tint unknown; untinted sample");
            layers.putIfAbsent(sprite+layer.getOrDefault("tint",""),layer);
        }
        if(layers.isEmpty() && model.getParticleIcon()!=null) {
            final String sprite=model.getParticleIcon().contents().name().toString();
            if(!sprite.equals("minecraft:missingno"))layers.put(sprite,Map.of("sprite",sprite));
            data.put("rendererStatus","Particle sample only; RENDERING_UNSUPPORTED");
        }
        if(layers.size()>8) { data.put("rendererStatus","UNSUPPORTED_RENDERER: more than 8 distinct sprites");return; }
        if(!layers.isEmpty()) { data.put("previewSprites",ProtocolCodec.GSON.toJson(layers.values()));recordTexture(data,layers.values().stream().map(layer->layer.get("sprite")).toList()); }
    }
    private void recordTexture(final Map<String,String> data,final List<String> sprites) {
        final var hashes=new TreeMap<String,String>();final var packs=new TreeMap<String,String>();
        for(final String sprite:sprites) {
            if(!activeHashes.containsKey(sprite))try {
                final var id=new net.minecraft.resources.ResourceLocation(sprite);
                final var resource=Minecraft.getInstance().getResourceManager().getResource(new net.minecraft.resources.ResourceLocation(id.getNamespace(),"textures/"+id.getPath()+".png"));
                if(resource.isPresent())try(final var stream=resource.get().open()) {
                    final byte[] bytes=stream.readNBytes(2*1024*1024+1);
                    if(bytes.length<=2*1024*1024)activeHashes.put(sprite,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
                    activePacks.put(sprite,resource.get().sourcePackId());
                }
            } catch(Exception unavailable) { /* Generated/atlas-only resources remain metadata-only. */ }
            if(activeHashes.containsKey(sprite))hashes.put(sprite,activeHashes.get(sprite));
            if(activePacks.containsKey(sprite))packs.put(sprite,activePacks.get(sprite));
        }
        if(!hashes.isEmpty())data.put("textureHashes",ProtocolCodec.GSON.toJson(hashes));
        if(!packs.isEmpty())data.put("texturePacks",ProtocolCodec.GSON.toJson(packs));
    }
    private void tick(final TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||future==null||future.isDone())return;
        try {
            final long deadline=System.nanoTime()+4_000_000;int count=0;
            while(jobs.hasNext()&&count++<64&&System.nanoTime()<deadline) {
                try { jobs.next().run(); } catch(RuntimeException|LinkageError unsupported) { /* Missing metadata stays explicitly unknown. */ }
            }
            if(!jobs.hasNext()) { future.complete(Map.copyOf(rows));rows.clear();jobs=null; }
        } catch(RuntimeException failure) { future.completeExceptionally(failure);rows.clear();jobs=null; }
    }
}
