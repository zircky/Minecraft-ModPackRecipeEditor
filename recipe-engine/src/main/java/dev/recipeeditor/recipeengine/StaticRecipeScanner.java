package dev.recipeeditor.recipeengine;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.core.io.SafeFiles;
import dev.recipeeditor.core.importing.*;
import dev.recipeeditor.registry.*;
import java.util.*;

public final class StaticRecipeScanner {
    private final List<RecipeParser> parsers;
    private final ResourceLayout layout;
    public StaticRecipeScanner(final List<RecipeParser> parsers,final ResourceLayout layout) { this.parsers=List.copyOf(parsers); this.layout=layout; }
    public List<RecipeDefinition> scan(final List<ResourceDocument> documents,final List<ImportDiagnostic> diagnostics) {
        final var result=new ArrayList<RecipeDefinition>(); final var reader=new ResourceJsonReader();
        for (final var document:documents) {
            SafeFiles.checkCancelled();
            final String path=document.source().internalPath();
            if (!path.matches("data/[^/]+/"+layout.recipesDirectory()+"/.+\\.json")) continue;
            final var parts=path.split("/",4);
            try {
                final var id=new ResourceLocation(parts[1],parts[3].substring(0,parts[3].length()-5));
                final var source=RecipeSource.from(document.source());
                RecipeTypeDefinition type=new RecipeTypeDefinition("");
                try {
                    final var json=reader.read(document.text());
                    if (!json.isObject() || !json.path("type").isTextual()) throw new IllegalArgumentException("Recipe must contain a string type");
                    type=new RecipeTypeDefinition(json.path("type").asText());
                    final var location=type.location().orElseThrow(()->new IllegalArgumentException("Invalid recipe type ID"));
                    final var matches=parsers.stream().filter(p->p.supports(location)).toList();
                    if (matches.isEmpty()) result.add(unparsed(id,type,source,document.text(),RecipeParseStatus.UNSUPPORTED,"No parser for this type"));
                    else {
                        if (matches.size()>1) diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,path,"Ambiguous recipe parsers; first registered parser selected"));
                        final var parsed=matches.getFirst().parse(new RecipeParseContext(id,type,source,document.text()));
                        result.add(parsed.recipe());
                    }
                } catch (Exception error) {
                    if (error instanceof java.util.concurrent.CancellationException cancelled) throw cancelled;
                    result.add(unparsed(id,type,source,document.text(),RecipeParseStatus.INVALID,error.getMessage()));
                }
            } catch (IllegalArgumentException error) { diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,path,"Invalid recipe path: "+error.getMessage())); }
        }
        result.stream().flatMap(r->r.diagnostics().stream()).forEach(diagnostics::add);
        return List.copyOf(result);
    }
    private RecipeDefinition unparsed(final ResourceLocation id,final RecipeTypeDefinition type,final RecipeSource source,
            final String raw,final RecipeParseStatus status,final String message) {
        final var diagnostic=new ImportDiagnostic(status==RecipeParseStatus.INVALID?ScanStatus.ERROR:ScanStatus.WARNING,source.archivePath()+"!"+source.internalPath(),message);
        return new RecipeDefinition(id,type,List.of(),List.of(),new RecipeLayout(),List.of(),source,raw,status,List.of(diagnostic));
    }
}
