package dev.recipeeditor.registry;

import dev.recipeeditor.core.ResourceLocation;
import dev.recipeeditor.core.importing.*;
import java.util.*;
public final class LocalizationService {
    private final Map<String,Map<String,String>> languages=new HashMap<>();
    private final List<ImportDiagnostic> diagnostics=new ArrayList<>();
    private final Map<String,Set<String>> conflicts=new HashMap<>();
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
                    if(conflicts.getOrDefault(language,Set.of()).contains(entry.getKey())) continue;
                    final String previous=values.putIfAbsent(entry.getKey(),entry.getValue().asText());
                    if (previous!=null && !previous.equals(entry.getValue().asText())) {
                        values.remove(entry.getKey());conflicts.computeIfAbsent(language,k->new HashSet<>()).add(entry.getKey());
                        diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,path,"Ambiguous translation: "+entry.getKey()+"; pack priority unknown, no arbitrary winner"));
                    }
                }
            } catch (Exception error) { diagnostics.add(new ImportDiagnostic(ScanStatus.WARNING,path,"Invalid language document: "+error.getMessage())); }
        }
    }
    public String key(final ResourceKind kind,final ResourceLocation id) { return kind.name().toLowerCase(Locale.ROOT)+"."+id.namespace()+"."+id.path().replace('/','.'); }
    public String displayName(final ResourceLocation id,final String key,final String language) {
        final var names=translations(key);return names.getOrDefault(language,names.getOrDefault("en_us",id.toString()));
    }
    public Map<String,String> translations(final String key) {
        final var result=new HashMap<String,String>();
        languages.forEach((language,values)-> { if (values.containsKey(key)&&!requiresArguments(values.get(key))) result.put(language,values.get(key)); });
        return Map.copyOf(result);
    }
    public List<ImportDiagnostic> diagnostics() { return List.copyOf(diagnostics); }
    /** A game's rendered name may still contain untranslated keys; such text is not a localization. */
    public boolean resolvedRuntimeName(final String name,final String component) {
        if(name==null||name.isBlank())return false;
        if(component==null)return false;
        try { return containsNoKeys(name,new ResourceJsonReader().read(component)); }
        catch(Exception error) { return false; }
    }
    private boolean containsNoKeys(final String name,final com.fasterxml.jackson.databind.JsonNode node) {
        if(node.isObject()&&node.path("translate").isTextual()&&name.contains(node.path("translate").asText()))return false;
        if(node.isContainerNode())for(final var child:node)if(!containsNoKeys(name,child))return false;
        return true;
    }
    /** Actual Minecraft component arguments are required for generated GT material form names. */
    public Map<String,String> componentTranslations(final String component) {
        if(component==null || component.length()>32768) return Map.of();
        final var result=new HashMap<String,String>();
        for(final String language:List.of("en_us","ru_ru")) try {
            final String value=component(new ResourceJsonReader().read(component),language,0);
            if(value!=null&&!value.isBlank()) result.put(language,value);
        } catch(Exception ignored) { /* An unknown key or unsupported component retains the resource ID. */ }
        return Map.copyOf(result);
    }
    private String component(final com.fasterxml.jackson.databind.JsonNode node,final String language,final int depth) {
        if(depth>16) throw new IllegalArgumentException("Component nesting limit");
        if(node.isTextual()) return node.asText();
        if(node.isArray()) { final var text=new StringBuilder();for(final var child:node)text.append(Objects.requireNonNull(component(child,language,depth+1)));return text.toString(); }
        String result;
        if(node.has("text")) result=node.path("text").asText();
        else if(node.has("translate")) {
            final String key=node.path("translate").asText();
            final String pattern=languages.getOrDefault(language,Map.of()).getOrDefault(key,languages.getOrDefault("en_us",Map.of()).get(key));
            if(pattern==null)return null;
            final var args=new ArrayList<String>();for(final var arg:node.path("with"))args.add(Objects.requireNonNull(component(arg,language,depth+1)));
            if(args.isEmpty()&&requiresArguments(pattern))return null;
            result=args.isEmpty()?pattern:String.format(Locale.ROOT,pattern,args.toArray());
        } else return null;
        for(final var extra:node.path("extra"))result+=Objects.requireNonNull(component(extra,language,depth+1));
        return result;
    }
    private boolean requiresArguments(final String value) { return value.replace("%%","").matches("(?s).*%(?:[0-9]+\\$)?[a-zA-Z].*"); }
}
