package dev.recipeeditor.ui;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.recipeengine.DiscoveryResult;
import dev.recipeeditor.registry.*;
import java.util.*;
import java.util.function.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Virtualized rows, adaptive columns and at most 60 records per page; no image I/O on the FX thread. */
public final class ResourceBrowserView extends BorderPane {
    private final DiscoveryResult result;
    private final TextureResourceService textures;
    private final Consumer<ResourceBrowserViewModel.RecipeQuery> showRecipes;
    private final BiPredicate<RegistryEntry,Boolean> hasRecipes;
    private final ResourceBrowserViewModel model;
    private final TextField search=new TextField();
    private final ComboBox<String> source=new ComboBox<>(),language=new ComboBox<>();
    private final ComboBox<ResourceBrowserViewModel.FilterMode> mode=new ComboBox<>();
    public enum Layout { CARDS, COMPACT_GRID }
    private final ComboBox<UnifiedResourceIndex.Type> resourceType=new ComboBox<>();
    private final ComboBox<ResourceBrowserViewModel.Evidence> evidence=new ComboBox<>();
    private final ComboBox<Layout> layout=new ComboBox<>();
    private final PaginationView pages=new PaginationView();
    private final TextArea details=new TextArea();
    private final VBox selection=new VBox(10);
    private final Label summary=new Label(),previewStatus=new Label();
    private final javafx.beans.property.ObjectProperty<ResourceIdentity> selectedId=new javafx.beans.property.SimpleObjectProperty<>();
    private ResourceKind kind=ResourceKind.ITEM;
    private List<RegistryEntry> filtered=List.of();
    private long generation,selectionGeneration;
    private boolean active,updating;
    public ResourceBrowserView(final DiscoveryResult result,final TextureResourceService textures,
            final Consumer<ResourceBrowserViewModel.RecipeQuery> showRecipes,final BiPredicate<RegistryEntry,Boolean> hasRecipes) {
        this.result=result;this.textures=textures;this.showRecipes=showRecipes;this.hasRecipes=hasRecipes;model=new ResourceBrowserViewModel(result.registry());
        getStyleClass().add("workspace-view");search.setPromptText("Search ID or localized name");HBox.setHgrow(search,Priority.ALWAYS);
        mode.getItems().setAll(ResourceBrowserViewModel.FilterMode.values());mode.getSelectionModel().selectFirst();
        language.getItems().setAll("en_us","ru_ru");language.getSelectionModel().selectFirst();
        language.setAccessibleText("Resource display language");source.setAccessibleText("Resource source filter");search.setAccessibleText("Search resources");
        final var notice=new Label("Runtime-confirmed entries and static candidates retain separate confidence. Images are flat previews; complex Minecraft geometry is not rendered.");notice.setWrapText(true);notice.getStyleClass().add("muted");
        resourceType.getItems().setAll(UnifiedResourceIndex.Type.values());resourceType.getSelectionModel().selectFirst();resourceType.setAccessibleText("Resource type");
        evidence.getItems().setAll(ResourceBrowserViewModel.Evidence.values());evidence.getSelectionModel().selectFirst();evidence.setAccessibleText("Resource evidence");
        layout.getItems().setAll(Layout.values());layout.getSelectionModel().selectFirst();layout.setAccessibleText("Resource layout");
        final var filters=new FlowPane(search,resourceType,mode,source,evidence,layout,new Label("Language:"),language);filters.getStyleClass().add("filter-row");
        final var header=new VBox(summary,notice,filters);header.getStyleClass().add("view-header");setTop(header);
        details.setEditable(false);details.setWrapText(true);VBox.setVgrow(details,Priority.ALWAYS);previewStatus.setWrapText(true);
        selection.setPadding(new Insets(12));selection.getChildren().setAll(new Label("Select a resource to inspect its image, localization and discovery evidence."));
        final var split=new SplitPane(pages,selection);split.setDividerPositions(0.70);setCenter(split);
        pages.setPageFactory(this::page);
        resourceType.valueProperty().addListener((o,a,b)->filter());evidence.valueProperty().addListener((o,a,b)->filter());layout.valueProperty().addListener((o,a,b)->filter(true));
        search.textProperty().addListener((o,a,b)->filter());source.valueProperty().addListener((o,a,b)->filter());
        language.valueProperty().addListener((o,a,b)->filter(true));mode.valueProperty().addListener((o,a,b)->{updateSources();filter();});
    }
    public void show(final ResourceKind kind) { this.kind=kind==ResourceKind.BLOCK?ResourceKind.ITEM:kind;active=true;
        updating=true;resourceType.setVisible(this.kind==ResourceKind.ITEM);resourceType.setManaged(this.kind==ResourceKind.ITEM);resourceType.getSelectionModel().select(kind==ResourceKind.BLOCK?UnifiedResourceIndex.Type.ALL_BLOCKS:UnifiedResourceIndex.Type.ALL);updating=false;updateSources();filter(); }
    public void showResource(final ResourceKind kind,final ResourceLocation id) {
        show(kind);updating=true;evidence.getSelectionModel().selectFirst();search.setText(id.toString());updating=false;updateSources();filter();
        filtered.stream().filter(entry->entry.kind()==kind&&entry.id().equals(id)).findFirst().ifPresent(this::select);
    }
    public void deactivate() { active=false;generation++;selectionGeneration++; }
    private void updateSources() {
        updating=true;source.getItems().setAll("All");source.getItems().addAll(kind==ResourceKind.ITEM?model.unifiedFilterValues(mode.getValue()):model.filterValues(kind,mode.getValue()));source.getSelectionModel().selectFirst();updating=false;
    }
    private void filter() {
        filter(false);
    }
    private void filter(final boolean retainSelection) {
        if(!active||updating)return;
        generation++;selectionGeneration++;
        final String selected=source.getSelectionModel().getSelectedIndex()<=0?"":source.getValue();
        filtered=kind==ResourceKind.ITEM?model.unifiedSearch(resourceType.getValue(),search.getText(),mode.getValue(),selected,evidence.getValue()):model.search(kind,search.getText(),mode.getValue(),selected).stream().filter(entry->model.matches(entry,evidence.getValue())).toList();
        final long confirmed=filtered.stream().filter(entry->entry.confidence()==DiscoveryConfidence.CONFIRMED_BY_RUNTIME).count();
        summary.setText(kind+" · "+filtered.size()+" entries · "+confirmed+" runtime-confirmed · "+(filtered.size()-confirmed)+" static candidates");
        pages.setPageCount((filtered.size()+59)/60);if(!retainSelection)pages.setCurrentPageIndex(0);pages.setPageFactory(this::page);
        final var retained=retainSelection?filtered.stream().filter(entry->ResourceIdentity.of(entry).equals(selectedId.get())).findFirst():Optional.<RegistryEntry>empty();
        if(retained.isPresent())select(retained.get());
        else {selection.getChildren().setAll(new Label("Select a resource to inspect details."));setBottom(null);selectedId.set(null);}
    }
    private javafx.scene.Node page(final Integer number) {
        final long request=++generation;
        if(filtered.isEmpty())return new EmptyStateView("No matching resources","Change filters. Blocks without Item requires explicit runtime evidence; older snapshots lack these links. All Blocks (technical) keeps unknown blocks accessible.","Clear search and source filter",()->{search.clear();source.getSelectionModel().selectFirst();});
        final var list=new ListView<List<RegistryEntry>>();list.setFixedCellSize(layout.getValue()==Layout.COMPACT_GRID?112:152);
        list.getStyleClass().add("resource-grid");list.setSelectionModel(null);list.setFocusTraversable(false);
        list.setCellFactory(view->new ListCell<>() {
            @Override protected void updateItem(final List<RegistryEntry> entries,final boolean empty) {
                super.updateItem(entries,empty);setText(null);setGraphic(null);
                if(empty||entries==null)return;
                final var row=new HBox(10);row.setPadding(new Insets(5));
                entries.forEach(entry->row.getChildren().add(tile(entry,request)));setGraphic(row);
            }
        });
        final Runnable resize=()->{
            final int columns=Math.max(1,(int)((list.getWidth()-26)/(layout.getValue()==Layout.COMPACT_GRID?106:158)));
            final var rows=new ArrayList<List<RegistryEntry>>();final int start=number*60,end=Math.min(start+60,filtered.size());
            for(int offset=start;offset<end;offset+=columns)rows.add(List.copyOf(filtered.subList(offset,Math.min(offset+columns,end))));
            if(!list.getItems().equals(rows))list.getItems().setAll(rows);
        };
        list.widthProperty().addListener((o,a,b)->resize.run());resize.run();return list;
    }
    private Button tile(final RegistryEntry entry,final long request) {
        final var image=new ResourcePreview(entry.visual(),result.selectedRoot(),textures,48,()->active&&generation==request,preview->{});
        final var name=line(model.displayName(entry,language.getValue()));
        final var content=new VBox(5,image,name);content.setAlignment(Pos.CENTER);
        final var button=new ResourceCardView(ResourceIdentity.of(entry),selectedId);button.setGraphic(content);
        final boolean compact=layout.getValue()==Layout.COMPACT_GRID;final int width=compact?96:148,height=compact?90:130;
        name.setPrefWidth(width-12);name.setMaxWidth(width-12);button.setMinSize(width,height);button.setPrefSize(width,height);button.setMaxSize(width,height);
        button.setTooltip(new Tooltip(model.tooltip(entry,language.getValue())));button.setAccessibleText(model.displayName(entry,language.getValue())+" · "+entry.id());
        button.setOnAction(event->{if(ResourceIdentity.of(entry).equals(selectedId.get())){selectedId.set(null);selection.getChildren().setAll(new Label("Select a resource to inspect details."));setBottom(null);selectionGeneration++;}else select(entry);});return button;
    }
    private Label line(final String text) { final var label=new Label(text);label.setMinWidth(0);label.setPrefWidth(128);label.setMaxWidth(128);label.setWrapText(false);label.setTextOverrun(OverrunStyle.ELLIPSIS);return label; }
    private void select(final RegistryEntry entry) {
        selectedId.set(ResourceIdentity.of(entry));
        final long request=++selectionGeneration;
        details.setText(model.details(entry,language.getValue()));details.positionCaret(0);previewStatus.setText(entry.visual().status()+": "+entry.visual().explanation());
        final var image=new ResourcePreview(entry.visual(),result.selectedRoot(),textures,96,()->active&&selectionGeneration==request,
                preview->previewStatus.setText(preview.status()+": "+preview.explanation()));
        selection.getChildren().setAll(image,previewStatus,details);
        if(entry.kind()==ResourceKind.ITEM||entry.kind()==ResourceKind.FLUID) {
            final var recipes=action("View Recipes",entry,false);final var uses=action("View Uses",entry,true);
            final var hint=new Label("Only parsed, indexed references are available. Unsupported runtime recipes cannot be searched by ingredient.");hint.setWrapText(true);
            final var footer=new VBox(6,new HBox(12,recipes,uses),hint);footer.setPadding(new Insets(10,0,0,0));setBottom(footer);
        } else setBottom(null);
    }
    private Button action(final String title,final RegistryEntry entry,final boolean uses) {
        final var button=new Button(title);button.setDisable(!hasRecipes.test(entry,uses));
        button.setTooltip(new Tooltip(button.isDisabled()?"No parsed references available for this resource":"Open parsed project recipes for this resource"));
        button.setOnAction(event->showRecipes.accept(new ResourceBrowserViewModel.RecipeQuery(entry.kind(),entry.id(),uses)));return button;
    }
}
