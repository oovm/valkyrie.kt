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
    antlr("org.antlr:antlr4:${antlrVersion}")
    implementation("org.graalvm.truffle:truffle-api:${graalVM}")
    annotationProcessor("org.graalvm.truffle:truffle-dsl-processor:${graalVM}")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.1")
    testImplementation("org.graalvm.truffle:truffle-tck:${graalVM}")
//    testImplementation("org.junit.platform:junit-platform-launcher")
}
configurations {
    runtimeOnly {

    }
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
graalvmNative {
    agent {

    }

    binaries {
        named("main") {
            imageName.set("valkyrie-lib") //
            mainClass.set("Main")
            sharedLibrary.set(true)
        }

        named("main") {
            imageName.set("valkyrie-vm") //
            mainClass.set("Main")
            sharedLibrary.set(false)
//            pgoInstrument.set(true)
            useFatJar.set(true)
        }
    }
}

application {
    mainClass.set("Main")
}