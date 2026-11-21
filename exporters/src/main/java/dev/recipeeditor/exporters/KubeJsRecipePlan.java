package dev.recipeeditor.exporters;

import java.util.List;

public record KubeJsRecipePlan(Mechanism mechanism,ExportCapability capability,List<String> targetFiles){
    public enum Mechanism { KUBEJS_JAVASCRIPT, KUBEJS_DATA }
    public KubeJsRecipePlan{targetFiles=List.copyOf(targetFiles);}
}
