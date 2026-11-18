package dev.recipeeditor.app.release;

import dev.recipeeditor.core.ApplicationInfo;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Generated from Gradle properties; never substitutes a fictional version if metadata is missing. */
public record BuildMetadata(String name, String desktopVersion, int protocolVersion,
        String companionVersion, int projectFormatVersion, String releaseStatus, String developer) {
    public static BuildMetadata load() {
        final var properties = new Properties();
        try (final var stream = BuildMetadata.class.getResourceAsStream("/dev/recipeeditor/app/build-info.properties")) {
            if (stream == null) throw new IllegalStateException("Build metadata is missing; rebuild the application");
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return new BuildMetadata(required(properties, "applicationName"), required(properties, "desktopVersion"),
                    Integer.parseInt(required(properties, "protocolVersion")), required(properties, "companionVersion"),
                    Integer.parseInt(required(properties, "projectFormatVersion")), required(properties, "releaseStatus"),
                    required(properties, "developer"));
        } catch (IOException | NumberFormatException error) {
            throw new IllegalStateException("Invalid generated build metadata", error);
        }
    }

    public ApplicationInfo applicationInfo() { return new ApplicationInfo(name, desktopVersion); }

    public String diagnosticSummary() {
        return name + "\nVersion: " + desktopVersion + "\nProtocol: " + protocolVersion
                + "\nCompanion: " + companionVersion + "\nProject format: " + projectFormatVersion
                + "\nJava: " + System.getProperty("java.version")
                + "\nPlatform: " + System.getProperty("os.name") + " " + System.getProperty("os.arch")
                + "\nDeveloper: " + developer + "\nRelease: " + releaseStatus;
    }

    private static String required(final Properties properties, final String name) {
        final String value = properties.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing build property: " + name);
        return value;
    }
}
