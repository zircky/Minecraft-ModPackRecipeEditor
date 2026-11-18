plugins { `java-library` }

dependencies {
    implementation("org.tomlj:tomlj:1.1.1")
    implementation("org.slf4j:slf4j-api:2.0.16")
    api(project(":core"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
}

tasks.test {
    providers.gradleProperty("realModpack").orNull?.let { systemProperty("realModpack", it) }
    testLogging { events("failed", "skipped") }
}
