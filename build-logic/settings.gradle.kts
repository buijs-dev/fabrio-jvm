// The build logic of Fabrio JVM: the fabrio.* convention plugins the modules apply.
dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        maven("https://repo.repsy.io/mvn/buijs-dev/maven")
    }

    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}

rootProject.name = "build-logic"
