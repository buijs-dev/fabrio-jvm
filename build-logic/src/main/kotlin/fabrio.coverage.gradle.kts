// Code coverage with Kover on the JaCoCo engine: `check` writes build/reports/kover/report.xml
// (JaCoCo XML) and build/reports/kover/html. The root applies it too, with
// `dependencies { kover(project(":module")) }`, for one report over all modules.
plugins {
    base
    id("org.jetbrains.kotlinx.kover")
}

val jacoco =
    extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")
        .findVersion("jacoco")
        .get()
        .requiredVersion

kover {
    useJacoco(jacoco)

    reports {
        filters { excludes { annotatedBy("*Generated*") } }

        total {
            xml { onCheck = true }

            html { onCheck = true }
        }
    }
}
