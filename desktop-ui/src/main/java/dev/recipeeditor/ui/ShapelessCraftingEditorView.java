package dev.recipeeditor.ui;
import dev.recipeeditor.recipeengine.DiscoveryResult;
import dev.recipeeditor.registry.TextureResourceService;
import javafx.scene.layout.GridPane;
import java.util.List;
import java.util.function.IntConsumer;

/** Positions are presentation only; callbacks retain the ingredient array's semantics. */
public final class ShapelessCraftingEditorView extends GridPane {
    public ShapelessCraftingEditorView(final List<String> ingredients,final DiscoveryResult discovery,final TextureResourceService textures,final IntConsumer edit,final IntConsumer remove,final Runnable add) {
        this(ingredients,discovery,textures,64,edit,remove,add);
    }
    public ShapelessCraftingEditorView(final List<String> ingredients,final DiscoveryResult discovery,final TextureResourceService textures,final double size,final IntConsumer edit,final IntConsumer remove,final Runnable add) {
        this(ingredients,discovery,textures,size,java.util.Locale.getDefault().getLanguage().equals("ru")?"ru_ru":"en_us",edit,remove,add);
    }
    public ShapelessCraftingEditorView(final List<String> ingredients,final DiscoveryResult discovery,final TextureResourceService textures,final double size,final String language,final IntConsumer edit,final IntConsumer remove,final Runnable add) {
        if(size<40||size>80)throw new IllegalArgumentException("Crafting slot size must be 40..80");
        if(ingredients.size()>9)throw new IllegalArgumentException("At most nine crafting ingredients");
        setHgap(4);setVgap(4);setMaxSize(USE_PREF_SIZE,USE_PREF_SIZE);
        for(int i=0;i<9;i++){final int index=i;final boolean occupied=i<ingredients.size();final var slot=new RecipeSlotView(RecipeSlotContent.parse(occupied?ingredients.get(i):""),discovery,textures,size,language);slot.editable(occupied?(edit==null?null:()->edit.accept(index)):add,occupied&&remove!=null?()->remove.accept(index):null);add(slot,i%3,i/3);}
    }
}
