package dev.recipeeditor.exporters;

import dev.recipeeditor.core.io.SafeFiles;
import dev.recipeeditor.recipeengine.*;
import dev.recipeeditor.recipeengine.editor.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

public final class ExportValidator {
    private final RecipeValidator recipes;
    public ExportValidator(final RecipeValidator recipes){this.recipes=Objects.requireNonNull(recipes);}
    public List<ExportDiagnostic> check(final RecipeChange change,final ExportContext context,final boolean copy) {
        final var diagnostics=new ArrayList<ExportDiagnostic>();
        try {
            ExportPaths.relative("data/"+change.id().namespace()+"/recipes/"+change.id().path()+".json");
            if(context.blockedIds().contains(change.id()))throw new IllegalArgumentException("Saved source change is quarantined; resolve it before export");
            if(!copy && change.type()==RecipeChangeType.CREATE&&!context.originals().findById(change.id()).isEmpty())throw new IllegalArgumentException("CREATE ID now collides with an original recipe");
            if(change.type()!=RecipeChangeType.CREATE) {
                final var current=context.originals().findById(change.id()).stream().map(OriginalRecipeReference::from).toList();
                if(current.isEmpty()||!new HashSet<>(current).equals(new HashSet<>(change.originals())))throw new IllegalArgumentException("Source references changed since the project was saved");
                for(final var reference:change.originals())verifySource(reference,context);
            }
            for(final var definition:change.replacement()) {
                if(!definition.id().equals(change.id()))throw new IllegalArgumentException("Recipe ID mismatch");
                final var parsed=recipes.validate(change.id(),definition.rawJson(),definition.source());
                if(!parsed.valid())throw new IllegalArgumentException("Recipe is invalid: "+parsed.issues());
            }
        }catch(Exception error){if(error instanceof java.util.concurrent.CancellationException cancelled)throw cancelled;diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Level.ERROR,ExportDiagnostic.Code.EXPORT_SOURCE_CONFLICT,error.getMessage(),List.of(change.id()),context.profile().preferredFormat(),"Validate recipe and re-open project / resolve source conflicts"));}
        return List.copyOf(diagnostics);
    }
    public void verifySource(final OriginalRecipeReference reference,final ExportContext context) throws java.io.IOException {
        final var source=reference.source();final var root=context.modpack().selectedDirectory().toRealPath();final var container=SafeFiles.inside(root,source.archivePath());
        if(source.type()==dev.recipeeditor.registry.SourceType.RUNTIME) {
            if(!new RuntimeRecipeBase().matches(source)
                    || !container.toRealPath().equals(context.modpack().gameDirectory().toRealPath()))
                throw new java.io.IOException("Runtime recipe evidence does not match the imported instance");
            // The caller checked the source identity and content hash against the currently bound snapshot.
            return;
        }
        final byte[] bytes;
        if(Files.isDirectory(container))bytes=SafeFiles.read(root,container.resolve(source.internalPath()),2*1024*1024);
        else {
            if(Files.size(container)>256L*1024*1024)throw new java.io.IOException("Source archive exceeds limit");
            try(final var zip=new ZipFile(container.toFile())){if(zip.size()>100000)throw new java.io.IOException("Source archive entry limit");final var entry=zip.getEntry(source.internalPath());if(entry==null||entry.getSize()>2*1024*1024)throw new java.io.IOException("Source JSON missing/oversized");try(final var input=zip.getInputStream(entry)){bytes=SafeFiles.read(input,2*1024*1024);}}
        }
        // Discovery decodes UTF-8 and preserves BOM in raw JSON; hash the same representation.
        if(!ExportText.hash(new String(bytes,StandardCharsets.UTF_8)).equals(reference.contentHash()))throw new java.io.IOException("Original recipe content changed on disk: "+source.internalPath());
    }
}
