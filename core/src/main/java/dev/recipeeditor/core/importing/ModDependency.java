package dev.recipeeditor.core.importing;

public record ModDependency(String targetId, DependencyKind kind, String versionConstraint, String rawDeclaration) { }
