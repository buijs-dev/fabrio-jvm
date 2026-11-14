pluginManagement {
    // The fabrio.* convention plugins.
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}

// Every module resolves from here; snapshots of dev.buijs artifacts (kotlin-style, Rialto) come
// from Repsy or Maven Local.
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        maven("https://repo.repsy.io/mvn/buijs-dev/maven") {
            mavenContent {
                snapshotsOnly()
                includeGroupByRegex("dev\\.buijs.*")
            }
        }
    }
}

rootProject.name = "fabrio-jvm"

// Code generation: the contract IR, the passes and the emitters. The runtime modules come with
// the slice that needs them (see README.md).
include(":fabrio-codegen")

// Alignment for consumers
include(":fabrio-bom")
