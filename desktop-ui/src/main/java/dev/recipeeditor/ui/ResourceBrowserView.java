package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.DiscoveryResult;
import dev.recipeeditor.registry.*;
import java.util.*;
import java.util.function.BiConsumer;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;

/** At most 60 resource tiles exist at a time; preview I/O is delegated to a bounded service. */
public final class ResourceBrowserView extends BorderPane {
    private final DiscoveryResult result;
    private final TextureResourceService textures;
    private final BiConsumer<ResourceLocation,Boolean> showRecipes;
    private final TextField search=new TextField();
    private final ComboBox<String> mod=new ComboBox<>(),language=new ComboBox<>();
    private final Pagination pages=new Pagination();
    private final TextArea details=new TextArea();
    private final Label summary=new Label();
    private ResourceKind kind=ResourceKind.ITEM;
    private List<RegistryEntry> filtered=List.of();
    private long generation;
    private boolean active;
    public ResourceBrowserView(final DiscoveryResult result,final TextureResourceService textures,final BiConsumer<ResourceLocation,Boolean> showRecipes) {
        this.result=result; this.textures=textures; this.showRecipes=showRecipes;
        setPadding(new Insets(18));
        search.setPromptText("Search ID or localized name"); HBox.setHgrow(search,Priority.ALWAYS);
        mod.getItems().add("All mods");
        result.registry().entries().stream().flatMap(e->e.sources().stream()).flatMap(s->s.modIds().stream()).distinct().sorted().forEach(mod.getItems()::add);
        mod.getSelectionModel().selectFirst(); language.getItems().addAll("en_us","ru_ru"); language.getSelectionModel().selectFirst();
        final var notice=new Label("STATIC CANDIDATES - registration and active load order are not confirmed. Missing resources may still exist in game.");
        notice.setWrapText(true); notice.getStyleClass().add("muted");
        final var header=new VBox(10,summary,notice,new HBox(10,search,mod,new Label("Resource language:"),language));
        header.setPadding(new Insets(0,0,12,0)); setTop(header);
        details.setEditable(false); details.setWrapText(true);
        final var split=new SplitPane(pages,details); split.setDividerPositions(0.7); setCenter(split);
        pages.setPageFactory(this::page);
        search.textProperty().addListener((o,a,b)->filter()); mod.valueProperty().addListener((o,a,b)->filter()); language.valueProperty().addListener((o,a,b)->filter());
    }
    public void show(final ResourceKind kind) { this.kind=kind; active=true; filter(); }
    public void deactivate() { active=false; generation++; }
    private String displayName(final RegistryEntry entry) {
        return entry.localizedNames().getOrDefault(language.getValue(),entry.localizedNames().getOrDefault("en_us",entry.id().toString()));
    }
    private void filter() {
        if (!active) return;
        generation++;
        final String selected=mod.getSelectionModel().getSelectedIndex()==0?"":mod.getValue();
        filtered=new RegistrySearchService(result.registry()).search(kind,search.getText(),selected);
        summary.setText(kind+" - "+filtered.size()+" candidates"+(kind==ResourceKind.FLUID?" (explicit fluid tag references only)":""));
        pages.setPageCount(Math.max(1,(filtered.size()+59)/60)); pages.setCurrentPageIndex(0);
        pages.setPageFactory(this::page); details.clear(); setBottom(null);
    }
    private javafx.scene.Node page(final Integer number) {
        final long request=++generation;
        final var grid=new TilePane(10,10); grid.setPrefColumns(6); grid.setPadding(new Insets(12));
        final int start=number*60;
        for (int i=start;i<Math.min(start+60,filtered.size());i++) grid.getChildren().add(tile(filtered.get(i),request));
        final var scroll=new ScrollPane(grid); scroll.setFitToWidth(true); return scroll;
    }
    private Button tile(final RegistryEntry entry,final long request) {
        final var placeholder=new Label("?"); placeholder.setMinSize(48,48); placeholder.setAlignment(Pos.CENTER);
        final var imageBox=new StackPane(placeholder); imageBox.setMinSize(48,48);
        final var name=new Label(displayName(entry)); name.setMaxWidth(105); name.setWrapText(true);
        final var content=new VBox(6,imageBox,name); content.setAlignment(Pos.CENTER);
        final var button=new Button(); button.setGraphic(content); button.setPrefSize(125,110);
        button.setTooltip(new Tooltip(entry.id()+"\n"+entry.confidence()+"\n"+entry.discoveryMethod()));
        button.setOnAction(event->select(entry));
        if (entry.textures().size()==1) textures.loadAsync(entry.textures().getFirst(),result.selectedRoot()).thenAccept(bytes->{
            if (bytes.length==0) return;
            final var image=new Image(new java.io.ByteArrayInputStream(bytes),48,48,true,false);
            Platform.runLater(()-> { if(active && generation==request && !image.isError()) imageBox.getChildren().setAll(new ImageView(image)); });
        });
        return button;
    }
    private void select(final RegistryEntry entry) {
        final var text=new StringBuilder(entry.id().toString()).append("\n").append(displayName(entry)).append("\n\nConfidence: ")
                .append(entry.confidence()).append("\nMethod: ").append(entry.discoveryMethod()).append("\nLocalization key: ").append(entry.localizationKey())
                .append("\nTranslations: ").append(entry.localizedNames()).append("\n\nSources:\n");
        entry.sources().forEach(source->text.append(source.type()).append(" · ").append(source.modIds()).append("\n").append(source.container()).append("!").append(source.internalPath()).append("\n"));
        text.append("\nPreview: ").append(entry.textures().isEmpty()?"Placeholder (absent, complex or ambiguous model)":"Texture sample; not a rendered Minecraft item/block");
        details.setText(text.toString());
        if (entry.kind()==ResourceKind.ITEM) {
            final var recipes=new Button("Recipes"); recipes.setOnAction(e->showRecipes.accept(entry.id(),false));
            final var uses=new Button("Uses"); uses.setOnAction(e->showRecipes.accept(entry.id(),true));
            setBottom(new HBox(12,new Label("Selected: "+entry.id()),recipes,uses));
        } else setBottom(new Label("Selected: "+entry.id()+" - runtime registration unknown"));
    }
}
