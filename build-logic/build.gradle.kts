plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.plugin.jvm)
    implementation(libs.kover.plugin)
    implementation(libs.dokka.plugin)
    implementation(libs.buijs.style.plugin)
    implementation(libs.maven.publish.plugin)
}
