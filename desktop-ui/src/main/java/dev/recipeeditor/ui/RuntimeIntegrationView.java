package dev.recipeeditor.ui;

import dev.recipeeditor.companionclient.*;
import dev.recipeeditor.companionprotocol.RuntimeData;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

public final class RuntimeIntegrationView extends BorderPane implements AutoCloseable {
    private final CompanionConnectionService connection;
    private final RuntimeSnapshotService snapshots;
    private final ExecutorService storage=Executors.newSingleThreadExecutor(Thread.ofVirtual().name("runtime-storage",0).factory());
    private final BiConsumer<RuntimeData.Snapshot,Path> apply;
    private final TextField session=new TextField();
    private final Label status=new Label("DISCONNECTED"),environment=new Label("No runtime environment received"),counts=new Label("No complete snapshot"),lastSync=new Label("Last sync: not available");
    private final TextArea diagnostics=new TextArea();private final ProgressBar progress=new ProgressBar(0);
    private final Button connect=new Button("Connect"),disconnect=new Button("Disconnect"),sync=new Button("Synchronize with Minecraft"),cancel=new Button("Cancel Sync"),view=new Button("View Snapshot"),load=new Button("Open Snapshot");
    private final TableView<RuntimeData.Registry> registries=new TableView<>();
    private RuntimeData.Snapshot snapshot;private Path snapshotFile;private boolean busy,closed;
    public RuntimeIntegrationView(final CompanionConnectionService connection,final RuntimeSnapshotService snapshots,final BiConsumer<RuntimeData.Snapshot,Path> apply) {
        this.connection=connection;this.snapshots=snapshots;this.apply=apply;getStyleClass().add("workspace-view");session.setPromptText("Minecraft config/mre_companion/session.json — token is read privately, never displayed");HBox.setHgrow(session,Priority.ALWAYS);
        final var browse=new Button("Session File…");browse.setOnAction(e->{final var chooser=new FileChooser();chooser.setTitle("Select Companion session.json in the chosen Minecraft instance");final var file=chooser.showOpenDialog(getScene().getWindow());if(file!=null)session.setText(file.toString());});
        connect.setOnAction(e->{try{busy(true);connection.connect(Path.of(session.getText())).whenComplete((done,error)->Platform.runLater(()->{busy(false);if(error!=null)error(error);}));}catch(RuntimeException failure){busy(false);error(failure);}});
        disconnect.setOnAction(e->connection.disconnect());cancel.setOnAction(e->connection.cancelSynchronization());sync.setOnAction(e->synchronize());view.setDisable(true);view.setOnAction(e->showSnapshot());
        load.setOnAction(e->{final var chooser=new FileChooser();chooser.setTitle("Open runtime snapshot (cached evidence, not a live connection)");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Runtime snapshot","*.mresnapshot"));final var file=chooser.showOpenDialog(getScene().getWindow());if(file!=null){busy(true);CompletableFuture.supplyAsync(()->{try{return snapshots.load(file.toPath());}catch(java.io.IOException failure){throw new CompletionException(failure);}},storage).whenComplete((loaded,error)->Platform.runLater(()->{busy(false);if(error!=null){error(error);return;}accept(loaded,file.toPath(),true);}));}});
        progress.setMaxWidth(Double.MAX_VALUE);diagnostics.setEditable(false);
        final var actions=new FlowPane(connect,disconnect,sync,cancel,load,view);actions.getStyleClass().add("filter-row");
        final var top=new VBox(new Label("Runtime connection · read only / localhost"),new HBox(10,session,browse),status,environment,actions,progress,counts,lastSync);top.getStyleClass().add("view-header");setTop(top);
        status.setWrapText(true);environment.setWrapText(true);counts.setWrapText(true);lastSync.setWrapText(true);
        registries.setPlaceholder(new EmptyStateView("No runtime snapshot","Select the Companion session file, connect and synchronize with an open Minecraft world, or open a previously saved snapshot. Static project data remains available."));
        registries.getColumns().add(column("Registry",r->r.kind().name(),140));registries.getColumns().add(column("ID",RuntimeData.Registry::id,300));registries.getColumns().add(column("Display name",RuntimeData.Registry::displayName,250));registries.getColumns().add(column("Runtime class",RuntimeData.Registry::runtimeClass,380));
        final var registryDetails=new TextArea();registryDetails.setEditable(false);registries.getSelectionModel().selectedItemProperty().addListener((o,a,b)->registryDetails.setText(b==null?"":b.metadata()+"\nBlockState property descriptors: "+b.stateProperties()+"\nConfirmed only in snapshot "+snapshot.snapshotId()+" @ "+snapshot.environment().collectedAt()));
        final var registryTab=new Tab("Runtime Registry",new SplitPane(registries,registryDetails));registryTab.setClosable(false);final var diagnosticTab=new Tab("Diagnostics",diagnostics);diagnosticTab.setClosable(false);setCenter(new TabPane(registryTab,diagnosticTab));
        connection.addListener(value->Platform.runLater(()->{if(!closed){if(snapshot==null&&value==CompanionConnectionStatus.CONNECTED)environment.setText("Companion "+connection.companionVersion()+" authenticated. Open a world and synchronize for environment details.");status.setText(value+" — "+(snapshot==null?"no snapshot":"snapshot is historical evidence from "+snapshot.environment().collectedAt()));buttons();}}));buttons();
    }
    private void synchronize() {
        busy(true);progress.setProgress(-1);diagnostics.clear();
        connection.synchronize(stage->Platform.runLater(()->status.setText(stage))).thenApplyAsync(data->{try{final var path=dev.recipeeditor.core.ApplicationDirectories.current().snapshots().resolve(data.snapshotId()+".mresnapshot");snapshots.save(path,data);return Map.entry(data,path);}catch(java.io.IOException failure){throw new CompletionException(failure);}},storage).whenComplete((result,error)->Platform.runLater(()->{busy(false);progress.setProgress(error==null?1:0);if(error!=null){error(error);return;}accept(result.getKey(),result.getValue(),false);}));
    }
    private void accept(final RuntimeData.Snapshot data,final Path file,final boolean cached) {
        snapshot=data;snapshotFile=file;status.setText(connection.status()+" — snapshot is historical evidence from "+data.environment().collectedAt());environment.setText(data.environment().minecraftVersion()+" / "+data.environment().loader()+" "+data.environment().loaderVersion()+" / Companion "+data.environment().companionVersion()+" / source: "+data.environment().sourceInstance());
        counts.setText("Items: "+count(RuntimeData.Kind.ITEM)+"   Blocks: "+count(RuntimeData.Kind.BLOCK)+"   Fluids: "+count(RuntimeData.Kind.FLUID)+"   Recipe types: "+count(RuntimeData.Kind.RECIPE_TYPE)+"   Serializers: "+count(RuntimeData.Kind.RECIPE_SERIALIZER)+"   Tags: "+data.tags().size()+"   Recipes: "+data.recipes().size());lastSync.setText((cached?"Cached snapshot collected: ":"Last sync: ")+data.environment().collectedAt());registries.getItems().setAll(data.registries());diagnostics.setText(String.join("\n",data.diagnostics())+"\nSnapshot: "+file+"\nRuntime confidence applies only to this environment/time. Original source JSON is not inferred.");view.setDisable(false);apply.accept(data,file);
    }
    private long count(final RuntimeData.Kind kind){return snapshot.registries().stream().filter(r->r.kind()==kind).count();}
    public void addDiagnostic(final String message){addDiagnostics(List.of(message));}
    public void addDiagnostics(final Collection<String> messages){
        final var text=new StringBuilder();
        int displayed=0;
        for(final String message:messages){
            if(displayed>=200||text.length()+message.length()>64_000)break;
            text.append('\n').append(message);displayed++;
        }
        if(displayed<messages.size())text.append("\nAdditional diagnostics omitted from this view: ").append(messages.size()-displayed);
        diagnostics.appendText(text.toString());
    }
    private void showSnapshot(){
        final var data=snapshot;busy(true);
        CompletableFuture.supplyAsync(()->new RecipeMergeService().merge(new dev.recipeeditor.recipeengine.RecipeIndex(List.of(),new dev.recipeeditor.registry.TagIndex(List.of())),data),storage).whenComplete((merged,failure)->Platform.runLater(()->{
            busy(false);if(closed)return;if(failure!=null){error(failure);return;}
            final var dialog=new Dialog<Void>();dialog.initOwner(getScene().getWindow());new UiTheme().apply(dialog.getDialogPane());dialog.setTitle("Runtime Snapshot — normalized read-only recipes");dialog.getDialogPane().setContent(new RuntimeRecipeBrowserView(merged.entries()));dialog.getDialogPane().setPrefSize(1100,720);dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);dialog.show();
        }));
    }
    private void busy(final boolean value){busy=value;buttons();}
    private void buttons(){view.setDisable(busy||snapshot==null);final var s=connection.status();connect.setDisable(busy||s==CompanionConnectionStatus.CONNECTING||s==CompanionConnectionStatus.SYNCHRONIZING);disconnect.setDisable(s==CompanionConnectionStatus.DISCONNECTED);sync.setDisable(busy||s!=CompanionConnectionStatus.CONNECTED);cancel.setDisable(s!=CompanionConnectionStatus.SYNCHRONIZING);load.setDisable(busy);session.setDisable(busy);}
    private void error(final Throwable failure){Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();diagnostics.appendText("\n"+cause.getClass().getSimpleName()+": "+cause.getMessage());status.setText(connection.status()+" — synchronization failed/cancelled; previous complete snapshot retained");}
    private TableColumn<RuntimeData.Registry,String> column(final String name,final java.util.function.Function<RuntimeData.Registry,String> text,final int width){final var c=new TableColumn<RuntimeData.Registry,String>(name);c.setCellValueFactory(v->new ReadOnlyStringWrapper(text.apply(v.getValue())));c.setPrefWidth(width);return c;}
    @Override public void close(){closed=true;connection.disconnect();storage.shutdown();}
}
