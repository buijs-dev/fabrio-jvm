import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SourcesJar

// A published library module: apply this one and set `description`.
plugins {
    id("fabrio.kotlin")
    id("fabrio.publishing")
    id("fabrio.formatting")
    id("fabrio.coverage")
    id("fabrio.dokka")
    `java-library`
}

mavenPublishing {
    configure(KotlinJvm(JavadocJar.Dokka("dokkaGeneratePublicationHtml"), SourcesJar.Sources()))
}
