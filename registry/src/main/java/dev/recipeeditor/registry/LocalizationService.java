package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.core.importing.*;
import java.util.*;
public final class LocalizationService {
    private final Map<String,Map<String,String>> languages=new HashMap<>();
    private final List<ImportDiagnostic> diagnostics=new ArrayList<>();
    public LocalizationService(final List<ResourceDocument> documents) {
        final var reader=new ResourceJsonReader();
        for (final var document:documents) {
            dev.recipeeditor.core.io.SafeFiles.checkCancelled();
            final String path=document.source().internalPath();
            if (!path.matches("assets/[^/]+/lang/(en_us|ru_ru)\\.json")) continue;
            final String language=path.substring(path.lastIndexOf('/')+1,path.length()-5);
            try {
                final var json=reader.read(document.text());
                if (!json.isObject()) throw new IllegalArgumentException("Language document must be an object");
                final var entries=json.fields();
                final var values=languages.computeIfAbsent(language,k->new HashMap<>());
                while (entries.hasNext()) {
                    final var entry=entries.next();
                    if (!entry.getValue().isTextual()) continue;
                    final String previous=values.putIfAbsent(entry.getKey(),entry.getValue().asText());
                    if (previous!=null && !previous.equals(entry.getValue().asText())) diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,path,"Ambiguous translation: "+entry.getKey()+"; showing first discovered value"));
                }
            } catch (Exception error) { diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,path,"Invalid language document: "+error.getMessage())); }
        }
    }
    public String key(final ResourceKind kind,final ResourceLocation id) { return kind.name().toLowerCase(Locale.ROOT)+"."+id.namespace()+"."+id.path().replace('/','.'); }
    public String displayName(final ResourceLocation id,final String key,final String language) {
        return languages.getOrDefault(language,Map.of()).getOrDefault(key,languages.getOrDefault("en_us",Map.of()).getOrDefault(key,id.toString()));
    }
    public Map<String,String> translations(final String key) {
        final var result=new HashMap<String,String>();
        languages.forEach((language,values)-> { if (values.containsKey(key)) result.put(language,values.get(key)); });
        return Map.copyOf(result);
    }
    public List<ImportDiagnostic> diagnostics() { return List.copyOf(diagnostics); }
}
