// A build of its own, outside fabrio-jvm: it generates the reference outputs with the reference
// generator (fabrio-codegen 1.0.0-SNAPSHOT from Maven Local). See docs/architecture.md, "The safety net".
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        // The reference generator and the rialto-core with the generator API, both from Maven Local.
        mavenLocal {
            mavenContent { includeGroupByRegex("dev\\.buijs.*") }
        }
        mavenCentral()
    }
}

rootProject.name = "fabrio-reference"
