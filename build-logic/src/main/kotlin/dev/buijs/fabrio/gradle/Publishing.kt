/**
 * Copyright (c) 2021 - 2026 Buijs Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package dev.buijs.fabrio.gradle

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension

/**
 * The defaults every publication shares. The group and version come from gradle.properties; a
 * module only sets its own `description`, and its artifact id is its project name.
 *
 * Releases go to Maven Central (`publishToMavenCentral`), snapshots to Repsy
 * (`publishAllPublicationsToRepsyRepository`), with the credentials the buijs-dev/ci-templates
 * publish workflows set. Publications are signed only when a signing key is configured
 * (`signingInMemoryKey` or `signing.keyId`), so publishing to Maven Local needs none.
 */
fun Project.configurePublishing() {
    val pomName = if (this == rootProject) Fabrio.DISPLAY_NAME else "${Fabrio.DISPLAY_NAME} - $name"
    val pomDescription = provider { description }
    val signed =
        providers.gradleProperty("signingInMemoryKey").isPresent ||
            providers.gradleProperty("signing.keyId").isPresent

    extensions.configure(MavenPublishBaseExtension::class.java) {
        publishToMavenCentral(automaticRelease = true)
        if (signed) signAllPublications()
        coordinates(group.toString(), name, version.toString())

        pom {
            name.convention(pomName)
            description.convention(pomDescription)
            url.convention(Fabrio.SITE)

            licenses {
                license {
                    name.set("Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    distribution.set("repo")
                }
            }

            developers {
                developer {
                    id.set("buijs-dev")
                    name.set("Gillian Buijs")
                    email.set("info@buijs.dev")
                }
            }

            scm {
                connection.set("scm:git:${Fabrio.GITHUB_URL}.git")
                developerConnection.set("scm:git:ssh://git@github.com/buijs-dev/${Fabrio.REPOSITORY}.git")
                url.set(Fabrio.GITHUB_URL)
            }
        }
    }

    extensions.configure(PublishingExtension::class.java) {
        repositories {
            maven {
                name = "Repsy"
                setUrl("https://repo.repsy.io/mvn/buijs-dev/maven")
                credentials {
                    username = providers.environmentVariable("REPSY_USERNAME").orNull
                    password = providers.environmentVariable("REPSY_PASSWORD").orNull
                }
            }
        }
    }
}
