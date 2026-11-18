package dev.recipeeditor.companionprotocol;

import java.io.*;
import java.util.Properties;

/** Build-generated Companion identity, independent of the desktop and protocol versions. */
public final class CompanionIdentity {
    private CompanionIdentity() { }

    public static String version() {
        final var values = new Properties();
        try (final var input = CompanionIdentity.class.getResourceAsStream("companion.properties")) {
            if (input == null) throw new IllegalStateException("Missing Companion build identity");
            values.load(input);
            final String version = values.getProperty("version");
            if (version == null || version.isBlank()) throw new IllegalStateException("Invalid Companion build identity");
            return version;
        } catch (IOException error) { throw new IllegalStateException("Cannot read Companion build identity", error); }
    }
}
