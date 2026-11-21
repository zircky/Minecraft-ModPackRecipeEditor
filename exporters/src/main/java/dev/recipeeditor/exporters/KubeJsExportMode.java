package dev.recipeeditor.exporters;

public enum KubeJsExportMode {
    AUTO, JAVASCRIPT, DATA_JSON, COMBINED;
    @Override public String toString(){return this==DATA_JSON?"DATA JSON":name();}
}
