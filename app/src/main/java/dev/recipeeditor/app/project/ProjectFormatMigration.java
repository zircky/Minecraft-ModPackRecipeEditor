package dev.recipeeditor.app.project;

import com.fasterxml.jackson.databind.JsonNode;
/** Explicit payload migration only; arbitrary scripts or classes are never loaded from a project file. */
public interface ProjectFormatMigration {
    int fromVersion();
    int toVersion();
    JsonNode migrate(JsonNode oldPayload);
}
