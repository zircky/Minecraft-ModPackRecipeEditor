package dev.recipeeditor.forge;
import java.util.List;
import java.util.Map;
/** Extension contract only. Invoke on the authorized server thread; implementations must report unavailable properties.
 * Not registered as a dataset in protocol v1; no material collection/editor is claimed in Phase 6.
 */
public interface GregTechMaterialCollector {
    record Component(String materialId,long amount) {}
    record MaterialSummary(String id,String name,Map<String,String> availableProperties,List<String> flags,
            List<String> forms,List<Component> components,List<String> diagnostics) {
        public MaterialSummary {availableProperties=Map.copyOf(availableProperties);flags=List.copyOf(flags);forms=List.copyOf(forms);components=List.copyOf(components);diagnostics=List.copyOf(diagnostics);}
    }
    List<MaterialSummary> collect();
}
