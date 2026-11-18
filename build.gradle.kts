plugins {
    base
}

val desktopVersion = providers.gradleProperty("appVersion").get()
val semver = Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(-[0-9A-Za-z-]+(\\.[0-9A-Za-z-]+)*)?(\\+[0-9A-Za-z-]+(\\.[0-9A-Za-z-]+)*)?")
require(semver.matches(desktopVersion)) { "appVersion must be Semantic Versioning MAJOR.MINOR.PATCH[-prerelease][+build]" }
require(providers.gradleProperty("protocolVersion").get() == "1" && providers.gradleProperty("projectFormatVersion").get() == "1") {
    "Version metadata must match the implemented protocol/project format; implement compatibility before changing these numbers."
}
desktopVersion.substringBefore('+').substringAfter('-', "").split('.').filter { it.isNotEmpty() }.forEach {
    require(!it.all(Char::isDigit) || it == "0" || !it.startsWith('0')) { "Numeric prerelease identifiers must not have leading zeroes" }
}
allprojects {
    group = "dev.recipeeditor"
    version = desktopVersion
    repositories { mavenCentral() }
}

subprojects {
    apply(plugin = "java-library")
    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.11.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging { events("failed", "skipped") }
    }
    tasks.named<Test>("test") {
        useJUnitPlatform { excludeTags("integration", "performance", "external") }
    }
    val testSources = extensions.getByType<SourceSetContainer>().named("test")
    fun verificationSuite(name: String, tag: String, details: String) = tasks.register<Test>(name) {
        group = "verification"
        description = details
        testClassesDirs = testSources.get().output.classesDirs
        classpath = testSources.get().runtimeClasspath
        useJUnitPlatform { includeTags(tag) }
        if (tag == "integration") useJUnitPlatform { excludeTags("external", "performance") }
        shouldRunAfter(tasks.named("test"))
        inputs.property("moduleTestOptions", providers.provider { tasks.named<Test>("test").get().systemProperties })
        // Retain module-specific snapshot/session options without duplicating their lists.
        doFirst {
            val ordinary = tasks.named<Test>("test").get()
            systemProperties(ordinary.systemProperties)
            maxHeapSize = ordinary.maxHeapSize
            if (tag == "performance") systemProperty("analyzerStress", "true")
        }
    }
    val integration = verificationSuite("integrationTest", "integration", "Runs reproducible, bounded integration tests without Minecraft.")
    verificationSuite("performanceTest", "performance", "Runs opt-in stress probes; not part of build/check.")
    verificationSuite("externalTest", "external", "Runs environment-dependent probes when their required properties are supplied.")
    tasks.named("check") { dependsOn(integration) }
}

tasks.named("build") { dependsOn(subprojects.map { "${it.path}:build" }) }
tasks.named("clean") { dependsOn(subprojects.map { "${it.path}:clean" }) }
tasks.register("test") {
    group = "verification"
    description = "Runs tests in every module."
    dependsOn(subprojects.map { "${it.path}:test" })
}

listOf("integrationTest", "performanceTest", "externalTest").forEach { suite ->
    tasks.register(suite) {
        group = "verification"
        description = "Runs $suite in every module."
        dependsOn(subprojects.map { "${it.path}:$suite" })
    }
}

tasks.register<TestReport>("testReport") {
    group = "verification"
    description = "Runs unit and reproducible integration suites and aggregates their reports."
    destinationDirectory.set(layout.buildDirectory.dir("reports/tests/all"))
    subprojects.forEach { module ->
        listOf("test", "integrationTest").forEach { suite ->
            val result = module.tasks.named<Test>(suite)
            dependsOn(result)
            testResults.from(result.flatMap { it.binaryResultsDirectory })
        }
    }
}

tasks.named("build") { dependsOn("testReport") }

// Check actual declared project dependencies, rather than parsing build script text.
tasks.register("verifyArchitecture") {
    group = "verification"
    description = "Rejects production module cycles and forbidden layer dependencies."
    doLast {
        val production = setOf("api", "implementation", "compileOnly", "runtimeOnly", "annotationProcessor")
        val graph = subprojects.associate { module ->
            module.path to module.configurations.filter { it.name in production }
                .flatMap { it.dependencies.withType<ProjectDependency>().map { dependency -> dependency.path } }.toSet()
        }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun visit(path: String) {
            check(path !in visiting) { "Production module dependency cycle at $path: $visiting" }
            if (path in visited) return
            visiting.add(path)
            graph[path].orEmpty().forEach { visit(it) }
            visiting.remove(path)
            visited.add(path)
        }
        graph.keys.forEach { visit(it) }
        check(graph.getValue(":core").isEmpty()) { "core must not depend on other project modules" }
        val business = setOf(":core", ":registry", ":recipe-engine", ":modpack-parser", ":integrations", ":exporters", ":recipe-analyzer", ":companion-protocol", ":companion-server", ":companion-client")
        business.forEach { module ->
            check(graph.getValue(module).none { it == ":app" || it == ":desktop-ui" }) { "$module depends on a UI/composition module" }
            val forbidden = project(module).configurations.filter { it.name in production }
                .flatMap { it.dependencies.withType<ExternalModuleDependency>() }
                .filter { dependency ->
                    listOf("org.openjfx", "net.minecraft", "net.minecraftforge", "net.neoforged").any { group ->
                        dependency.group == group || dependency.group?.startsWith("$group.") == true
                    }
                }
            check(forbidden.isEmpty()) { "$module declares forbidden UI/game dependencies: $forbidden" }
        }
        logger.lifecycle("Production architecture verified: {} modules, no cycles.", graph.size)
    }
}
tasks.named("check") { dependsOn("verifyArchitecture") }

listOf("packageWindows", "createInstaller", "createMsiInstaller", "releaseManifest").forEach { taskName ->
    tasks.register(taskName) {
        group = "distribution"
        description = "Runs :app:$taskName for the Windows alpha distribution."
        dependsOn(":app:$taskName")
    }
}
tasks.register("releaseWindows") {
    group = "distribution"
    description = "Builds verified unsigned alpha artifacts and supported Companion; does not publish."
    dependsOn("build", "buildCompanion", ":app:releaseManifest")
}

// Independent ForgeGradle 6 / Gradle 8.8 build; never evaluated by the desktop Gradle 9 build.
tasks.register<Exec>("buildCompanion") {
    group = "build"
    description = "Builds the installable Minecraft 1.20.1 Forge Companion with Java 17."
    workingDir(layout.projectDirectory.dir("minecraft-companions/forge-1.20.1"))
    commandLine("cmd", "/d", "/c", "gradlew.bat", "clean", "build")
    doFirst {
        val toolchains = project(":companion-protocol").extensions.getByType<org.gradle.jvm.toolchain.JavaToolchainService>()
        val launcher = toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }.get()
        environment("JAVA_HOME", launcher.metadata.installationPath.asFile.absolutePath)
    }
}
