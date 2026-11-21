package dev.recipeeditor.ui;
import javafx.application.Platform;
import java.util.concurrent.*;
final class FxLayoutTestSupport {
    static { try{Platform.startup(()->{});}catch(IllegalStateException alreadyStarted){ } }
    static void run(final Runnable test)throws Exception { final var task=new FutureTask<Void>(()->{test.run();return null;});Platform.runLater(task);task.get(15,TimeUnit.SECONDS); }
    static dev.recipeeditor.recipeengine.DiscoveryResult discovery(){return new dev.recipeeditor.recipeengine.DiscoveryResult(java.nio.file.Path.of("."),new dev.recipeeditor.registry.RegistryIndex(java.util.List.of()),new dev.recipeeditor.recipeengine.RecipeIndex(java.util.List.of(),new dev.recipeeditor.registry.TagIndex(java.util.List.of())),java.util.List.of(),0);}
}
