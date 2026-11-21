package dev.recipeeditor.ui;
import dev.recipeeditor.recipeengine.DiscoveryResult;
import dev.recipeeditor.registry.TextureResourceService;
import javafx.scene.layout.GridPane;
import java.util.List;
import java.util.function.IntConsumer;

/** Reading this grid never rewrites a pattern or synthesizes a change. */
public final class ShapedCraftingEditorView extends GridPane {
    public ShapedCraftingEditorView(final List<String> slots,final DiscoveryResult discovery,final TextureResourceService textures,final IntConsumer edit,final IntConsumer clear) {
        this(slots,discovery,textures,64,edit,clear);
    }
    public ShapedCraftingEditorView(final List<String> slots,final DiscoveryResult discovery,final TextureResourceService textures,final double size,final IntConsumer edit,final IntConsumer clear) {
        if(size<40||size>80)throw new IllegalArgumentException("Crafting slot size must be 40..80");
        setHgap(4);setVgap(4);setMaxSize(USE_PREF_SIZE,USE_PREF_SIZE);
        if(slots.size()>9)throw new IllegalArgumentException("Crafting grid exceeds nine slots");
        for(int i=0;i<9;i++){final int index=i;final var slot=new RecipeSlotView(RecipeSlotContent.parse(i<slots.size()?slots.get(i):""),discovery,textures,size);slot.editable(edit==null?null:()->edit.accept(index),clear==null?null:()->clear.accept(index));add(slot,i%3,i/3);}
    }
}
