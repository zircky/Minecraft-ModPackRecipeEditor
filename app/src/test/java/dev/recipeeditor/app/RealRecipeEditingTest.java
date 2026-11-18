package dev.recipeeditor.app;

import dev.recipeeditor.app.project.FileProjectPersistence;
import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.integrations.Vanilla1201Parsers;
import dev.recipeeditor.modpackparser.DefaultModpackImportService;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
@org.junit.jupiter.api.Tag("external")
final class RealRecipeEditingTest {
    @TempDir Path temporary;
    private byte[] hash(final Path file) throws Exception { final var digest=MessageDigest.getInstance("SHA-256");try(final var input=Files.newInputStream(file)){final var buffer=new byte[65536];int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);}return digest.digest(); }
    @Test @EnabledIfSystemProperty(named="realModpack",matches=".+")
    void editsRealRecipeWithUndoPersistenceAndSourceConflictWithoutChangingJar() throws Exception {
        try(final var importer=new DefaultModpackImportService();final var analyzer=new DefaultStaticAnalysisService();final var store=new FileProjectPersistence()) {
            final var imported=importer.start(Path.of(System.getProperty("realModpack")),p->{}).result().get(2,TimeUnit.MINUTES);
            final var discovery=analyzer.start(imported.modpack(),p->{}).result().get(5,TimeUnit.MINUTES);
            final var original=discovery.recipes().recipes().stream().filter(r->r.status()==RecipeParseStatus.PARSED).findFirst().orElseThrow();
            final var originalHash=hash(original.source().archivePath());
            final var validator=new RecipeValidator(new Vanilla1201Parsers().parsers(),discovery.registry());
            final var editor=new RecipeEditorService(discovery.recipes(),validator);
            final String changed=new RecipeFormService().patch(original.rawJson(),"group","\"phase4_verification_only\"");
            editor.update(original.id(),validator.validate(original.id(),changed,original.source()).recipe()); assertEquals(RecipeModificationStatus.MODIFIED,editor.status(original.id()));
            assertFalse(new RecipeDiffService().compare(original.rawJson(),changed).isEmpty()); editor.undo(); assertEquals(RecipeModificationStatus.UNCHANGED,editor.status(original.id())); editor.redo();
            final var duplicate=editor.duplicate(editor.effective().findById(original.id()).getFirst(),ResourceLocation.parse("recipeeditor_test:temporary_duplicate"));
            editor.delete(duplicate.id()); assertFalse(editor.snapshot().changes().containsKey(duplicate.id())); editor.undo();
            final var file=temporary.resolve("project.json");store.write(file,ProjectDocument.capture(imported.modpack().selectedDirectory(),editor.persistentSnapshot()));
            final var restored=new RecipeEditorService(discovery.recipes(),validator); restored.restore(store.read(file).materialize(validator,file));
            assertEquals(changed,restored.effective().findById(original.id()).getFirst().rawJson()); assertEquals(1,restored.effective().findById(duplicate.id()).size());
            final var updatedSources=new ArrayList<>(discovery.recipes().recipes()); updatedSources.remove(original); updatedSources.add(validator.validate(original.id(),new RecipeFormService().patch(original.rawJson(),"group","\"updated_mod_source\""),original.source()).recipe());
            final var conflicted=new RecipeEditorService(new RecipeIndex(updatedSources,discovery.recipes().tagIndex()),validator); conflicted.restore(store.read(file).materialize(validator,file)); assertTrue(conflicted.hasConflict(original.id()));
            assertArrayEquals(originalHash,hash(original.source().archivePath()));
            Files.createDirectories(Path.of("build"));Files.writeString(Path.of("build/real-editor-result.txt"),"Verified real recipe "+original.id()+" from "+original.source().archivePath()+"\nUPDATE / Undo / Redo / Duplicate / CREATE+DELETE / restore / source conflict: passed\nOriginal JAR SHA-256 unchanged\nTemporary project only: "+file);
        }
    }
}
