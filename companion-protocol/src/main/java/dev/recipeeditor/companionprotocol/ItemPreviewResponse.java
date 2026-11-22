package dev.recipeeditor.companionprotocol;

import java.util.*;
import java.nio.ByteBuffer;
/** PNG is bounded before desktop decoding; successful capture carries its resource-pack context. */
public record ItemPreviewResponse(ItemPreviewRequest request,String source,String status,String context,String pngBase64,String diagnostic) {
    public static final int MAX_PNG_BYTES=96*1024;
    public ItemPreviewResponse {
        Objects.requireNonNull(request);Objects.requireNonNull(source);Objects.requireNonNull(status);Objects.requireNonNull(context);Objects.requireNonNull(pngBase64);Objects.requireNonNull(diagnostic);
        if(context.length()>256||diagnostic.length()>2048||pngBase64.length()>MAX_PNG_BYTES*4/3+4)throw new IllegalArgumentException("Preview payload limit");
        if(!Set.of("READY","ERROR").contains(status)||!source.equals("MINECRAFT_CLIENT_INVENTORY"))throw new IllegalArgumentException("Invalid preview source/status");
        if(status.equals("READY")){if(context.isBlank())throw new IllegalArgumentException("Capture context required");validatePng(Base64.getDecoder().decode(pngBase64),request.size());}
        else if(!pngBase64.isEmpty())throw new IllegalArgumentException("Error cannot carry an image");
    }
    public static void validatePng(final byte[] bytes,final int size){
        if(bytes.length<33||bytes.length>MAX_PNG_BYTES||ByteBuffer.wrap(bytes).getLong()!=0x89504e470d0a1a0aL||ByteBuffer.wrap(bytes,12,4).getInt()!=0x49484452||ByteBuffer.wrap(bytes,16,4).getInt()!=size||ByteBuffer.wrap(bytes,20,4).getInt()!=size)throw new IllegalArgumentException("Invalid PNG header/dimensions");
    }
    public byte[] png(){return pngBase64.isEmpty()?new byte[0]:Base64.getDecoder().decode(pngBase64);}
    public static ItemPreviewResponse error(final ItemPreviewRequest request,final String context,final String message){return new ItemPreviewResponse(request,"MINECRAFT_CLIENT_INVENTORY","ERROR",context,"",message);}
}
