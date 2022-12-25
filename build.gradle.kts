group = "valkyrie.language"
version = "0.0.0"


plugins {
    kotlin("jvm") version "1.9.0"
    id("org.graalvm.buildtools.native") version "0.9.27"
    id("org.graalvm.plugin.truffle-language") version "0.1.0-alpha2"
    application
}



repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.graalvm.truffle:truffle-api:22.1.0")
    annotationProcessor("org.graalvm.truffle:truffle-dsl-processor:22.1.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
graal {
    version ="23.1.0"
    languageId ="valkyrie.runtime"    // required
    languageName ="Valkyrie"           // defaults to project name
}
application {
    mainClass.set("MainKt")
}