package com.sphereon.gradle.buildsupport

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.signing.SigningExtension


/**
 * Configures Nexus publishing for the root project.
 * This should be called from the root project.
 *
 * Note: This function doesn't directly configure the NexusPublishExtension
 * to avoid dependency issues. Instead, it applies the plugin and expects
 * the configuration to be done in a separate script file.
 */
fun Project.configureNexusPublishing() {
    if (this != rootProject) {
        logger.warn("configureNexusPublishing() should only be called from the root project")
        return
    }

    // Apply the nexus-publish plugin if it's not already applied
    if (!plugins.hasPlugin("com.vanniktech.maven.publish")) {
        plugins.apply("com.vanniktech.maven.publish")
    }
}

/**
 * Configures Maven publications with consistent metadata and signing.
 * This should be called from any project that has Maven publications.
 */
fun Project.configureProjectPublication() {
    // Ensure required plugins are applied
    if (!plugins.hasPlugin("maven-publish")) {
        plugins.apply("maven-publish")
    }
    if (!plugins.hasPlugin("signing")) {
        plugins.apply("signing")
    }

    // Delay configuration until after project evaluation to ensure all publications are added.
    afterEvaluate {
        // Configure all Maven publications
        val publishing = extensions.findByType(PublishingExtension::class.java)
        publishing?.publications
            ?.withType(MavenPublication::class.java)
            ?.configureEach {
                // Register a stub javadoc jar task and add it as an artifact.
                val javadocJarProvider = tasks.register("${name}JavadocJar", Jar::class.java) {
                    archiveClassifier.set("javadoc")
                    archiveAppendix.set(name)
                    // Note: You can add content to the jar if necessary.
                }
                artifact(javadocJarProvider)

                // Configure the publication's POM details.
                pom {
                    name.set("Gradle build support plugins and BOMs for consistent project setup")
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
            }

        // Configure signing if the property 'signing.gnupg.keyName' is set.
        val signingExtension = extensions.findByType(SigningExtension::class.java)
        if (hasProperty("signing.gnupg.keyName")) {
            signingExtension?.useGpgCmd()
            // Sign all publications.
            signingExtension?.sign(publishing?.publications)
        }
    }
}

/**
 * Ensures the project has the proper Apache 2.0 license.
 * Throws an exception if the license isn't Apache 2.0 or no license file is found.
 */
fun Project.ensureApacheLicense() {
    // Define the expected license file
    val licenseFile = project.file("LICENSE.md").takeIf { it.exists() }
        ?: project.file("LICENSE").takeIf { it.exists() }

    if (licenseFile == null) {
        throw GradleException("Publishing to Sonatype is not allowed: LICENSE.md or LICENSE file is missing.")
    }

    // Check if the license content mentions Apache 2.0
    val isApacheLicense = licenseFile.readText().contains("Apache License", ignoreCase = true) &&
            licenseFile.readText().contains("Version 2.0", ignoreCase = true)

    if (!isApacheLicense) {
        throw GradleException("Publishing to Sonatype is not allowed: The license is not Apache 2.0.")
    }
}