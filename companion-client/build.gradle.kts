plugins { `java-library` }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }
dependencies { api(project(":companion-protocol"));api(project(":recipe-engine")); }

dependencies { testImplementation(project(":companion-server")) }
tasks.test {
    if (providers.gradleProperty("runtimeSessionFile").isPresent || providers.gradleProperty("runtimeSnapshotFile").isPresent) maxHeapSize = "1536m"
    listOf("runtimeSessionFile", "runtimeProbeMarker", "runtimeReadyFlag", "runtimeSnapshotFile", "runtimeProbeRunId", "runtimeReloadRequest", "runtimeReloadComplete", "runtimeExpectServerStop", "phase7Mode", "phase7Directory").forEach { key -> providers.gradleProperty(key).orNull?.let { systemProperty(key, it) } }
    testLogging { events("failed", "skipped") }
}

dependencies { testImplementation(project(":integrations")); testImplementation(project(":exporters")) }
