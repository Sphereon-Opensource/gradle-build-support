package com.sphereon.gradle.buildsupport

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import javax.inject.Inject

/**
 * Plugin that generates TOML version catalog files from BOM projects.
 *
 * This plugin creates two TOML files for each BOM project:
 * 1. A TOML file with versions (for standalone use)
 * 2. A TOML file without versions (for use with a BOM)
 *
 * It also configures Maven publications for both TOML files.
 */
class TomlCatalogPlugin @Inject constructor(
    private val scf: SoftwareComponentFactory
) : Plugin<Project> {
    override fun apply(project: Project) {
        // Ensure the maven-publish plugin is applied
        /*if (!project.plugins.hasPlugin("maven-publish")) {
            project.plugins.apply("maven-publish")
        }*/

        // ensure we have both containers
        project.pluginManager.apply("org.gradle.java-platform")
        project.pluginManager.apply("maven-publish")

        val isPluginBom = project.name.contains("gradle-plugin")
        val targetSection = if (isPluginBom) "plugins" else "libraries"
        val outputDir = project.layout.buildDirectory.dir("tomlCatalog").get().asFile

        // Register a task to generate both TOML files
        val generateTomlCatalogTask = project.tasks.register("generateTomlCatalog") {
            group = "versioning"
            description = "Generates TOML version catalogs for this BOM"

            outputs.dir(outputDir)

            doLast {
                outputDir.mkdirs()

                // Generate TOML file with versions
                project.generateTomlFromBom(
                    outputDir = outputDir,
                    targetSection = targetSection,
                    includeVersions = true
                )

                // Generate TOML file without versions (for use with BOM)
                project.generateTomlFromBom(
                    outputDir = outputDir,
                    targetSection = targetSection,
                    includeVersions = false
                )
            }
        }

        // Configure Maven publications for both TOML files during configuration phase
        configureTomlPublications(project)
    }

    /**
     * Configures Maven publications for both TOML files.
     */
    private fun configureTomlPublications(project: Project) {
        val publishing = project.extensions.findByType(PublishingExtension::class) ?: return

        // build the "sphereon"-prefixed catalog name
        val tokens = project.name.split("-")
            .joinToString("") { it.replaceFirstChar(Char::uppercase) }
        val catalogName = "sphereon$tokens"

        // locate your TOML-gen task
        val genToml = project.tasks.named("generateTomlCatalog")

        // lazy Providers for the two files
        val versionedToml = project.layout.buildDirectory
            .file("tomlCatalog/$catalogName.versioned.toml")
        val bomToml = project.layout.buildDirectory
            .file("tomlCatalog/$catalogName.toml")

        // 1) one consumable configuration, platform+version-catalog
        val catalogElements = project.configurations.create("${catalogName}CatalogElements") {
            isCanBeResolved = false
            isCanBeConsumed = true
            attributes {
                attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    project.objects.named(Category.REGULAR_PLATFORM)
                )
                attribute(
                    Usage.USAGE_ATTRIBUTE,
                    project.objects.named(Usage.VERSION_CATALOG)
                )
            }
            // primary, no classifier → the "versioned" TOML
            outgoing.artifact(versionedToml) {
                builtBy(genToml)
                extension = "toml"
            }
            // secondary, classifier="bom" → the version-less BOM TOML
            outgoing.artifact(bomToml) {
                builtBy(genToml)
                extension = "toml"
                classifier = "bom"
            }
        }

        // 2) one AdhocComponent from that configuration
        val componentName = "${catalogName}Catalog"
        val catalogComponent = scf.adhoc(componentName).apply {
            // declare the single version-catalog variant
            addVariantsFromConfiguration(catalogElements) { /* no-op */ }
        }
        project.components.add(catalogComponent)

        // 3) one MavenPublication that pulls in both artifacts
        publishing.publications.create("bomCatalog", MavenPublication::class.java) {
            from(catalogComponent)
            artifactId = project.name       // e.g. "common-bom"
            version = project.version.toString()
            groupId = project.group.toString()
            pom { packaging = "pom" }
        }

        project.logger.lifecycle(
            "⚙️ Configured single publication '${project.name}: ${project.group}:${project.name}:${project.version}' " +
                    "with two TOMLs (primary, plus classifier=bom)"
        )
    }

}