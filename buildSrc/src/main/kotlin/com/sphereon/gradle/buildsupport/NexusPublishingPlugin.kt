package com.sphereon.gradle.buildsupport
//import com.vanniktech.maven.publish.SonatypeHost

//import io.github.gradlenexus.publishplugin.NexusPublishExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Plugin that applies and configures the nexus-publish plugin.
 */
class NexusPublishingPlugin : Plugin<Project> {

    companion object {
        private const val DEFAULT_NEXUS_URL = "https://s01.oss.sonatype.org/service/local/"
        private const val DEFAULT_SNAPSHOT_URL = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
    }

    override fun apply(project: Project) {
        project.plugins.apply("io.github.gradle-nexus.publish-plugin")

        println("Applying Nexus Publish Plugin for project: ${project.name}")
        // Configure the Nexus Publish Extension
        /* project.extensions.configure(MavenPublishin::class.java) {
            repositories {
                sonatype {
                    nexusUrl.set(
                        project.uri(*//*project.findProperty("nexusUrl") as String? ?:*//* DEFAULT_NEXUS_URL)
                    )
                    snapshotRepositoryUrl.set(
                        project.uri(*//*project.findProperty("snapshotRepositoryUrl") as String? ?:*//* DEFAULT_SNAPSHOT_URL)
                    )
                  *//*  username.set(
                        project.findProperty("sonatypeUsername")?.toString() ?: ""
                    )
                    password.set(
                        project.findProperty("sonatypePassword")?.toString() ?: ""
                    )*//*
                }
            }
        }*/
    }
}