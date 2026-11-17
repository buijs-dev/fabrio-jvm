// The build logic of Fabrio JVM: the fabrio.* convention plugins the modules apply.
dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        maven("https://repo.repsy.io/buijs-dev/maven2") {
            mavenContent {
                includeGroupByRegex("dev\\.buijs.*")
            }
        }
    }

    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}

rootProject.name = "build-logic"
