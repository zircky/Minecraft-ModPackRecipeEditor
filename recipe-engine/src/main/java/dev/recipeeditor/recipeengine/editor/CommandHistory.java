package dev.recipeeditor.recipeengine.editor;

import java.util.*;
public final class CommandHistory {
    private final Deque<RecipeCommand> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    public void execute(final RecipeCommand command) {
        try { command.execute(); }
        catch(RuntimeException error) { try { command.undo(); } catch(RuntimeException rollback) { error.addSuppressed(rollback); } throw error; }
        undo.addLast(command); redo.clear(); trim();
    }
    public void undo() {
        if(undo.isEmpty()) return; final var command=undo.getLast();
        try { command.undo(); } catch(RuntimeException error) { try { command.execute(); } catch(RuntimeException rollback) { error.addSuppressed(rollback); } throw error; }
        undo.removeLast(); redo.addLast(command);
    }
    public void redo() {
        if(redo.isEmpty()) return; final var command=redo.getLast();
        try { command.execute(); } catch(RuntimeException error) { try { command.undo(); } catch(RuntimeException rollback) { error.addSuppressed(rollback); } throw error; }
        redo.removeLast(); undo.addLast(command); trim();
    }
    private void trim() { long bytes=undo.stream().mapToLong(RecipeCommand::retainedBytes).sum(); while(undo.size()>200 || bytes>32L*1024*1024) bytes-=undo.removeFirst().retainedBytes(); }
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public void clear() { undo.clear(); redo.clear(); }
}
