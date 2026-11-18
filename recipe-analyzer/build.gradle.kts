plugins { `java-library` }

dependencies {
    api(project(":recipe-engine"))
    api(project(":registry"))
    api(project(":companion-protocol"))
    api(project(":integrations"))
    implementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation(project(":companion-client"))
}
tasks.test {
    maxHeapSize = "1536m"
    listOf("analyzerSnapshot", "analyzerStress").forEach { key -> providers.gradleProperty(key).orNull?.let { systemProperty(key,it) } }
    testLogging { events("failed", "skipped") }
}
