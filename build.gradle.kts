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
    testImplementation("junit:junit:4.12")
    testImplementation("org.graalvm.truffle:truffle-tck:${graalVM}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
        defaultMode.set("standard") // Default agent mode if one isn't specified using `-Pagent=mode_name`
        enabled.set(true) // Enables the agent

        modes {
            // The standard agent mode generates metadata without conditions.
            standard {
            }
            // The conditional agent mode generates metadata with conditions.
            conditional {
                userCodeFilterPath.set("path-to-filter.json") // Path to a filter file that determines classes which will be used in the metadata conditions.
                extraFilterPath.set("path-to-another-filter.json") // Optional, extra filter used to further filter the collected metadata.
            }
            // The direct agent mode allows users to directly pass options to the agent.
            direct {
                // {output_dir} is a special string expanded by the plugin to where the agent files would usually be output.
                options.add("config-output-dir={output_dir}")
                options.add("experimental-configuration-with-origins")
            }
        }

        callerFilterFiles.from("filter.json")
        accessFilterFiles.from("filter.json")
        builtinCallerFilter.set(true)
        builtinHeuristicFilter.set(true)
        enableExperimentalPredefinedClasses.set(false)
        enableExperimentalUnsafeAllocationTracing.set(false)
        trackReflectionMetadata.set(true)

        // Copies metadata collected from tasks into the specified directories.
        metadataCopy {
            inputTaskNames.add("test") // Tasks previously executed with the agent attached.
            outputDirectories.add("src/main/resources/META-INF/native-image")
            mergeWithExisting.set(true) // Instead of copying, merge with existing metadata in the output directories.
        }

        /*
        By default, if `-Pagent` is specified, all tasks that extend JavaForkOptions are instrumented.
        This can be limited to only specific tasks that match this predicate.
         */
//        tasksToInstrumentPredicate.set(true)
    }

    binaries {
        named("main") {
            // Main options
            imageName.set("application") // The name of the native image, defaults to the project name
            mainClass.set("org.test.Main") // The main class to use, defaults to the application.mainClass
            debug.set(true) // Determines if debug info should be generated, defaults to false (alternatively add --debug-native to the CLI)
            verbose.set(true) // Add verbose output, defaults to false
            fallback.set(true) // Sets the fallback mode of native-image, defaults to false
            sharedLibrary.set(false) // Determines if image is a shared library, defaults to false if `java-library` plugin isn't included
            quickBuild.set(false) // Determines if image is being built in quick build mode (alternatively use GRAALVM_QUICK_BUILD environment variable, or add --native-quick-build to the CLI)
            richOutput.set(false) // Determines if native-image building should be done with rich output

            systemProperties.putAll(
                mapOf(
                    "name1" to "value1",
                    "name2" to "value2"
                )
            ) // Sets the system properties to use for the native image builder
            configurationFileDirectories.from(file("src/my-config")) // Adds a native image configuration file directory, containing files like reflection configuration
            excludeConfig.put(
                "org.example.test:artifact:version",
                listOf("^/META-INF/native-image/.*", "^/config/.*")
            ) // Excludes configuration that matches one of given regexes from JAR of dependency with said coordinates.
            excludeConfig.put(file("path/to/artifact.jar"), listOf("^/META-INF/native-image/.*", "^/config/.*"))

            // Advanced options
            buildArgs.add("--link-at-build-time") // Passes '--link-at-build-time' to the native image builder options. This can be used to pass parameters which are not directly supported by this extension
            jvmArgs.add("flag") // Passes 'flag' directly to the JVM running the native image builder

            // Runtime options
            runtimeArgs.add("--help") // Passes '--help' to built image, during "nativeRun" task

            useFatJar.set(true) // Instead of passing each jar individually, builds a fat jar
        }
    }
}
application {
    mainClass.set("MainKt")
}