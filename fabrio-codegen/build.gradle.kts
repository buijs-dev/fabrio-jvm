plugins {
    id("fabrio.library")
}

description =
    "The Fabrio code generator: the contract IR built from a Rialto contract, the passes per " +
    "profile and the emitters of the generated files"

dependencies {
    // The Rialto contract model, loading and validation: the IR is built from it.
    api(libs.rialto.core)

    testImplementation(libs.bundles.test)
    testImplementation(kotlin("test"))
}

// The reference output of the safety net (src/test/resources/reference) is an input of the tests:
// a change to it reruns them rather than taking them from the build cache. It is under resources,
// so Gradle tracks it already; the report of its coverage is an output.
tasks.test {
    val report = layout.buildDirectory.file("reports/reference/coverage.txt")
    outputs.file(report).withPropertyName("referenceCoverage")
    systemProperty("fabrio.referenceReport", report.get().asFile.absolutePath)
}
