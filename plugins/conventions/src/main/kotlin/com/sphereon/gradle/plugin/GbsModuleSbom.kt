package com.sphereon.gradle.plugin

import org.cyclonedx.Version
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.model.Component
import org.cyclonedx.model.OrganizationalEntity
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

/**
 * Publishes a CycloneDX SBOM next to every published module, as `<artifactId>-<version>-cyclonedx.json`,
 * the classifier and extension the CycloneDX Maven plugin uses. A consumer, a scanner or the release
 * inventory can then fetch the exact third-party content of any version from the repository.
 *
 * The SBOM covers the module's runtime classpaths of production code, the libraries a consumer
 * receives. It carries no serial number or timestamp, so the same inputs give the same bytes and
 * the task stays cacheable. `sphereon.moduleSbom=off` disables it.
 *
 * A Kotlin Multiplatform module attaches the SBOM to its root `kotlinMultiplatform` publication,
 * which every target publication belongs to; any other module attaches it to each publication.
 */
object GbsModuleSbom {
    const val PROPERTY = "sphereon.moduleSbom"
    const val CLASSIFIER = "cyclonedx"
    const val OUTPUT = "reports/cyclonedx/module/bom.cdx.json"
    const val KMP_ROOT_PUBLICATION = "kotlinMultiplatform"

    /** Runtime classpaths of production code, the ones a consumer of the published module receives. */
    const val INCLUDE = "(?i).*RuntimeClasspath"
    const val SKIP = "(?i).*test.*"

    /** Which publications carry the SBOM: the multiplatform root publication when there is one, else all. */
    fun attachesTo(publication: String, multiplatform: Boolean): Boolean =
        !multiplatform || publication == KMP_ROOT_PUBLICATION
}

fun Project.configureGbsModuleSbom() {
    if (providers.gradleProperty(GbsModuleSbom.PROPERTY).orNull == "off") return
    plugins.withId("maven-publish") {
        pluginManager.apply("org.cyclonedx.bom")
        val output = layout.buildDirectory.file(GbsModuleSbom.OUTPUT)
        val sbom = tasks.named("cyclonedxDirectBom", CyclonedxDirectTask::class.java) {
            includeConfigs.set(listOf(GbsModuleSbom.INCLUDE))
            skipConfigs.set(listOf(GbsModuleSbom.SKIP))
            projectType.set(Component.Type.LIBRARY)
            schemaVersion.set(Version.VERSION_16)
            includeBomSerialNumber.set(false)
            includeBuildSystem.set(false)
            organizationalEntity.set(OrganizationalEntity().apply {
                name = "Sphereon International B.V."
                urls = listOf("https://sphereon.com")
            })
            jsonOutput.set(output)
            xmlOutput.unsetConvention()
        }
        // Decided per publication: the Kotlin plugin creates its publications after it is applied,
        // which may be after maven-publish.
        extensions.getByType(PublishingExtension::class.java).publications.withType(MavenPublication::class.java)
            .matching { GbsModuleSbom.attachesTo(it.name, plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) }
            .configureEach {
                artifact(output) {
                    classifier = GbsModuleSbom.CLASSIFIER
                    extension = "json"
                    builtBy(sbom)
                }
            }
    }
}
