import com.vanniktech.maven.publish.JavaPlatform

plugins {
    `java-platform`
    id("fabrio.publishing")
}

description = "The versions of every Fabrio JVM module, to import as a platform"

mavenPublishing { configure(JavaPlatform()) }

dependencies {
    constraints {
        rootProject.subprojects
            .filter { it != project }
            .forEach { api(project(it.path)) }
    }
}
