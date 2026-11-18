plugins { `java-library` }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
dependencies { api("com.google.code.gson:gson:2.10.1") }

val identityDirectory = layout.buildDirectory.dir("generated/identity")
val generateCompanionIdentity by tasks.registering {
    val companionVersion = providers.gradleProperty("companionVersion")
    inputs.property("companionVersion", companionVersion)
    outputs.dir(identityDirectory)
    doLast {
        val target = identityDirectory.get().file("dev/recipeeditor/companionprotocol/companion.properties").asFile
        target.parentFile.mkdirs()
        target.writeText("version=${companionVersion.get()}\n", Charsets.UTF_8)
    }
}
sourceSets.main { resources.srcDir(identityDirectory) }
tasks.processResources { dependsOn(generateCompanionIdentity) }
