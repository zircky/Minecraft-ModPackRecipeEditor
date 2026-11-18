package dev.recipeeditor.app;

import javafx.application.Application;

/** Classpath launcher keeps JavaFX startup independent of JPMS packaging. */
public final class Launcher {
    private Launcher() { }

    public static void main(final String[] args) {
        final var directories = dev.recipeeditor.core.ApplicationDirectories.current();
        try {
            directories.create();
            System.setProperty("mre.logDirectory", directories.logs().toString());
            System.setProperty("java.io.tmpdir", directories.temporary().toString());
            System.setProperty("javafx.cachedir", directories.cache().resolve("javafx").toString());
            if (java.util.Arrays.asList(args).contains("--migrate-legacy-snapshots")) {
                new dev.recipeeditor.app.release.LegacySnapshotMigration().migrate(directories);
            }
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Unable to prepare application data directories or migrate snapshots", error);
        }
        Application.launch(RecipeEditorApplication.class, args);
    }
}
