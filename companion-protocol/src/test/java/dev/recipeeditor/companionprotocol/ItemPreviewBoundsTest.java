package dev.recipeeditor.companionprotocol;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ItemPreviewBoundsTest {
    @Test void rejectsOversizedImagesAndUnboundedOrUnsupportedVariants(){assertThrows(IllegalArgumentException.class,()->new ItemPreviewRequest("minecraft:stone",4096));assertThrows(IllegalArgumentException.class,()->new ItemPreviewRequest("minecraft:stone",64,"unknown",""));assertThrows(IllegalArgumentException.class,()->new ItemPreviewRequest("minecraft:stone",64,"nbt","[".repeat(33)+"]".repeat(33)));assertThrows(IllegalArgumentException.class,()->new ItemPreviewRequest("minecraft:stone",64,"default","{a:1}"));}
    @Test void arbitraryTextureBytesCannotBeLabeledAsAClientInventoryPng(){assertThrows(IllegalArgumentException.class,()->new ItemPreviewResponse(new ItemPreviewRequest("minecraft:stone",64),"MINECRAFT_CLIENT_INVENTORY","READY","ctx","AA==","invalid fixture"));assertDoesNotThrow(()->new ItemPreviewRequest("minecraft:stone",64,"nbt","{text:'[escaped]'}"));}
}
