package com.sphereon.gradle.buildsupport

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.findByType
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
        project.pluginManager.apply("org.gradle.java-platform")
        project.pluginManager.apply("maven-publish")

        val isPluginBom = project.name.contains("gradle-plugin")
        val targetSection = if (isPluginBom) "plugins" else "libraries"
        val outputDir = project.layout.buildDirectory.dir("tomlCatalog")

        // Pre-resolve all project data during configuration time (CC-safe)
        val projectName = project.name
        val projectGroup = project.group.toString()
        val projectVersion = project.version.toString()
        val rootGroup = project.rootProject.group.toString()
        val buildFileContent = project.file("build.gradle.kts").let { if (it.exists()) it.readText() else "" }

        // Pre-resolve nested project metadata (group, name, version, build file content)
        val nestedProjectData = mutableMapOf<String, ProjectData>()
        collectNestedProjectData(project, buildFileContent, nestedProjectData)

        val generateTomlCatalogTask = project.tasks.register("generateTomlCatalog", GenerateTomlCatalogTask::class.java) {
            group = "versioning"
            description = "Generates TOML version catalogs for this BOM"

            this.bomName.set(projectName)
            this.bomGroup.set(projectGroup)
            this.bomVersion.set(projectVersion)
            this.rootGroup.set(rootGroup)
            this.targetSection.set(targetSection)
            this.buildFileContent.set(buildFileContent)
            this.nestedProjects.set(nestedProjectData.mapValues { (_, v) -> "${v.group}|${v.name}|${v.version}|${v.buildFileContent}" })
            this.outputDirectory.set(outputDir)
        }

        configureTomlPublications(project)
    }

    /**
     * Recursively collect project metadata for all platform project references
     * found in build file content. This runs during configuration time.
     */
    private fun collectNestedProjectData(
        project: Project,
        content: String,
        collected: MutableMap<String, ProjectData>,
        depth: Int = 0
    ) {
        if (depth > 5) return // safety limit
        val pattern = """api\(platform\(project\("([^"]+)"\)\)\)""".toRegex()
        pattern.findAll(content).forEach { match ->
            val path = match.groupValues[1]
            if (!collected.containsKey(path)) {
                try {
                    val nested = project.project(path)
                    val nestedBuildFile = nested.file("build.gradle.kts")
                    val nestedContent = if (nestedBuildFile.exists()) nestedBuildFile.readText() else ""
                    collected[path] = ProjectData(
                        group = nested.group.toString(),
                        name = nested.name,
                        version = nested.version.toString(),
                        buildFileContent = nestedContent
                    )
                    collectNestedProjectData(project, nestedContent, collected, depth + 1)
                } catch (_: Exception) {
                    // project not found, skip
                }
            }
        }
    }

    private fun configureTomlPublications(project: Project) {
        val publishing = project.extensions.findByType(PublishingExtension::class) ?: return

        val tokens = project.name.split("-")
            .joinToString("") { it.replaceFirstChar(Char::uppercase) }
        val catalogName = "sphereon$tokens"

        val genToml = project.tasks.named("generateTomlCatalog")

        val versionedToml = project.layout.buildDirectory
            .file("tomlCatalog/$catalogName.versioned.toml")
        val bomToml = project.layout.buildDirectory
            .file("tomlCatalog/$catalogName.toml")

        val catalogElements = project.configurations.create("${catalogName}CatalogElements") {
            isCanBeResolved = false
            isCanBeConsumed = true
            attributes {
                attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.REGULAR_PLATFORM))
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.VERSION_CATALOG))
                attribute(Attribute.of("sphereon.catalog.type", String::class.java), "toml-bom")
            }
            outgoing.artifact(versionedToml) {
                builtBy(genToml)
                extension = "toml"
            }
            outgoing.artifact(bomToml) {
                builtBy(genToml)
                extension = "toml"
                classifier = "bom"
            }
        }

        val componentName = "${catalogName}Catalog"
        val catalogComponent = scf.adhoc(componentName).apply {
            addVariantsFromConfiguration(catalogElements) { /* no-op */ }
        }
        project.components.add(catalogComponent)

        publishing.publications.create("bomCatalog", MavenPublication::class.java) {
            from(catalogComponent)
            artifactId = project.name
            version = project.version.toString()
            groupId = project.group.toString()
            pom { packaging = "pom" }
        }
    }
}

data class ProjectData(
    val group: String,
    val name: String,
    val version: String,
    val buildFileContent: String
)
