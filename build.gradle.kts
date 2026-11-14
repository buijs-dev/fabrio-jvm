plugins {
    id("fabrio.coverage")
}

// One coverage report over every module.
dependencies {
    subprojects
        .filter { it.name != "fabrio-bom" }
        .forEach { kover(project(it.path)) }
}
