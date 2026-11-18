package dev.recipeeditor.registry;

/** Bounded text document; texture files are referenced, never eagerly decoded. */
public record ResourceDocument(ResourceSource source, String text) { }
