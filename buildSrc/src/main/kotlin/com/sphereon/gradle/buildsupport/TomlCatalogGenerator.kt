package com.sphereon.gradle.buildsupport


import org.gradle.api.Project
import java.io.File
import kotlin.reflect.jvm.jvmName

/**
 * Function to generate TOML version catalog files from a BOM
 *
 * @param outputDir The directory where the TOML files will be generated
 * @param targetSection The section to add dependencies to (libraries or plugins)
 * @param includeVersions Whether to include versions in the TOML file
 * @param processedProjects Set of projects that have already been processed to avoid infinite recursion
 */
fun Project.generateTomlFromBom(
    outputDir: File,
    targetSection: String = "libraries",
    includeVersions: Boolean = true,
    processedProjects: MutableSet<String> = mutableSetOf()
) {
    val bomName = name
    val catalogName = "sphereon" + bomName.split("-").joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }

    // Determine the file name based on whether versions are included
    val fileName = if (includeVersions) "$catalogName.versioned.toml" else "$catalogName.toml"
    val outputFile = File(outputDir, fileName)

    logger.lifecycle("Generating TOML catalog for $bomName at ${outputFile.absolutePath} (includeVersions=$includeVersions)")

    // Get the root project group to filter out internal sub-BOMs
    val rootGroup = rootProject.group.toString()
    logger.lifecycle("Root group: $rootGroup")

    val versions = mutableMapOf<String, String>()
    val dependencies = mutableListOf<Triple<String, String, String>>()
    // Keep track of which dependencies are platforms
    val platformDependencies = mutableSetOf<Pair<String, String>>()
    // Keep track of which dependencies are plugins (should go to plugins section)
    val pluginDependencies = mutableSetOf<Pair<String, String>>()
    // Keep track of duplicate dependencies to avoid duplicate keys
    val processedDependencies = mutableSetOf<Pair<String, String>>()

    // Add this project to the processed set to avoid infinite recursion
    processedProjects.add(path)

    val groupStr = group.toString()
    val versionStr = version.toString()

    // Check if this dependency has already been processed
    if (processedDependencies.add(Pair(groupStr, bomName))) {
        dependencies.add(Triple(groupStr, bomName, versionStr))
        // Add the BOM's own version to the versions map
        val bomVersionKey = generateVersionKey(groupStr, bomName)
        addVersionSafely(versions, bomVersionKey, versionStr)
    }

    // Skip directly to parsing the build file since configuration resolution fails with constraints
    val buildFile = file("build.gradle.kts")
    if (buildFile.exists()) {
        val content = buildFile.readText()

        // Parse regular dependencies
        val constraintsPattern = """api\("([^:"]+)(?::([^:"]+))?:([^"]+)"\)""".toRegex()
        constraintsPattern.findAll(content).forEach { match ->
            val (group, name, version) = determineDependencyParts(match.groupValues, rootGroup, bomName, targetSection)
            if (group != null && name != null && version != null) {
                // Check if this dependency has already been processed
                if (processedDependencies.add(Pair(group, name))) {
                    dependencies.add(Triple(group, name, version))
                    val versionKey = generateVersionKey(group, name)
                    addVersionSafely(versions, versionKey, version)
                }
            }
        }

        // Parse project platform dependencies and recursively process them
        val platformProjectPattern = """api\(platform\(project\("([^"]+)"\)\)\)""".toRegex()
        platformProjectPattern.findAll(content).forEach { match ->
            val projectPath = match.groupValues[1]
            // Get the project reference
            val platformProject = project.project(projectPath)
            val platformGroup = platformProject.group.toString()
            val platformName = platformProject.name
            val platformVersion = platformProject.version.toString()

            // Check if this platform dependency has already been processed
            if (processedDependencies.add(Pair(platformGroup, platformName))) {
                dependencies.add(Triple(platformGroup, platformName, platformVersion))
                val versionKey = generateVersionKey(platformGroup, platformName)
                addVersionSafely(versions, versionKey, platformVersion)

                // Mark this as a platform dependency
                platformDependencies.add(Pair(platformGroup, platformName))

                // Check if this is a plugin BOM
                val isPluginBom = platformName.contains("gradle-plugin")
                if (isPluginBom) {
                    // Mark this as a plugin dependency
                    pluginDependencies.add(Pair(platformGroup, platformName))
                }
            }

            logger.lifecycle("Added project platform dependency: $platformGroup:$platformName:$platformVersion")

            // Recursively process the platform project if it hasn't been processed yet
            if (!processedProjects.contains(platformProject.path)) {
                logger.lifecycle("Recursively processing platform project: ${platformProject.path}")

                // Create temporary collections to hold the platform's dependencies
                val platformVersions = mutableMapOf<String, String>()
                val platformDeps = mutableListOf<Triple<String, String, String>>()
                val platformPlatformDeps = mutableSetOf<Pair<String, String>>()

                // Process the platform project's build file
                val platformBuildFile = platformProject.file("build.gradle.kts")
                if (platformBuildFile.exists()) {
                    val platformContent = platformBuildFile.readText()

                    // Parse regular dependencies from the platform
                    constraintsPattern.findAll(platformContent).forEach { platformMatch ->
                        val (pGroup, pName, pVersion) = determineDependencyParts(
                            platformMatch.groupValues, rootGroup, platformName, targetSection
                        )
                        if (pGroup != null && pName != null && pVersion != null) {
                            // Check if this platform dependency has already been processed
                            if (!processedDependencies.contains(Pair(pGroup, pName))) {
                                platformDeps.add(Triple(pGroup, pName, pVersion))
                                addVersionSafely(platformVersions, generateVersionKey(pGroup, pName), pVersion)

                                // Check if this platform is a plugin BOM
                                val isPluginBom = platformName.contains("gradle-plugin")
                                if (isPluginBom) {
                                    // Mark dependencies from plugin BOMs as plugin dependencies
                                    // We'll add these to the main pluginDependencies set later
                                    platformPlatformDeps.add(Pair(pGroup, pName))
                                }
                            }
                        }
                    }

                    // Add all platform dependencies to our main collections
                    platformDeps.forEach { (group, name, version) ->
                        if (processedDependencies.add(Pair(group, name))) {
                            dependencies.add(Triple(group, name, version))
                        }
                    }

                    // Add all platform versions to our main versions map
                    platformVersions.forEach { (key, version) ->
                        addVersionSafely(versions, key, version)
                    }

                    platformDependencies.addAll(platformPlatformDeps)

                    // Check if this platform is a plugin BOM
                    val isPluginBom = platformName.contains("gradle-plugin")
                    if (isPluginBom) {
                        // Add all dependencies from this plugin BOM to the plugin dependencies set
                        pluginDependencies.addAll(platformPlatformDeps)
                    }

                    // Recursively process nested platform dependencies
                    val nestedPlatformPattern = """api\(platform\(project\("([^"]+)"\)\)\)""".toRegex()
                    nestedPlatformPattern.findAll(platformContent).forEach { nestedMatch ->
                        val nestedProjectPath = nestedMatch.groupValues[1]
                        val nestedProject = project.project(nestedProjectPath)

                        if (!processedProjects.contains(nestedProject.path)) {
                            processedProjects.add(nestedProject.path)

                            val nestedGroup = nestedProject.group.toString()
                            val nestedName = nestedProject.name
                            val nestedVersion = nestedProject.version.toString()

                            // Check if this nested platform dependency has already been processed
                            if (processedDependencies.add(Pair(nestedGroup, nestedName))) {
                                dependencies.add(Triple(nestedGroup, nestedName, nestedVersion))
                                addVersionSafely(versions, generateVersionKey(nestedGroup, nestedName), nestedVersion)
                                platformDependencies.add(Pair(nestedGroup, nestedName))
                            }

                            // Recursively process the nested platform's build file
                            val nestedBuildFile = nestedProject.file("build.gradle.kts")
                            if (nestedBuildFile.exists()) {
                                val nestedContent = nestedBuildFile.readText()

                                // Parse regular dependencies from the nested platform
                                constraintsPattern.findAll(nestedContent).forEach { nestedDepMatch ->
                                    val (nGroup, nName, nVersion) = determineDependencyParts(
                                        nestedDepMatch.groupValues, rootGroup, nestedName, targetSection
                                    )
                                    if (nGroup != null && nName != null && nVersion != null) {
                                        // Check if this nested dependency has already been processed
                                        if (processedDependencies.add(Pair(nGroup, nName))) {
                                            dependencies.add(Triple(nGroup, nName, nVersion))
                                            addVersionSafely(versions, generateVersionKey(nGroup, nName), nVersion)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Parse external platform dependencies
        val platformExternalPattern = """api\(platform\("([^:"]+)(?::([^:"]+))?:([^"]+)"\)\)""".toRegex()
        platformExternalPattern.findAll(content).forEach { match ->
            val (group, name, version) = determineDependencyParts(match.groupValues, rootGroup, bomName, targetSection)
            if (group != null && name != null && version != null) {
                // Check if this external platform dependency has already been processed
                if (processedDependencies.add(Pair(group, name))) {
                    dependencies.add(Triple(group, name, version))
                    val versionKey = generateVersionKey(group, name)
                    addVersionSafely(versions, versionKey, version)

                    // Mark this as a platform dependency
                    platformDependencies.add(Pair(group, name))

                    logger.lifecycle("Added external platform dependency: $group:$name:$version")
                }
            }
        }
    }

    outputFile.writer().use { writer ->
        // Check if we have any platform dependencies
        val hasPlatformDeps = dependencies.any { (group, name, _) ->
            name.endsWith("-bom") || platformDependencies.contains(Pair(group, name))
        }

        // Include versions section if includeVersions is true or if we have platform dependencies
        if (includeVersions || hasPlatformDeps) {
            writer.appendLine("[versions]")
            versions.forEach { (key, version) ->
                // For non-versioned catalogs, only include versions for platform dependencies
                val isPlatformVersion = if (!includeVersions) {
                    // Extract group and name from the version key
                    val parts = key.split("-", limit = 2)
                    val group = parts[0]
                    val name = if (parts.size > 1) parts[1] else ""

                    // Check if this is a platform dependency
                    name.endsWith("-bom") || platformDependencies.contains(Pair(group, name))
                } else {
                    true // Include all versions for versioned catalogs
                }

                if (isPlatformVersion) {
                    writer.appendLine("$key = \"$version\"")
                }
            }
            writer.appendLine()
        }

        // Write libraries section
        // Always write libraries section, regardless of targetSection
        writer.appendLine("[libraries]")
        // Track used keys to avoid duplicates
        val usedLibraryKeys = mutableSetOf<String>()

        dependencies.forEach { (group, name, version) ->
            // Skip dependencies that should go to the plugins section
            if (!pluginDependencies.contains(Pair(group, name))) {
                val key = generateLibraryKey(group, name)
                // Only process this dependency if we haven't seen this key before
                if (usedLibraryKeys.add(key)) {
                    // Check if this is a platform dependency (either by name convention or by being added via platform())
                    val isPlatform = name.endsWith("-bom") || platformDependencies.contains(Pair(group, name))

                    if (includeVersions || isPlatform) {
                        // Use version reference if versions are included or if it's a platform dependency
                        val versionKey = generateVersionKey(group, name)
                        writer.appendLine("$key = { module = \"$group:$name\", version.ref = \"$versionKey\" }")
                    } else {
                        // Use direct version if versions are not included (for use with BOM)
                        writer.appendLine("$key = { module = \"$group:$name\" }")
                    }
                }
            }
        }
        writer.appendLine()

        // Write plugins section
        // Always write plugins section, regardless of targetSection
        writer.appendLine("[plugins]")
        // Track used keys to avoid duplicates
        val usedPluginKeys = mutableSetOf<String>()

        dependencies.forEach { (group, name, version) ->
            // Skip the BOM itself and only include plugin dependencies or if targetSection is "plugins"
            if (name != bomName && (pluginDependencies.contains(Pair(group, name)) || targetSection == "plugins")) {
                val key = generatePluginKey(group, name)
                // Only process this plugin if we haven't seen this key before
                if (usedPluginKeys.add(key)) {
                    val pluginId = if (name.contains(".gradle.plugin")) {
                        name.substringBefore(".gradle.plugin")
                    } else {
                        "$group.$name"
                    }

                    // Check if this is a platform dependency (either by name convention or by being added via platform())
                    val isPlatform = name.endsWith("-bom") || platformDependencies.contains(Pair(group, name))

                    // For non-versioned TOML, only include version references for actual platform/BOM dependencies
                    // For plugins, we only want version references if it's a plugin BOM (like gradle-plugin-bom)
                    val shouldIncludeVersionRef = includeVersions || (isPlatform && name.endsWith("-bom"))

                    if (shouldIncludeVersionRef) {
                        // Use version reference if versions are included or if it's a platform/BOM dependency
                        val versionKey = generateVersionKey(group, name)
                        writer.appendLine("$key = { id = \"$pluginId\", version.ref = \"$versionKey\" }")
                    } else {
                        // Use direct version if versions are not included (for use with BOM)
                        writer.appendLine("$key = { id = \"$pluginId\" }")
                    }
                }
            }
        }
        writer.appendLine()

        // Write empty bundles section
        writer.appendLine("[bundles]")
    }

    project.pluginManager.withPlugin(TomlCatalogPlugin::class.jvmName) {}

    logger.lifecycle("Generated TOML catalog for $bomName at ${outputFile.absolutePath} (includeVersions=$includeVersions)")
}

private fun determineDependencyParts(
    parts: List<String>,
    rootGroup: String,
    bomName: String,
    targetSection: String
): Triple<String?, String?, String?> {
    return when {
        parts[2].isEmpty() -> {
            val groupName = parts[1].substringBeforeLast(".")
            Triple(groupName, parts[1].substringAfterLast("."), parts[3])
        }

        else -> Triple(parts[1], parts[2], parts[3])
    }
}

/**
 * Generates a key for a version, removing redundancy if the group ends with the same name as the first part of the artifact.
 * For example, "dev.whyoleg.cryptography-cryptography-core" becomes "dev.whyoleg.cryptography-core".
 * Also replaces dots with hyphens as dots are not valid in TOML keys.
 */
private fun generateVersionKey(group: String, name: String): String {
    val groupLastPart = group.substringAfterLast(".")
    val key = if (name.startsWith("$groupLastPart-")) {
        "$group-${name.substring(groupLastPart.length + 1)}"
    } else {
        "$group-$name"
    }
    // Replace dots with hyphens as dots are not valid in TOML keys
    return key.replace(".", "-")
}

/**
 * Generates a key for a library, removing redundancy if the group ends with the same name as the first part of the artifact.
 * For example, "dev.whyoleg.cryptography-cryptography-core" becomes "dev.whyoleg.cryptography-core".
 * Also replaces dots with hyphens as dots are not valid in TOML keys.
 */
private fun generateLibraryKey(group: String, name: String): String {
    val groupLastPart = group.substringAfterLast(".")
    val key = if (name.startsWith("$groupLastPart-")) {
        "$group-${name.substring(groupLastPart.length + 1)}"
    } else {
        "$group-$name"
    }
    // Replace dots with hyphens as dots are not valid in TOML keys
    return key.replace(".", "-").replace("com-sphereon", "sphereon")
}

/**
 * Generates a key for a plugin, removing redundancy if the group ends with the same name as the first part of the artifact.
 * For example, "dev.whyoleg.cryptography-cryptography-core" becomes "dev.whyoleg.cryptography-core".
 * Also replaces dots with hyphens as dots are not valid in TOML keys.
 */
private fun generatePluginKey(group: String, name: String): String {
    val groupLastPart = group.substringAfterLast(".")
    val key = if (name.startsWith("$groupLastPart-")) {
        "$group-${name.substring(groupLastPart.length + 1)}"
    } else {
        "$group-$name"
    }
    // Replace dots with hyphens as dots are not valid in TOML keys
    return key.replace(".", "-").replace("com-sphereon", "sphereon")
}

/**
 * Safely adds a version to the versions map, ensuring no duplicate keys with different values.
 * If a key already exists with a different value, a warning is logged and the original value is kept.
 */
private fun Project.addVersionSafely(versions: MutableMap<String, String>, key: String, version: String) {
    // Replace ${version} or $version with the actual project version
    val resolvedVersion = if (version == "\${version}" || version == "\$version") {
        project.version.toString()
    } else {
        version
    }

    val existingVersion = versions[key]
    if (existingVersion != null && existingVersion != resolvedVersion) {
        logger.warn("Version conflict for key '$key': existing='$existingVersion', new='$resolvedVersion'. Keeping existing version.")
    } else {
        versions[key] = resolvedVersion
    }
}