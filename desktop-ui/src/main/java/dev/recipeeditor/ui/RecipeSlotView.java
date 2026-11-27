package dev.recipeeditor.ui;

import dev.recipeeditor.recipeengine.DiscoveryResult;
import dev.recipeeditor.registry.*;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import java.util.*;

/** Shared read-only/editable slot, 36px by default; texture I/O stays in TextureResourceService. */
public final class RecipeSlotView extends ToggleButton {
    private final RecipeSlotState baseState;
    public RecipeSlotView(final RecipeSlotContent content,final DiscoveryResult discovery,final TextureResourceService textures) { this(content,discovery,textures,36); }
    public RecipeSlotView(final RecipeSlotContent content,final DiscoveryResult discovery,final TextureResourceService textures,final double size) {
        this(content,discovery,textures,size,Locale.getDefault().getLanguage().equals("ru")?"ru_ru":"en_us");
    }
    public RecipeSlotView(final RecipeSlotContent content,final DiscoveryResult discovery,final TextureResourceService textures,final double size,final String language) {
        setUserData(content);
        if(size<24||size>144)throw new IllegalArgumentException("Slot size must be 24..144");
        getStyleClass().add("minecraft-recipe-slot");setMinSize(size,size);setPrefSize(size,size);setMaxSize(size,size);setMnemonicParsing(false);
        final var graphic=new StackPane();graphic.setMinSize(size-8,size-8);
        RecipeSlotContent sample=content;
        if(content.kind()==RecipeSlotContent.Kind.ALTERNATIVES&&!content.alternatives().isEmpty())sample=content.alternatives().getFirst();
        final var kind=sample.kind()==RecipeSlotContent.Kind.FLUID?ResourceKind.FLUID:sample.kind()==RecipeSlotContent.Kind.BLOCK?ResourceKind.BLOCK:ResourceKind.ITEM;
        final var entries=sample.id()==null||sample.kind()==RecipeSlotContent.Kind.CHEMICAL?List.<RegistryEntry>of():discovery.registry().find(sample.id()).stream().filter(entry->entry.kind()==kind).toList();
        final Optional<RegistryEntry> entry;
        if(sample.kind()==RecipeSlotContent.Kind.TAG) {
            final var members=discovery.recipes().tagIndex().members(ResourceKind.ITEM,sample.id());
            final var tagId=sample.id();
            entry=members.stream().sorted(Comparator.comparingInt((dev.recipeeditor.core.ResourceLocation id)->{
                        if(id.equals(tagId))return 0;
                        if(id.namespace().equals("minecraft"))return id.path().equals(tagId.path())?1:2;
                        return 3;
                    }).thenComparing(id->id))
                    .flatMap(id->discovery.registry().find(id).stream()).filter(value->value.kind()==ResourceKind.ITEM).findFirst();
        } else entry=entries.stream().findFirst();
        baseState=content.state()==RecipeSlotState.FILLED&&entry.isEmpty()&&content.kind()!=RecipeSlotContent.Kind.TAG&&content.kind()!=RecipeSlotContent.Kind.ALTERNATIVES?RecipeSlotState.UNKNOWN:content.state();
        String name=content.kind().name();
        if(entry.isPresent()) {
            name=content.kind()==RecipeSlotContent.Kind.TAG?"Tag #"+content.id():new ResourceBrowserViewModel(discovery.registry()).displayName(entry.get(),language);
            graphic.getChildren().add(new ResourcePreview(entry.get(),discovery,textures,(int)size-8,()->getScene()!=null,preview->{} ,sample.nbt()));
        } else if(content.kind()!=RecipeSlotContent.Kind.EMPTY)graphic.getChildren().add(new Label(content.kind()==RecipeSlotContent.Kind.TAG?"#":content.kind()==RecipeSlotContent.Kind.CHEMICAL?"C":"?"));
        if(content.amount()>1){final var quantity=new Label(Long.toString(content.amount()));quantity.getStyleClass().add("slot-quantity");StackPane.setAlignment(quantity,Pos.BOTTOM_RIGHT);graphic.getChildren().add(quantity);}
        if(content.kind()==RecipeSlotContent.Kind.ALTERNATIVES){final var badge=new Label("◇");StackPane.setAlignment(badge,Pos.TOP_LEFT);graphic.getChildren().add(badge);}
        if(content.kind()==RecipeSlotContent.Kind.TAG&&entry.isPresent()){final var badge=new Label("#");StackPane.setAlignment(badge,Pos.TOP_LEFT);graphic.getChildren().add(badge);}
        setGraphic(graphic);
        final var tooltip=new StringBuilder(name).append(content.id()==null||name.equals(content.id().toString())?"":"\n"+content.id()).append("\nAmount: ").append(content.amount()).append("\nIngredient type: ").append(content.kind());
        entry.ifPresent(value->tooltip.append("\n").append(new ResourceBrowserViewModel(discovery.registry()).source(value)).append("\n").append(value.confidence()).append("\n").append(value.visual().status()));
        if(content.kind()==RecipeSlotContent.Kind.TAG) {
            entry.ifPresent(value->tooltip.append("\nPreview variant (indexed member): ").append(value.id()));
            final var members=discovery.recipes().tagIndex().members(ResourceKind.ITEM,content.id());
            final long available=members.stream().filter(id->discovery.registry().find(id).stream().anyMatch(value->value.kind()==ResourceKind.ITEM)).count();
            tooltip.append("\nIndexed variants: ").append(available).append(available==0?"; unresolved tag":"; registration confidence depends on index");
        }
        if(content.kind()==RecipeSlotContent.Kind.ALTERNATIVES)tooltip.append("\nAlternatives: ").append(content.alternatives());
        setTooltip(new Tooltip(tooltip.toString()));selectedProperty().addListener((o,a,b)->refreshState());disabledProperty().addListener((o,a,b)->refreshState());refreshState();
        if(content.kind()==RecipeSlotContent.Kind.TAG&&entry.isPresent()){
            final var variants=discovery.recipes().tagIndex().members(ResourceKind.ITEM,content.id()).stream().sorted()
                    .flatMap(id->discovery.registry().find(id).stream()).filter(value->value.kind()==ResourceKind.ITEM).toList();
            if(variants.size()>1){
                final var next=new MenuItem("Next tag preview");
                final int[] selected={variants.indexOf(entry.get())};
                final var names=new ResourceBrowserViewModel(discovery.registry());
                next.setOnAction(event->{
                    selected[0]=(selected[0]+1)%variants.size();
                    final var value=variants.get(selected[0]);
                    graphic.getChildren().set(0,new ResourcePreview(value,discovery,textures,(int)size-8,()->getScene()!=null,preview->{}));
                    getTooltip().setText("Tag #"+content.id()+"\n"+names.displayName(value,language)+"\n"+value.id()
                            +" · "+(selected[0]+1)+"/"+variants.size());
                });
                setContextMenu(new ContextMenu(next));
            }
        }
    }
    public RecipeSlotState slotState(){return isDisabled()?RecipeSlotState.DISABLED:isSelected()?RecipeSlotState.SELECTED:baseState;}
    private void refreshState(){for(final var state:RecipeSlotState.values())pseudoClassStateChanged(PseudoClass.getPseudoClass(state.name().toLowerCase(Locale.ROOT)),slotState()==state);}
    public void editable(final Runnable edit,final Runnable clear) {
        setOnAction(edit==null?null:event->edit.run());
        if(clear!=null){final var action=new MenuItem("Clear slot");action.setOnAction(event->clear.run());if(getContextMenu()==null)setContextMenu(new ContextMenu());getContextMenu().getItems().add(action);}
    }
}
