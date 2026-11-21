package dev.recipeeditor.ui;

import dev.recipeeditor.recipeengine.DiscoveryResult;
import dev.recipeeditor.registry.MaterialDefinition;
import dev.recipeeditor.registry.RegistryEntry;
import dev.recipeeditor.registry.ResourceVisual;
import dev.recipeeditor.registry.TextureResourceService;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A separate runtime dataset: virtualized, bounded pages also protect accessibility clients from huge trees.
 */
public final class MaterialsBrowserView extends BorderPane {
  private final DiscoveryResult result;
  private final TextureResourceService textures;
  private final Consumer<MaterialDefinition.Form> navigate;
  private final ResourceBrowserViewModel model;
  private final FilteredList<MaterialDefinition> filtered;
  private final Pagination pages = new Pagination();
  private final VBox details = new VBox(10);
  private final TextField search = new TextField();
  private final ComboBox<String> language = new ComboBox<>();
  private boolean active = true;
  private long generation;

  public MaterialsBrowserView(final DiscoveryResult result, final TextureResourceService textures, final Consumer<MaterialDefinition.Form> navigate) {
    this.result = result;
    this.textures = textures;
    this.navigate = navigate;
    model = new ResourceBrowserViewModel(result.registry());
    getStyleClass().add("workspace-view");
    filtered = new FilteredList<>(javafx.collections.FXCollections.observableArrayList(result.registry().materials().stream().sorted(Comparator.comparing(MaterialDefinition::id)).toList()));
    search.setPromptText("Search material ID or localized name");
    HBox.setHgrow(search, Priority.ALWAYS);
    language.getItems().setAll("en_us", "ru_ru");
    language.getSelectionModel().selectFirst();
    final var status = new Label(filtered.isEmpty() ? "Runtime Companion required: synchronize a materials-v1 snapshot. Check Runtime diagnostics for GT version support."
        : filtered.size() + " runtime materials. Forms link only to actual registrations; availability reflects this snapshot.");
    status.setWrapText(true);
    final var filters=new javafx.scene.layout.FlowPane(search,language);filters.getStyleClass().add("filter-row");
    final var header=new VBox(status,filters);header.getStyleClass().add("view-header");setTop(header);
    details.setPadding(new Insets(12));
    final var scroll = new ScrollPane(details);
    scroll.setFitToWidth(true);
    final var split = new SplitPane(pages, scroll);
    split.setDividerPositions(0.45);
    setCenter(split);
    search.textProperty().addListener((o, a, b) -> filter());
    language.valueProperty().addListener((o, a, b) -> filter());
    filter();
  }

  private void filter() {
    final String query = search.getText().strip().toLowerCase(Locale.ROOT);
    filtered.setPredicate(material -> material.id().toString().contains(query) || model.displayName(material, language.getValue()).toLowerCase(Locale.ROOT).contains(query));
    generation++;
    select(null);
    pages.setPageCount(Math.max(1, (filtered.size() + 59) / 60));
    pages.setCurrentPageIndex(0);
    pages.setPageFactory(this::page);
  }

  private Node page(final Integer number) {
    if(filtered.isEmpty())return new EmptyStateView(result.registry().materials().isEmpty()?"No runtime materials":"No matching materials",
        result.registry().materials().isEmpty()?"Open Runtime and synchronize a supported materials-v1 snapshot. Static resources remain available in Items, Blocks and Fluids.":"Change the search query or display language.");
    final var list = new ListView<MaterialDefinition>();
    list.setFixedCellSize(150);
    final int start = Math.min(number * 60, filtered.size());
    list.getItems().setAll(filtered.subList(start, Math.min(start + 60, filtered.size())));
    list.setCellFactory(view -> new ListCell<>() {
      @Override
      protected void updateItem(final MaterialDefinition material, final boolean empty) {
        super.updateItem(material, empty);
        setText(null);
        setGraphic(null);
        setTooltip(null);
        if (empty || material == null) return;
        final var image = new ResourcePreview(representative(material), result.selectedRoot(), textures, 48, () -> active, preview -> {
        });
        final var box = new VBox(4, image, line(model.displayName(material, language.getValue())), line(material.id().toString()), line("Namespace: " + material.id().namespace()), line("Runtime confirmed · " + material.forms().size() + " registered forms"));
        setGraphic(box);
        setTooltip(new Tooltip(model.displayName(material, language.getValue()) + "\n" + material.id() + "\nImage: representative registered form"));
      }
    });
    list.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> select(b));
    return list;
  }

  private Label line(final String text) {
    final var label = new Label(text);
    label.setMinWidth(0);
    label.setPrefWidth(300);
    label.setMaxWidth(300);
    label.setTextOverrun(OverrunStyle.ELLIPSIS);
    return label;
  }

  private ResourceVisual representative(final MaterialDefinition material) {
    return material.forms().stream().map(model::form).flatMap(Optional::stream).map(RegistryEntry::visual).filter(visual -> !visual.layers().isEmpty()).findFirst()
        .orElse(ResourceVisual.unavailable(ResourceVisual.Status.RUNTIME_TEXTURE_REQUIRED, "No preview for confirmed forms"));
  }

  private void select(final MaterialDefinition material) {
    final long request = ++generation;
    details.getChildren().clear();
    if (material == null) {
      details.getChildren().add(new Label("Select a material to inspect properties and registered forms."));
      return;
    }
    final var previewStatus = new Label("Image is a sample of a registered form.");
    previewStatus.setWrapText(true);
    details.getChildren().addAll(new ResourcePreview(representative(material), result.selectedRoot(), textures, 96, () -> active && generation == request,
        preview -> previewStatus.setText(preview.status() + ": " + preview.explanation())), previewStatus);
    final var text = new TextArea(model.displayName(material, language.getValue()) + "\nMaterial ID: " + material.id() + "\nLocalization key: " + material.localizationKey()
        + "\nRuntime-reported name (may include game fallback): " + material.name() + "\nProperties: " + material.properties() + "\nFlags: " + material.flags()
        + "\nConfidence: CONFIRMED_BY_RUNTIME\nMetadata: " + material.metadata());
    text.setEditable(false);
    text.setWrapText(true);
    text.setPrefRowCount(12);
    details.getChildren().addAll(text, new Label("Actually registered forms:"), forms(material));
    if (material.forms().isEmpty())
      details.getChildren().add(new Label("No registered forms reported for this material."));
  }

  private ListView<MaterialDefinition.Form> forms(final MaterialDefinition material) {
    final var forms = new ListView<MaterialDefinition.Form>();
    forms.getItems().setAll(material.forms());
    forms.setFixedCellSize(38);
    forms.setPrefHeight(Math.min(400, Math.max(50, material.forms().size() * 38 + 4)));
    forms.setCellFactory(view -> new ListCell<>() {
      @Override
      protected void updateItem(final MaterialDefinition.Form form, final boolean empty) {
        super.updateItem(form, empty);
        setText(null);
        setGraphic(null);
        if (empty || form == null) return;
        final var button = new Button(form.prefix() + " · " + form.kind() + " · " + form.resourceId());
        button.setMnemonicParsing(false);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setTextOverrun(OverrunStyle.ELLIPSIS);
        button.prefWidthProperty().bind(forms.widthProperty().subtract(30));
        button.setTooltip(new Tooltip(form.prefix() + "\n" + form.kind() + "\n" + form.resourceId()));
        button.setDisable(model.form(form).isEmpty());
        button.setOnAction(event -> navigate.accept(form));
        setGraphic(button);
      }
    });
    return forms;
  }

  public void activate() {
    active = true;
    filter();
  }

  public void deactivate() {
    active = false;
    generation++;
  }
}
