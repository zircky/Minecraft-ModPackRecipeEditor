package dev.recipeeditor.core.importing;

public record ImportProgress(String stage, int processed, int total, double fraction) { }
