package dev.recipeeditor.ui;

import dev.recipeeditor.companionclient.RecipeMergeService;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import java.util.*;

/** Read-only normalized state; no guessed source JSON is sent to the editor. */
public final class RuntimeRecipeBrowserView extends BorderPane {
    private final TableView<RecipeMergeService.Entry> table=new TableView<>();
    private final FilteredList<RecipeMergeService.Entry> rows;
    private boolean runtimeOnly;
    private final TextField search=new TextField();    private final ComboBox<dev.recipeeditor.recipeengine.RecipeFilterMode> sourceMode=new ComboBox<>(FXCollections.observableArrayList(dev.recipeeditor.recipeengine.RecipeFilterMode.values()));    private final ComboBox<String> sourceValue=new ComboBox<>();
    public RuntimeRecipeBrowserView(final List<RecipeMergeService.Entry> entries) { this(entries,null,recipe->false); }
    public RuntimeRecipeBrowserView(final List<RecipeMergeService.Entry> entries,
            final java.util.function.Consumer<dev.recipeeditor.companionprotocol.RuntimeData.Recipe> copy,
            final java.util.function.Predicate<dev.recipeeditor.companionprotocol.RuntimeData.Recipe> canCopy) {
        rows=new FilteredList<>(FXCollections.observableArrayList(entries));table.setItems(rows);search.setPromptText("Search runtime / static Recipe ID, type or adapter");search.textProperty().addListener((o,a,b)->filter());setTop(new VBox(8,new Label("Runtime properties are normalized loaded state, not original source JSON. Read only."),search,new HBox(10,sourceMode,sourceValue)));        sourceMode.setValue(dev.recipeeditor.recipeengine.RecipeFilterMode.SOURCE_MOD);        sourceValue.getItems().addAll("All","UNKNOWN");sourceValue.setValue("All");        sourceMode.valueProperty().addListener((o,a,b)->{sourceValue.getItems().setAll("All");if(b==dev.recipeeditor.recipeengine.RecipeFilterMode.RECIPE_NAMESPACE)entries.stream().map(e->e.id().namespace()).distinct().sorted().forEach(sourceValue.getItems()::add);else {sourceValue.getItems().add("UNKNOWN");entries.stream().flatMap(e->e.provenance().sourceModIds().stream()).distinct().sorted().forEach(sourceValue.getItems()::add);}sourceValue.setValue("All");filter();});        entries.stream().flatMap(e->e.provenance().sourceModIds().stream()).distinct().sorted().forEach(sourceValue.getItems()::add);        sourceValue.valueProperty().addListener((o,a,b)->filter());
        if(copy!=null) {
            final var button=new Button("Copy to New Project Recipe…");button.setDisable(true);
            table.getSelectionModel().selectedItemProperty().addListener((o,a,b)->button.setDisable(b==null||b.runtime()==null||!canCopy.test(b.runtime())));
            button.setOnAction(e->{final var selected=table.getSelectionModel().getSelectedItem();if(selected!=null&&selected.runtime()!=null)copy.accept(selected.runtime());});
            ((VBox)getTop()).getChildren().add(button);
        }
        table.getColumns().add(column("ID",e->e.id().toString(),300));table.getColumns().add(column("Type",e->e.runtime()!=null?e.runtime().type():e.staticDefinitions().getFirst().type().displayId(),220));table.getColumns().add(column("Source",e->e.runtime()!=null?(e.staticDefinitions().isEmpty()?"Runtime":"Runtime preferred + static retained"):"Static only",220));table.getColumns().add(column("Runtime status / adapter",e->e.runtime()==null?"Absent in snapshot":e.runtime().status()+" / "+e.runtime().adapter(),280));
        final var details=new TextArea();details.setEditable(false);table.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(b==null){details.clear();return;}final var text=new StringBuilder(b.provenance().describe()+"\nMerge diagnostics: "+b.diagnostics()+"\n");if(b.runtime()!=null){if(b.runtime().adapter().startsWith("gtceu")){final var p=b.runtime().properties();text.append("GREGTECH RECIPE\nRecipe Type: ").append(b.runtime().type()).append("\n");for(final var key:List.of("recipeCategory","inputs","outputs","tickInputs","tickOutputs","inputEUt","outputEUt","duration","recipeConditions","data","runtimeVariants"))if(p.has(key))text.append(key).append(": ").append(p.get(key)).append("\n");}text.append("Source: ").append(b.runtime().sourceInformation()).append("\nAdapter: ").append(b.runtime().adapter()).append("\nSerializer: ").append(b.runtime().serializer()).append("\nRuntime class: ").append(b.runtime().runtimeClass()).append("\nStatus: ").append(b.runtime().status()).append("\nDiagnostics: ").append(b.runtime().diagnostics()).append("\nAvailable properties (GT inputs/outputs/tick EU/duration/conditions/chances/data when received):\n").append(new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(b.runtime().properties()));}else text.append("No runtime recipe for this ID. Original static definitions remain in the Static editor.");details.setText(text.toString());details.positionCaret(0);});setCenter(new SplitPane(table,details));
    }
    public void runtimeOnly(final boolean value){runtimeOnly=value;filter();}
    private void filter(){final String query=search.getText().toLowerCase(Locale.ROOT);rows.setPredicate(e->(!runtimeOnly||e.runtime()!=null)&&matchesSource(e)&&(e.id().toString().contains(query)||(e.runtime()!=null&&(e.runtime().type().contains(query)||e.runtime().serializer().contains(query)||e.runtime().adapter().contains(query)))));}    private boolean matchesSource(final RecipeMergeService.Entry entry){final String selected=sourceValue.getValue();if(selected==null||selected.equals("All"))return true;if(sourceMode.getValue()==dev.recipeeditor.recipeengine.RecipeFilterMode.RECIPE_NAMESPACE)return entry.id().namespace().equals(selected);return entry.provenance().sourceModIds().contains(selected)||(selected.equals("UNKNOWN")&&entry.provenance().sourceModIds().isEmpty());}
    private TableColumn<RecipeMergeService.Entry,String> column(final String title,final java.util.function.Function<RecipeMergeService.Entry,String> text,final int width){final var c=new TableColumn<RecipeMergeService.Entry,String>(title);c.setCellValueFactory(v->new ReadOnlyStringWrapper(text.apply(v.getValue())));c.setPrefWidth(width);return c;}
}
