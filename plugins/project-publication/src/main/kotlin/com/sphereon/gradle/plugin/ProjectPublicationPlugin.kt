package com.sphereon.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class ProjectPublicationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Ensure required plugins are applied
        project.plugins.apply("maven-publish")
        project.plugins.apply("signing")
        /*project.plugins.apply("com.vanniktech.maven.publish")

        // Configure immediately instead of in afterEvaluate to avoid capturing project objects
        project.extensions.findByType<MavenPublishBaseExtension>()?.apply {
            publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
            // Configure POM
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


            *//*    // Configure signing if the property 'signing.gnupg.keyName' is set
                if (project.hasProperty("signing.gnupg.keyName")) {
                    signing {
                        enabled.set(true)
                        useGpgCmd()
                    }
                }*//*
        }*/

        /*// Configure signing if the property 'signing.gnupg.keyName' is set.
        val signingExtension = project.extensions.findByType(SigningExtension::class.java)
        if (project.hasProperty("signing.gnupg.keyName")) {
            signingExtension?.useGpgCmd()
            // Sign all publications.
            signingExtension?.sign(publishing?.publications)
        }*/
    }
}