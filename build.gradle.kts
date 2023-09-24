import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "valkyrie.language"
version = "0.0.0"
val antlrVersion = "4.12.0"
val graalVM = "23.1.0"

plugins {
    antlr
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
    antlr("org.antlr:antlr4:${antlrVersion}")
    implementation("org.graalvm.truffle:truffle-api:${graalVM}")
    annotationProcessor("org.graalvm.truffle:truffle-dsl-processor:${graalVM}")
}

tasks.test {
    useJUnitPlatform()
}
tasks.generateGrammarSource {
    maxHeapSize = "64m"
    arguments = arguments + listOf(
        "-listener",
        "-visitor",
        "-long-messages",
        "-encoding", "utf8",
        "-package", "valkyrie.antlr"
    )
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "17"
}
compileKotlin.dependsOn("generateGrammarSource")

graal {
    version = graalVM
    languageId = "valkyrie"
    languageName = "Valkyrie Language"
}
application {
    mainClass.set("MainKt")
}