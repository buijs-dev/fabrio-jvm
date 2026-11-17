plugins {
    kotlin("jvm") version "2.4.21"
    application
}

kotlin { jvmToolchain(25) }

dependencies {
    implementation("dev.buijs.fabrio:fabrio-codegen:1.0.0-SNAPSHOT")
}

application {
    mainClass = "RegenerateKt"
}

// ./gradlew -p tools/reference run: regenerates fabrio-codegen/src/test/resources/reference/expected.
tasks.named<JavaExec>("run") {
    val reference = rootDir.resolve("../../fabrio-codegen/src/test/resources/reference")
    args(reference.absolutePath, layout.buildDirectory.dir("work").get().asFile.absolutePath)
}
