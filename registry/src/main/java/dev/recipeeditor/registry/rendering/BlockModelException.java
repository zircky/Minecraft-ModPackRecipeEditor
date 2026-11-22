package dev.recipeeditor.registry.rendering;

public final class BlockModelException extends IllegalArgumentException {
    public enum Code { MISSING_MODEL, MISSING_PARENT, AMBIGUOUS_SOURCE, MISSING_TEXTURE, INVALID_UV, INVALID_GEOMETRY, PARENT_CYCLE, UNSUPPORTED_RENDERER }
    private final Code code;
    public BlockModelException(final Code code,final String message){super(code+": "+message);this.code=code;}
    public Code code(){return code;}
}
