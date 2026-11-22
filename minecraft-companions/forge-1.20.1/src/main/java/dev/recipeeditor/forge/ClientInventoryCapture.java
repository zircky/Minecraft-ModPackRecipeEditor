package dev.recipeeditor.forge;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.recipeeditor.companionprotocol.*;
import dev.recipeeditor.companionserver.ItemPreviewProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** GUI ItemRenderer capture on the render thread; PNG compression runs on one bounded worker. */
@Mod.EventBusSubscriber(modid="mre_companion",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ClientInventoryCapture implements ItemPreviewProvider,AutoCloseable {
    private static final AtomicLong reload=new AtomicLong();
    private record Job(ItemPreviewRequest request,BooleanSupplier cancelled,CompletableFuture<ItemPreviewResponse> result){}
    private final ArrayBlockingQueue<Job> queue=new ArrayBlockingQueue<>(16);
    private final ThreadPoolExecutor encoder=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),job->{final var thread=new Thread(job,"mre-icon-png");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
    private final String session=UUID.randomUUID().toString();
    private volatile boolean closed;
    @SubscribeEvent public static void setup(final FMLClientSetupEvent event){event.enqueueWork(()->{
        final var capture=new ClientInventoryCapture();RuntimeItemPreviews.install(capture);MinecraftForge.EVENT_BUS.addListener(capture::tick);MinecraftForge.EVENT_BUS.addListener(capture::worldChanged);
        Runtime.getRuntime().addShutdownHook(new Thread(capture::close,"mre-icon-shutdown"));
    });}
    @SubscribeEvent public static void reload(final RegisterClientReloadListenersEvent event){event.registerReloadListener((ResourceManagerReloadListener)manager->reload.incrementAndGet());}
    @Override public boolean available(){return !closed&&Minecraft.getInstance().getItemRenderer()!=null;}
    @Override public String context(){return session+":"+reload.get();}
    @Override public CompletableFuture<List<ItemPreviewResponse>> capture(final List<ItemPreviewRequest> items,final BooleanSupplier cancelled){
        if(items.isEmpty()||items.size()>4)return CompletableFuture.failedFuture(new IllegalArgumentException("Batch limit: 1..4"));
        final var rows=new ArrayList<CompletableFuture<ItemPreviewResponse>>();
        for(final var item:items){final var future=new CompletableFuture<ItemPreviewResponse>();rows.add(future);if(!queue.offer(new Job(item,cancelled,future)))future.complete(ItemPreviewResponse.error(item,context(),"CLIENT_PREVIEW_QUEUE_FULL"));}
        final var result=CompletableFuture.allOf(rows.toArray(CompletableFuture[]::new)).thenApply(ignored->rows.stream().map(CompletableFuture::join).toList());
        result.whenComplete((value,error)->{if(result.isCancelled())rows.forEach(row->row.cancel(false));});return result;
    }
    private void worldChanged(final net.minecraftforge.client.event.ClientPlayerNetworkEvent event){reload.incrementAndGet();}
    private void tick(final TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||closed||Minecraft.getInstance().getOverlay()!=null)return;
        Job next;do{next=queue.poll();}while(next!=null&&(next.result().isDone()||next.cancelled().getAsBoolean()));final var job=next;if(job==null)return;
        final String capturedContext=context();NativeImage image=null;
        try{
            image=render(job.request());final var pixels=image;image=null;
            try{encoder.execute(()->{try(pixels){if(job.result().isDone()||job.cancelled().getAsBoolean())return;if(!context().equals(capturedContext)){job.result().complete(ItemPreviewResponse.error(job.request(),context(),"RESOURCE_PACK_CHANGED"));return;}
                if(!visible(pixels)){job.result().complete(ItemPreviewResponse.error(job.request(),capturedContext,"EMPTY_ITEM_RENDER"));return;}
                unpremultiply(pixels);final byte[] png=pixels.asByteArray();job.result().complete(new ItemPreviewResponse(job.request(),"MINECRAFT_CLIENT_INVENTORY","READY",capturedContext,Base64.getEncoder().encodeToString(png),job.request().variant().equals("default")?"Registered Item default stack; other NBT variants may differ":"Explicit bounded ItemStack NBT"));
            }catch(Exception failure){job.result().complete(ItemPreviewResponse.error(job.request(),capturedContext,"PNG_CAPTURE_FAILED"));}});}catch(RejectedExecutionException busy){pixels.close();job.result().complete(ItemPreviewResponse.error(job.request(),capturedContext,"PNG_ENCODER_BUSY"));}
        }catch(Exception|LinkageError failure){job.result().complete(ItemPreviewResponse.error(job.request(),capturedContext,"ITEM_RENDER_FAILED: "+failure.getClass().getSimpleName()));}
        finally{if(image!=null)image.close();}
    }
    private NativeImage render(final ItemPreviewRequest request)throws Exception{
        RenderSystem.assertOnRenderThread();final var mc=Minecraft.getInstance();final var id=new ResourceLocation(request.resourceId());
        if(!ForgeRegistries.ITEMS.containsKey(id))throw new IllegalArgumentException("Item not registered");
        final var stack=Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(id)).getDefaultInstance().copy();if(stack.isEmpty())throw new IllegalArgumentException("Empty default stack");
        if(request.variant().equals("nbt"))stack.setTag(TagParser.parseTag(request.nbt()));stack.setCount(1);
        if(mc.getItemRenderer().getModel(stack,mc.level,mc.player,0)==mc.getModelManager().getMissingModel())throw new IllegalArgumentException("Missing baked item model");
        final var colorMask=org.lwjgl.BufferUtils.createByteBuffer(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,colorMask);
        final int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        final int activeTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        final int framebuffer=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),readFramebuffer=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        final boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        final int blendSrc=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),blendDst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),blendSrcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),blendDstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);final boolean depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int[] clip=new int[4];GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,clip);
        final var shader=RenderSystem.getShader();final int depthFunc=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);final float[] clearColor=new float[4];GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clearColor);
        final var projection=new Matrix4f(RenderSystem.getProjectionMatrix());final var sorting=RenderSystem.getVertexSorting();final float[] color=RenderSystem.getShaderColor().clone();
        final var modelView=RenderSystem.getModelViewStack();modelView.pushPose();TextureTarget target=null;NativeImage pixels=null;
        try{
            RenderSystem.disableScissor();RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(true);
            target=new TextureTarget(request.size(),request.size(),true,Minecraft.ON_OSX);target.setClearColor(0,0,0,0);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            RenderSystem.disableScissor();RenderSystem.setShaderColor(1,1,1,1);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.enableDepthTest();
            modelView.setIdentity();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,16,16,0,-1000,1000),VertexSorting.ORTHOGRAPHIC_Z);Lighting.setupFor3DItems();
            final var graphics=new GuiGraphics(mc,net.minecraft.client.renderer.MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(256)));graphics.renderItem(stack,0,0);graphics.flush();
            RenderSystem.bindTexture(target.getColorTextureId());pixels=new NativeImage(request.size(),request.size(),false);pixels.downloadTexture(0,false);pixels.flipY();return pixels;
        }catch(Exception|LinkageError failure){if(pixels!=null)pixels.close();throw failure;}
        finally{
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);if(target!=null)target.destroyBuffers();GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,framebuffer);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readFramebuffer);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            modelView.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);RenderSystem.bindTexture(texture);RenderSystem.activeTexture(activeTexture);RenderSystem.setShader(()->shader);RenderSystem.depthFunc(depthFunc);RenderSystem.clearColor(clearColor[0],clearColor[1],clearColor[2],clearColor[3]);Lighting.setupFor3DItems();
            RenderSystem.blendFuncSeparate(blendSrc,blendDst,blendSrcAlpha,blendDstAlpha);RenderSystem.depthMask(depthMask);RenderSystem.colorMask(colorMask.get(0)!=0,colorMask.get(1)!=0,colorMask.get(2)!=0,colorMask.get(3)!=0);
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();if(scissor)RenderSystem.enableScissor(clip[0],clip[1],clip[2],clip[3]);else RenderSystem.disableScissor();
        }
    }
    private boolean visible(final NativeImage pixels){for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++)if((pixels.getPixelRGBA(x,y)>>>24)!=0)return true;return false;}
    private void unpremultiply(final NativeImage pixels){for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++){final int value=pixels.getPixelRGBA(x,y),alpha=value>>>24;if(alpha>0&&alpha<255){int straight=alpha<<24;for(int shift=0;shift<=16;shift+=8)straight|=Math.min(255,((value>>>shift)&255)*255/alpha)<<shift;pixels.setPixelRGBA(x,y,straight);}}}
    @Override public void close(){closed=true;Job job;while((job=queue.poll())!=null)job.result().cancel(false);encoder.shutdownNow();}
}
