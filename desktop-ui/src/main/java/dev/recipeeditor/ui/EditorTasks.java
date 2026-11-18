package dev.recipeeditor.ui;

import java.util.concurrent.*;
import java.util.function.*;
import javafx.application.Platform;
/** One worker per recipe workspace; all draft parsing/validation stays off the JavaFX thread. */
public final class EditorTasks implements AutoCloseable {
    private final ExecutorService worker=Executors.newSingleThreadExecutor(Thread.ofVirtual().name("recipe-editor-",0).factory());
    public <T> void submit(final Supplier<T> work,final Consumer<T> done,final Consumer<Throwable> failed) {
        CompletableFuture.supplyAsync(work,worker).whenComplete((value,error)->Platform.runLater(()-> { if(error==null) done.accept(value); else failed.accept(error.getCause()==null?error:error.getCause()); }));
    }
    @Override public void close() { worker.shutdownNow(); }
}
