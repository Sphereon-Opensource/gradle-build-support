package com.sphereon.gradle.buildsupport

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Configuration-cache-safe task that generates TOML version catalog files from
 * pre-resolved BOM project data. All project data is captured at configuration
 * time by [TomlCatalogPlugin] and passed as serializable [Input] properties.
 */
abstract class GenerateTomlCatalogTask : DefaultTask() {

    @get:Input
    abstract val bomName: Property<String>

    @get:Input
    abstract val bomGroup: Property<String>

    @get:Input
    abstract val bomVersion: Property<String>

    @get:Input
    abstract val rootGroup: Property<String>

    @get:Input
    abstract val targetSection: Property<String>

    @get:Input
    abstract val buildFileContent: Property<String>

    /** Nested project metadata serialized as "group|name|version|buildFileContent" per key. */
    @get:Input
    abstract val nestedProjects: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val outDir = outputDirectory.get().asFile
        outDir.mkdirs()

        generateToml(outDir, includeVersions = true)
        generateToml(outDir, includeVersions = false)
    }

    private fun generateToml(outputDir: File, includeVersions: Boolean) {
        val bName = bomName.get()
        val bGroup = bomGroup.get()
        val bVersion = bomVersion.get()
        val rGroup = rootGroup.get()
        val tSection = targetSection.get()
        val content = buildFileContent.get()
        val nested = nestedProjects.get()

        val catalogName = "sphereon" + bName.split("-")
            .joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
        val fileName = if (includeVersions) "$catalogName.versioned.toml" else "$catalogName.toml"
        val outputFile = File(outputDir, fileName)

        val versions = mutableMapOf<String, String>()
        val dependencies = mutableListOf<Triple<String, String, String>>()
        val platformDependencies = mutableSetOf<Pair<String, String>>()
        val pluginDependencies = mutableSetOf<Pair<String, String>>()
        val processedDependencies = mutableSetOf<Pair<String, String>>()

        // Add the BOM itself
        if (processedDependencies.add(bGroup to bName)) {
            dependencies.add(Triple(bGroup, bName, bVersion))
            addVersionSafely(versions, generateVersionKey(bGroup, bName), bVersion, bomVersion = bVersion)
        }

        // Parse regular dependencies from build file
        parseDependencies(content, rGroup, bName, tSection, dependencies, versions,
            platformDependencies, pluginDependencies, processedDependencies, bomVersion = bVersion)

        // Parse project platform dependencies using pre-resolved nested project data
        val platformProjectPattern = """api\(platform\(project\("([^"]+)"\)\)\)""".toRegex()
        platformProjectPattern.findAll(content).forEach { match ->
            val projectPath = match.groupValues[1]
            val serialized = nested[projectPath] ?: return@forEach
            val parts = serialized.split("|", limit = 4)
            if (parts.size < 4) return@forEach
            val (pGroup, pName, pVersion, pContent) = parts

            if (processedDependencies.add(pGroup to pName)) {
                dependencies.add(Triple(pGroup, pName, pVersion))
                addVersionSafely(versions, generateVersionKey(pGroup, pName), pVersion, bomVersion = bVersion)
                platformDependencies.add(pGroup to pName)
                if (pName.contains("gradle-plugin")) {
                    pluginDependencies.add(pGroup to pName)
                }
            }

            // Parse dependencies from the nested platform's build file
            parseDependencies(pContent, rGroup, pName, tSection, dependencies, versions,
                platformDependencies, pluginDependencies, processedDependencies,
                isPluginBomContext = pName.contains("gradle-plugin"), bomVersion = bVersion)

            // Parse nested platform project references
            platformProjectPattern.findAll(pContent).forEach nestedMatch@{ nestedMatch ->
                val nestedPath = nestedMatch.groupValues[1]
                val nestedSerialized = nested[nestedPath] ?: return@nestedMatch
                val nestedParts = nestedSerialized.split("|", limit = 4)
                if (nestedParts.size < 4) return@nestedMatch
                val (nGroup, nName, nVersion, nContent) = nestedParts

                if (processedDependencies.add(nGroup to nName)) {
                    dependencies.add(Triple(nGroup, nName, nVersion))
                    addVersionSafely(versions, generateVersionKey(nGroup, nName), nVersion, bomVersion = bVersion)
                    platformDependencies.add(nGroup to nName)
                }

                parseDependencies(nContent, rGroup, nName, tSection, dependencies, versions,
                    platformDependencies, pluginDependencies, processedDependencies, bomVersion = bVersion)
            }
        }

        // Parse external platform dependencies
        val platformExternalPattern = """api\(platform\("([^:"]+)(?::([^:"]+))?:([^"]+)"\)\)""".toRegex()
        platformExternalPattern.findAll(content).forEach { match ->
            val (group, name, version) = determineDependencyParts(match.groupValues, rGroup, bName, tSection)
            if (group != null && name != null && version != null) {
                if (processedDependencies.add(group to name)) {
                    dependencies.add(Triple(group, name, version))
                    addVersionSafely(versions, generateVersionKey(group, name), version, bomVersion = bVersion)
                    platformDependencies.add(group to name)
                }
            }
        }

        // Write TOML
        writeToml(outputFile, includeVersions, bName, rGroup, tSection, versions, dependencies,
            platformDependencies, pluginDependencies)

        logger.lifecycle("Generated TOML catalog at ${outputFile.absolutePath} (includeVersions=$includeVersions)")
    }

    private fun parseDependencies(
        content: String,
        rootGroup: String,
        contextName: String,
        targetSection: String,
        dependencies: MutableList<Triple<String, String, String>>,
        versions: MutableMap<String, String>,
        platformDependencies: MutableSet<Pair<String, String>>,
        pluginDependencies: MutableSet<Pair<String, String>>,
        processedDependencies: MutableSet<Pair<String, String>>,
        isPluginBomContext: Boolean = false,
        bomVersion: String? = null,
    ) {
        val constraintsPattern = """api\("([^:"]+)(?::([^:"]+))?:([^"]+)"\)""".toRegex()
        constraintsPattern.findAll(content).forEach { match ->
            val (group, name, version) = determineDependencyParts(match.groupValues, rootGroup, contextName, targetSection)
            if (group != null && name != null && version != null) {
                val isPluginOnly = group.isEmpty()
                if (processedDependencies.add(group to name)) {
                    dependencies.add(Triple(group, name, version))
                    addVersionSafely(versions, generateVersionKey(group, name), version, bomVersion = bomVersion)
                    if (isPluginOnly || isPluginBomContext) {
                        pluginDependencies.add(group to name)
                    }
                }
            }
        }
    }

    private fun writeToml(
        outputFile: File,
        includeVersions: Boolean,
        bomName: String,
        rootGroup: String,
        targetSection: String,
        versions: Map<String, String>,
        dependencies: List<Triple<String, String, String>>,
        platformDependencies: Set<Pair<String, String>>,
        pluginDependencies: Set<Pair<String, String>>,
    ) {
        val depsByVersionKey = mutableMapOf<String, Triple<String, String, String>>()
        dependencies.forEach { dep ->
            depsByVersionKey.putIfAbsent(generateVersionKey(dep.first, dep.second), dep)
        }

        outputFile.writer().use { writer ->
            val hasPlatformDeps = dependencies.any { (group, name, _) ->
                name.endsWith("-bom") || platformDependencies.contains(group to name)
            }

            if (includeVersions || hasPlatformDeps || pluginDependencies.isNotEmpty()) {
                writer.appendLine("[versions]")
                versions.forEach { (key, version) ->
                    val shouldInclude = if (!includeVersions) {
                        val matchingDep = depsByVersionKey[key]
                        val isPlatform = matchingDep?.let { (g, n, _) ->
                            n.endsWith("-bom") || platformDependencies.contains(g to n)
                        } ?: false
                        val isThirdPartyPlugin = matchingDep?.let { (g, n, _) ->
                            val isInPluginSection = pluginDependencies.contains(g to n) || targetSection == "plugins"
                            isInPluginSection && if (g.isEmpty()) !n.startsWith("com.sphereon") else !g.startsWith(rootGroup)
                        } ?: false
                        isPlatform || isThirdPartyPlugin
                    } else true

                    if (shouldInclude) {
                        writer.appendLine("$key = \"$version\"")
                    }
                }
                writer.appendLine()
            }

            // Libraries section
            writer.appendLine("[libraries]")
            val usedLibraryKeys = mutableSetOf<String>()
            dependencies.forEach { (group, name, _) ->
                if (!pluginDependencies.contains(group to name)) {
                    val key = generateLibraryKey(group, name)
                    if (key != null && usedLibraryKeys.add(key)) {
                        val isPlatform = name.endsWith("-bom") || platformDependencies.contains(group to name)
                        if (includeVersions || isPlatform) {
                            writer.appendLine("$key = { module = \"$group:$name\", version.ref = \"${generateVersionKey(group, name)}\" }")
                        } else {
                            writer.appendLine("$key = { module = \"$group:$name\" }")
                        }
                    }
                }
            }
            writer.appendLine()

            // Plugins section
            writer.appendLine("[plugins]")
            val usedPluginKeys = mutableSetOf<String>()
            dependencies.forEach { (group, name, _) ->
                if (name != bomName && (pluginDependencies.contains(group to name) || targetSection == "plugins")) {
                    val key = generatePluginKey(group, name)
                    if (usedPluginKeys.add(key)) {
                        val pluginId = when {
                            group.isEmpty() -> name
                            name.contains(".gradle.plugin") -> name.substringBefore(".gradle.plugin")
                            else -> "$group.$name"
                        }
                        val isPlatform = name.endsWith("-bom") || platformDependencies.contains(group to name)
                        val isInternalPlugin = if (group.isEmpty()) name.startsWith("com.sphereon") else group.startsWith(rootGroup)
                        val shouldIncludeVersionRef = includeVersions || (isPlatform && name.endsWith("-bom")) || !isInternalPlugin

                        if (shouldIncludeVersionRef) {
                            writer.appendLine("$key = { id = \"$pluginId\", version.ref = \"${generateVersionKey(group, name)}\" }")
                        } else {
                            writer.appendLine("$key = { id = \"$pluginId\" }")
                        }
                    }
                }
            }
            writer.appendLine()

            writer.appendLine("[bundles]")
        }
    }

    companion object {
        private fun determineDependencyParts(
            parts: List<String>, rootGroup: String, bomName: String, targetSection: String
        ): Triple<String?, String?, String?> = when {
            parts[2].isEmpty() -> Triple("", parts[1], parts[3])
            else -> Triple(parts[1], parts[2], parts[3])
        }

        private fun generateVersionKey(group: String, name: String): String {
            if (group.isEmpty()) return name.replace(".", "-")
            val groupLastPart = group.substringAfterLast(".")
            val key = if (name.startsWith("$groupLastPart-")) {
                "$group-${name.substring(groupLastPart.length + 1)}"
            } else "$group-$name"
            return key.replace(".", "-").lowercase()
        }

        private fun generateLibraryKey(group: String, name: String): String? {
            if (group.isEmpty()) return null
            val groupLastPart = group.substringAfterLast(".")
            val key = if (name.startsWith("$groupLastPart-")) {
                "$group-${name.substring(groupLastPart.length + 1)}"
            } else "$group-$name"
            return key.replace(".", "-").lowercase().replace("com-sphereon", "sphereon")
        }

        private fun generatePluginKey(group: String, name: String): String {
            if (group.isEmpty()) return name.replace(".", "-")
            val groupLastPart = group.substringAfterLast(".")
            val key = if (name.startsWith("$groupLastPart-")) {
                "$group-${name.substring(groupLastPart.length + 1)}"
            } else "$group-$name"
            return key.replace(".", "-").lowercase().replace("com-sphereon", "sphereon")
        }

        private fun addVersionSafely(versions: MutableMap<String, String>, key: String, version: String, bomVersion: String? = null) {
            val resolved = if (version == "\${version}" || version == "\$version") {
                bomVersion ?: version
            } else version
            val existing = versions[key]
            if (existing == null || existing == resolved) {
                versions[key] = resolved
            }
        }
    }
}
