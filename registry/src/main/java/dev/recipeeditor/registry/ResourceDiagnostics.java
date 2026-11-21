package dev.recipeeditor.registry;
import dev.recipeeditor.core.ResourceLocation;
import java.util.*;
import java.util.stream.Stream;

/** Evidence diagnostics only; no scanning, image I/O or per-resource ERROR logging. */
public final class ResourceDiagnostics {
    public record Diagnostic(ResourceLocation id,String code,List<ResourceSource> sources,String description,String severity) { public Diagnostic {sources=List.copyOf(sources);} }
    public Stream<Diagnostic> inspect(final RegistryIndex index,final String language) {
        return index.entries().stream().flatMap(entry->{
            final var rows=new ArrayList<Diagnostic>();
            final String code=switch(entry.visual().status()) {
                case TEXTURE_NOT_FOUND->"RESOURCE_TEXTURE_NOT_FOUND";
                case RUNTIME_TEXTURE_REQUIRED->"RESOURCE_RUNTIME_REQUIRED";
                case UNKNOWN_RESOURCE->"RESOURCE_UNKNOWN";
                case UNSUPPORTED_RENDERER,RENDERING_UNSUPPORTED->"RESOURCE_RENDERER_UNSUPPORTED";
                case INVALID_TEXTURE->"RESOURCE_INVALID_TEXTURE";
                case AMBIGUOUS_SOURCE->"RESOURCE_SOURCE_AMBIGUOUS";
                default->"";
            };
            if(!code.isEmpty())rows.add(new Diagnostic(entry.id(),code,entry.sources(),entry.visual().explanation(),"WARNING"));
            if(!entry.localizedNames().containsKey(language)&&!entry.localizedNames().containsKey("en_us"))rows.add(new Diagnostic(entry.id(),"RESOURCE_TRANSLATION_MISSING",entry.sources(),"No selected-language or English translation for "+entry.localizationKey(),"WARNING"));
            return rows.stream();
        });
    }
    public Map<String,Long> summarize(final RegistryIndex index,final String language) {return Map.copyOf(inspect(index,language).collect(java.util.stream.Collectors.groupingBy(Diagnostic::code,java.util.stream.Collectors.counting())));}
}
