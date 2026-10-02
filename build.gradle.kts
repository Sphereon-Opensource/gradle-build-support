plugins {
    `kotlin-dsl`
    `maven-publish`
    alias(libs.plugins.vanniktech.mavenPublish)
}
repositories {
    mavenCentral()
    gradlePluginPortal()
}
dependencies {
    implementation(gradleApi())
}

allprojects {
    group = "$group"
    // The npm plugin derives each consuming module's version from its JS build.

    plugins.withType<MavenPublishPlugin> {
        configure<PublishingExtension> {
            repositories {
                val worktreeMavenRepo = System.getenv("WORKTREE_MAVEN_REPO")?.trim()?.takeIf { it.isNotEmpty() }
                if (worktreeMavenRepo != null) {
                    maven {
                        name = "worktree"
                        url = uri(worktreeMavenRepo)
                    }
                }
                maven {
                    name = "sphereon-opensource"
                    val snapshotsUrl = "https://nexus.sphereon.com/repository/sphereon-opensource-snapshots/"
                    val releasesUrl = "https://nexus.sphereon.com/repository/sphereon-opensource-releases/"
                    url = uri(if (version.toString().contains("SNAPSHOT")) snapshotsUrl else releasesUrl)
                    credentials {
                        username = System.getenv("NEXUS_USERNAME")
                        password = System.getenv("NEXUS_PASSWORD")
                    }
                }
            }

            // Ensure unique coordinates for different publication types
            publications.withType<MavenPublication> {
                val publicationName = name
            }
        }
    }
}

mavenPublishing {
    repositories {
        val worktreeMavenRepo = System.getenv("WORKTREE_MAVEN_REPO")?.trim()?.takeIf { it.isNotEmpty() }
        if (worktreeMavenRepo != null) {
            maven {
                name = "worktree"
                url = uri(worktreeMavenRepo)
            }
        }
        maven {
            name = "sphereon-opensource"
            val snapshotsUrl = "https://nexus.sphereon.com/repository/sphereon-opensource-snapshots/"
            val releasesUrl = "https://nexus.sphereon.com/repository/sphereon-opensource-releases/"
            url = uri(if (version.toString().contains("SNAPSHOT")) snapshotsUrl else releasesUrl)
            credentials {
                username = System.getenv("NEXUS_USERNAME")
                password = System.getenv("NEXUS_PASSWORD")
            }
        }
    }

    // Configure POM
    pom {
        name.set(project.name)
        description.set("Gradle build support plugins and BOMs for consistent project setup")
        url.set("https://github.com/sphereon-opensource/gradle-build-support")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("Sphereon")
                name.set("Sphereon")
                organization.set("Sphereon")
                organizationUrl.set("https://sphereon.com")
            }
        }
        scm {
            url.set("https://github.com/sphereon-opensource/gradle-build-support")
        }
    }

    /*    // Configure signing if the property 'signing.gnupg.keyName' is set
        if (project.hasProperty("signing.gnupg.keyName")) {
            signing {
                enabled.set(true)
                useGpgCmd()
            }
        }*/
}
