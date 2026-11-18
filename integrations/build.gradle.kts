plugins { `java-library` }

dependencies {
    api(project(":recipe-engine"))
    api("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation(project(":core"))
    api(project(":companion-protocol"))
}
