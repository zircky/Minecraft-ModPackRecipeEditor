package dev.recipeeditor.recipeengine.editor;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class CommandHistoryTest {
    @Test void failedCommandRestoresBeforeStateAndKeepsHistory() {
        final var value=new AtomicInteger(); final var history=new CommandHistory();
        history.execute(new RecipeCommand(){public void execute(){value.set(1);}public void undo(){value.set(0);}});
        assertThrows(IllegalStateException.class,()->history.execute(new RecipeCommand(){public void execute(){value.set(999);throw new IllegalStateException("failure");}public void undo(){value.set(1);}}));
        assertEquals(1,value.get()); history.undo(); assertEquals(0,value.get()); history.redo(); assertEquals(1,value.get());
    }
    @Test void newCommandClearsRedoAndEmptyHistoryIsSafe() {
        final var history=new CommandHistory(); history.undo(); history.redo();
        history.execute(new RecipeCommand(){public void execute(){}public void undo(){}}); history.undo(); assertTrue(history.canRedo());
        history.execute(new RecipeCommand(){public void execute(){}public void undo(){}}); assertFalse(history.canRedo());
    }
    @Test void failedUndoRestoresExecutedStateAndKeepsHistoryPosition() {
        final var value=new AtomicInteger();final var history=new CommandHistory();
        history.execute(new RecipeCommand(){public void execute(){value.set(1);}public void undo(){value.set(0);throw new IllegalStateException("undo failed");}});
        assertThrows(IllegalStateException.class,history::undo);assertEquals(1,value.get());assertTrue(history.canUndo());assertFalse(history.canRedo());
    }
}
