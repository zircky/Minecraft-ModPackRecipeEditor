plugins { `java-library` }

dependencies {
    implementation(project(":integrations"))
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation(project(":core"))
    api(project(":recipe-engine"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
}
