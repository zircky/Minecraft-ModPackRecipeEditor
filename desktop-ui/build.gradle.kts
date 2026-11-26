plugins { `java-library` }

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation(project(":integrations"))
    implementation(project(":companion-client"))
    implementation(project(":exporters"))
    implementation(project(":recipe-engine"))
    implementation(project(":core"))
    implementation("org.slf4j:slf4j-api:2.0.16")
    api("org.openjfx:javafx-controls:21.0.6:win")
    api("org.openjfx:javafx-graphics:21.0.6:win")
    api("org.openjfx:javafx-base:21.0.6:win")
}

dependencies { testImplementation(project(":companion-server")) }

tasks.test {
    providers.gradleProperty("runtimeSnapshotFile").orNull?.let { file ->
        systemProperty("runtimeSnapshotFile", file)
        maxHeapSize = "1536m"
    }
}
