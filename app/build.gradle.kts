plugins { application; `java-test-fixtures` }

dependencies {
    implementation(project(":companion-client"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation(project(":desktop-ui"))
    implementation(project(":core"))
    implementation(project(":modpack-parser"))
    implementation(project(":registry"))
    implementation(project(":recipe-engine"))
    implementation(project(":recipe-analyzer"))
    implementation(project(":integrations"))
    implementation(project(":exporters"))
    implementation(project(":companion-protocol"))
    implementation("org.slf4j:slf4j-api:2.0.16")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.16")
}

application {
    mainClass.set("dev.recipeeditor.app.Launcher")
    applicationDefaultJvmArgs = listOf("-Xmx2g")
}

val buildInfoDirectory = layout.buildDirectory.dir("generated/build-info")
val generateBuildInfo by tasks.registering {
    val metadata = mapOf(
        "applicationName" to "Minecraft Recipe Editor",
        "desktopVersion" to project.version.toString(),
        "protocolVersion" to providers.gradleProperty("protocolVersion").get(),
        "companionVersion" to providers.gradleProperty("companionVersion").get(),
        "projectFormatVersion" to providers.gradleProperty("projectFormatVersion").get(),
        "releaseStatus" to "NOT READY",
        "developer" to "Zircky"
    )
    inputs.properties(metadata)
    outputs.dir(buildInfoDirectory)
    doLast {
        val target = buildInfoDirectory.get().file("dev/recipeeditor/app/build-info.properties").asFile
        target.parentFile.mkdirs()
        target.writeText(metadata.entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" }, Charsets.UTF_8)
    }
}
sourceSets.main { resources.srcDir(buildInfoDirectory) }
tasks.processResources { dependsOn(generateBuildInfo) }

tasks.test {
    dependsOn(":companion-server:classes")
    systemProperty("architectureRoot", rootProject.projectDir.absolutePath)
    systemProperty("expectedDesktopVersion", project.version.toString())
    providers.gradleProperty("realModpack").orNull?.let { systemProperty("realModpack", it) }
    testLogging { events("failed", "skipped") }
}

// Manual UI probe lives exclusively on the test classpath and reads a real snapshot.
tasks.register<JavaExec>("previewGregTech") {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.recipeeditor.app.GregTechEditorPreview")
    maxHeapSize = "1536m"
    args(providers.gradleProperty("runtimeSnapshotFile").getOrElse(""))
}

apply(from = rootProject.file("gradle/windows-packaging.gradle.kts"))
