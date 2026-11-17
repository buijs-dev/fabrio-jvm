import dev.buijs.fabrio.gradle.Fabrio

// KDoc as HTML: `./gradlew dokkaGenerate` writes build/dokka/html. The root applies it too, with
// `dependencies { dokka(project(":module")) }`, for one site over all modules (the API reference
// of the developer portal).
plugins {
    id("org.jetbrains.dokka")
}

dokka {
    moduleName.set(if (project == rootProject) Fabrio.DISPLAY_NAME else project.name)

    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory.set(rootDir)
            remoteUrl("${Fabrio.GITHUB_URL}/tree/main")
            remoteLineSuffix.set("#L")
        }
    }
}
