import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipFile

val applicationName = "MinecraftRecipeEditor"
val fullVersion = project.version.toString()
val nativeVersion = fullVersion.substringBefore('-').substringBefore('+')
val windowsRoot = layout.buildDirectory.dir("windows")
val inputDirectory = windowsRoot.map { it.dir("input") }
val runtimeDirectory = windowsRoot.map { it.dir("runtime") }
val imageDirectory = windowsRoot.map { it.dir("image") }
val installerDirectory = windowsRoot.map { it.dir("installers") }
val noticesDirectory = windowsRoot.map { it.dir("notices") }
val icon = rootProject.layout.projectDirectory.file("packaging/icons/MinecraftRecipeEditor.ico")
val toolchains = extensions.getByType<JavaToolchainService>()
val java21 = toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
val mainJar = tasks.named<Jar>("jar")

fun tool(name: String) = java21.get().metadata.installationPath.file("bin/$name.exe").asFile.absolutePath
fun requireWindows() {
    check('-' in fullVersion.substringBefore('+')) { "Stable packaging is blocked: release readiness gates have not passed. Use an explicit prerelease appVersion." }
    check(System.getProperty("os.name").startsWith("Windows") && System.getProperty("os.arch") in setOf("amd64", "x86_64")) {
        "Windows x64 is required; jpackage cannot cross-build Windows installers."
    }
    check(nativeVersion.split('.').map(String::toLong).let { it[0] <= 255 && it[1] <= 255 && it[2] <= 65535 }) {
        "Windows MSI version requires major/minor <=255 and patch <=65535"
    }
}
fun removeGenerated(directory: File) {
    val root = layout.buildDirectory.get().asFile.canonicalFile.toPath()
    val target = directory.canonicalFile.toPath()
    check(target.startsWith(root) && target != root) { "Refusing to remove a path outside this module build directory: $target" }
    delete(directory)
}
fun sha256(file: File): String {
    val hash = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { stream ->
        val buffer = ByteArray(65536)
        while (true) { val size = stream.read(buffer); if (size < 0) break; hash.update(buffer, 0, size) }
    }
    return hash.digest().joinToString("") { "%02x".format(it) }
}
fun json(value: String): String = "\"" + value.flatMap { c ->
    when(c) { '\\' -> "\\\\"; '"' -> "\\\""; '\n' -> "\\n"; '\r' -> "\\r"; '\t' -> "\\t"; else -> c.toString() }.toList()
}.joinToString("") + "\""

val dependencyNotices by tasks.registering {
    group = "distribution"
    description = "Collects dependency license/notice resources and the actual artifact inventory."
    inputs.files(configurations.named("runtimeClasspath"))
    outputs.dir(noticesDirectory)
    doLast {
        val output = noticesDirectory.get().asFile
        removeGenerated(output); output.mkdirs()
        val inventory = StringBuilder("Runtime dependency inventory (retain these notices and bundled runtime/legal):\n")
        configurations.getByName("runtimeClasspath").resolvedConfiguration.resolvedArtifacts.sortedBy { it.file.name }.forEach { artifact ->
            inventory.append("${artifact.moduleVersion.id} => ${artifact.file.name}\n")
            if (artifact.file.extension == "jar") ZipFile(artifact.file).use { zip ->
                val licenses = zip.entries().asSequence().filter { !it.isDirectory &&
                    (it.name.substringAfterLast('/').matches(Regex("(?i)(license|notice|copying|additional_license_info|assembly_exception).*")) ||
                        it.name.startsWith("legal/")) }.toList()
                if (licenses.isEmpty()) inventory.append("  No embedded license. See supplemental/ and DISTRIBUTION-NOTICE.txt.\n")
                licenses.forEach { entry ->
                    val target = File(output, artifact.file.name + "/" + entry.name).toPath().normalize()
                    check(target.startsWith(output.toPath())) { "Unsafe license entry" }
                    val bytes = zip.getInputStream(entry).use { it.readNBytes(1024 * 1024 + 1) }
                    check(bytes.size <= 1024 * 1024) { "Oversized license entry" }
                    target.parent.toFile().mkdirs(); target.toFile().writeBytes(bytes)
                }
            }
        }
        File(output, "DEPENDENCIES.txt").writeText(inventory.toString(), Charsets.UTF_8)
    }
}

val stageWindows by tasks.registering(Sync::class) {
    dependsOn(mainJar, dependencyNotices)
    into(inputDirectory)
    from(mainJar)
    from(configurations.named("runtimeClasspath"))
    from(rootProject.file("packaging/DISTRIBUTION-NOTICE.txt"))
    from(noticesDirectory) { into("legal") }
    from(rootProject.file("packaging/licenses")) { into("legal/supplemental") }
}

val verifyWindowsInputs by tasks.registering {
    group = "verification"
    description = "Checks real staged application resources and JavaFX Windows native dependencies."
    dependsOn(stageWindows)
    inputs.dir(inputDirectory)
    doLast {
        val jars = inputDirectory.get().asFile.listFiles().orEmpty().filter { it.extension == "jar" }
        val required = setOf("dev/recipeeditor/app/Launcher.class", "dev/recipeeditor/app/build-info.properties",
            "logback.xml", "dev/recipeeditor/ui/theme.css", "dev/recipeeditor/app/icons/app-32.png")
        val entries = jars.flatMap { jar -> ZipFile(jar).use { zip -> zip.entries().asSequence().map { it.name }.toList() } }.toSet()
        check(entries.containsAll(required)) { "Missing packaged resources: ${required - entries}" }
        check(entries.any { it.endsWith("glass.dll") } && entries.any { it.endsWith("prism_d3d.dll") }) {
            "JavaFX Windows native DLLs are missing from Gradle runtime dependencies"
        }
    }
}

val runtimeImage by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Creates a Java 21 runtime with jlink; JavaFX/native JARs stay in the app classpath."
    inputs.file(java21.map { it.metadata.installationPath.file("release") })
    inputs.property("modules", "java.se,jdk.unsupported,jdk.unsupported.desktop,jdk.crypto.ec,jdk.charsets,jdk.localedata,jdk.zipfs")
    outputs.dir(runtimeDirectory)
    doFirst {
        requireWindows()
        val output = runtimeDirectory.get().asFile; removeGenerated(output)
        commandLine(tool("jlink"), "--add-modules", inputs.properties["modules"].toString(),
            "--strip-debug", "--no-header-files", "--no-man-pages", "--compress=2", "--output", output.absolutePath)
    }
}

val packageWindows by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds the actual Windows app-image with bundled Java 21 and JavaFX."
    dependsOn(rootProject.tasks.named("build"), verifyWindowsInputs, runtimeImage)
    inputs.dir(inputDirectory); inputs.dir(runtimeDirectory); inputs.file(icon)
    inputs.property("version", fullVersion)
    outputs.dir(imageDirectory)
    doFirst {
        requireWindows()
        val output = imageDirectory.get().asFile; removeGenerated(output); output.mkdirs()
        commandLine(tool("jpackage"), "--type", "app-image", "--name", applicationName,
            "--app-version", nativeVersion, "--description", "Minecraft Recipe Editor $fullVersion — evaluation alpha, NOT READY",
            "--vendor", "Zircky", "--input", inputDirectory.get().asFile.absolutePath,
            "--main-jar", mainJar.get().archiveFileName.get(), "--main-class", "dev.recipeeditor.app.Launcher",
            "--runtime-image", runtimeDirectory.get().asFile.absolutePath, "--icon", icon.asFile.absolutePath,
            "--java-options", "-Xmx2g", "--java-options", "-Dfile.encoding=UTF-8", "--dest", output.absolutePath)
    }
}

fun installer(taskName: String, kind: String) = tasks.register<Exec>(taskName) {
    group = "distribution"
    description = "Creates a real per-user Windows $kind installer with jpackage/WiX."
    dependsOn(packageWindows)
    inputs.dir(imageDirectory); inputs.file(icon); inputs.property("version", fullVersion)
    val output = installerDirectory.map { it.file("$applicationName-$fullVersion-windows-x64.$kind") }
    outputs.file(output)
    doFirst {
        requireWindows()
        val wix = providers.gradleProperty("wixHome").orNull ?: System.getenv("WIX")
        if (!wix.isNullOrBlank()) {
            val directory = file(wix)
            check(File(directory, "candle.exe").isFile && File(directory, "light.exe").isFile) { "wixHome must contain WiX 3 candle.exe and light.exe" }
            environment("PATH", directory.absolutePath + File.pathSeparator + System.getenv("PATH"))
        }
        val searchPath = environment["PATH"]?.toString() ?: System.getenv("PATH").orEmpty()
        check(listOf("candle.exe", "light.exe").all { name -> searchPath.split(File.pathSeparator).any { File(it, name).isFile } }) {
            "Windows installer requires WiX 3 candle.exe/light.exe. Set -PwixHome=<WiX 3 directory> or WIX/PATH. Portable packageWindowsApp needs no WiX."
        }
        val destination = installerDirectory.get().asFile; destination.mkdirs()
        val generated = File(destination, "$applicationName-$nativeVersion.$kind")
        if (generated.exists()) generated.delete()
        commandLine(tool("jpackage"), "--type", kind, "--name", applicationName, "--app-version", nativeVersion,
            "--description", "Minecraft Recipe Editor $fullVersion — evaluation alpha, NOT READY", "--vendor", "Zircky",
            "--app-image", imageDirectory.get().dir(applicationName).asFile.absolutePath,
            "--icon", icon.asFile.absolutePath, "--dest", destination.absolutePath,
            "--win-per-user-install", "--win-menu", "--win-menu-group", "Minecraft Recipe Editor Alpha",
            "--win-shortcut", "--win-shortcut-prompt", "--win-dir-chooser", "--win-upgrade-uuid", "c9a88b06-3b35-4bf7-9513-a7cd32819cc7")
    }
    doLast {
        val generated = installerDirectory.get().file("$applicationName-$nativeVersion.$kind").asFile
        check(generated.isFile && generated.length() > 0) { "jpackage did not create the requested installer" }
        java.nio.file.Files.move(generated.toPath(), output.get().asFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
}
val createInstaller = installer("createInstaller", "exe")
val createMsiInstaller = installer("createMsiInstaller", "msi")
createMsiInstaller.configure { mustRunAfter(createInstaller) }

val imageZip by tasks.registering(Zip::class) {
    dependsOn(packageWindows)
    from(imageDirectory)
    destinationDirectory.set(windowsRoot.map { it.dir("artifacts/$fullVersion") })
    archiveFileName.set("$applicationName-$fullVersion-windows-x64.zip")
}

tasks.register("releaseManifest") {
    group = "distribution"
    description = "Prepares unsigned alpha artifacts, SHA-256 and an honest machine-readable release manifest."
    dependsOn(createInstaller, createMsiInstaller, imageZip, rootProject.tasks.named("buildCompanion"))
    val artifacts = windowsRoot.map { it.dir("artifacts/$fullVersion") }
    doLast {
        val output = artifacts.get().asFile; output.mkdirs()
        installerDirectory.get().asFile.listFiles().orEmpty().filter { it.name.contains(fullVersion) && it.extension in setOf("exe", "msi") }
            .forEach { it.copyTo(File(output, it.name), overwrite = true) }
        val companionVersion = providers.gradleProperty("companionVersion").get()
        val companion = rootProject.file("minecraft-companions/forge-1.20.1/build/libs/mre_companion-forge-1.20.1-$companionVersion.jar")
        check(companion.isFile) { "Supported Companion was not built" }
        companion.copyTo(File(output, companion.name), overwrite = true)
        val files = output.listFiles().orEmpty().filter { it.extension in setOf("exe", "msi", "zip", "jar") }.sortedBy { it.name }
        val hashes = files.associateWith(::sha256)
        File(output, "SHA256SUMS.txt").writeText(hashes.entries.joinToString("\n", postfix = "\n") { "${it.value}  ${it.key.name}" }, Charsets.UTF_8)
        val rows = hashes.entries.joinToString(",\n") { (file, hash) -> "    {\"name\":${json(file.name)},\"sha256\":${json(hash)},\"bytes\":${file.length()}}" }
        File(output, "release-manifest.json").writeText("""
            {
              "application":"Minecraft Recipe Editor",
              "version":${json(fullVersion)},
              "nativeWindowsVersion":${json(nativeVersion)},
              "protocolVersion":${providers.gradleProperty("protocolVersion").get()},
              "projectFormatVersion":${providers.gradleProperty("projectFormatVersion").get()},
              "platform":"windows-x64",
              "releaseStatus":"NOT READY",
              "signed":false,
              "builtAt":${json(Instant.now().toString())},
              "companions":[{"version":${json(companionVersion)},"minecraft":"1.20.1","loader":"FORGE","loaderRange":"47.x","java":17}],
              "artifacts":[
            $rows
              ]
            }
        """.trimIndent() + "\n", Charsets.UTF_8)
    }
}
