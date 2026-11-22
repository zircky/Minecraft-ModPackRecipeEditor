package dev.recipeeditor.companionprotocol;

import java.util.Objects;
/** Optional item-previews-v1 extension; never a serialized Minecraft object. */
public record ItemPreviewRequest(String resourceId,int size,String variant,String nbt) {
    public ItemPreviewRequest {
        Objects.requireNonNull(resourceId);Objects.requireNonNull(variant);Objects.requireNonNull(nbt);
        if(!resourceId.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")||resourceId.length()>256)throw new IllegalArgumentException("Invalid item ID");
        if(size<16||size>128||Integer.bitCount(size)!=1)throw new IllegalArgumentException("Preview size must be 16/32/64/128");
        if(!variant.equals("default")&&!variant.equals("nbt"))throw new IllegalArgumentException("Unsupported item variant");
        if(nbt.length()>8192||variant.equals("default")&&!nbt.isEmpty())throw new IllegalArgumentException("Invalid bounded variant NBT");
        int depth=0;char quote=0;boolean escaped=false;
        for(int i=0;i<nbt.length();i++){
            final char c=nbt.charAt(i);if(quote!=0){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c==quote)quote=0;continue;}
            if(c=='"'||c=='\'')quote=c;else if(c=='{'||c=='['){if(++depth>32)throw new IllegalArgumentException("NBT nesting limit");}else if(c=='}'||c==']'){if(--depth<0)throw new IllegalArgumentException("Malformed NBT nesting");}
        }
        if(depth!=0||quote!=0)throw new IllegalArgumentException("Unterminated NBT");
    }
    public ItemPreviewRequest(final String resourceId,final int size){this(resourceId,size,"default","");}
}
