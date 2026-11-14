// A build of its own, outside fabrio-jvm: it runs the generator that the reference output was
// made with (the source-scanning fabrio-codegen 1.0.0-SNAPSHOT, before the IR), which is not part of
// this repository. See docs/architecture.md, "The safety net".
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        // The legacy fabrio-codegen and the rialto-core it was built with (with the generator API),
        // both published to Maven Local from their source trees.
        mavenLocal {
            mavenContent { includeGroupByRegex("dev\\.buijs.*") }
        }
        mavenCentral()
    }
}

rootProject.name = "fabrio-reference"
