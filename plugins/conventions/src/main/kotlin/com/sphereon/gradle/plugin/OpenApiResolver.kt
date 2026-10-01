package com.sphereon.gradle.plugin

import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File
import java.util.Properties

/** Explicit source input for infra/release builds, otherwise a pinned Maven spec bundle. */
fun Project.openapiCheckout(): File {
    val root = rootProject
    val explicit = providers.gradleProperty("openapiRepo").orNull
        ?: providers.environmentVariable("VDX_OPENAPI_REPO").orNull
    if (!explicit.isNullOrBlank()) {
        val directory = root.file(explicit).canonicalFile
        if (!File(directory, "common-components.yml").isFile || !File(directory, "manifest-catalog.json").isFile) {
            throw GradleException("Explicit OpenAPI input is incomplete: $directory")
        }
        return directory
    }
    val cached = root.extensions.extraProperties
    val cacheKey = "sphereonResolvedOpenApiInput"
    if (cached.has(cacheKey)) return cached.get(cacheKey) as File
    val pinFiles = listOf(File(root.rootDir, "openapi-input.properties"),
        File(root.rootDir.parentFile, "openapi-input.properties"))
    val pin = pinFiles.firstOrNull { it.isFile }
    val version = providers.gradleProperty("openapiSpecsVersion").orNull ?: pin?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }.getProperty("openapiSpecsVersion")
    } ?: throw GradleException("OpenAPI Maven input is not pinned; provide openapi-input.properties or -PopenapiSpecsVersion")
    if (!version.matches(Regex("[0-9a-f]{32}"))) throw GradleException("Invalid pinned OpenAPI specs version: $version")
    val dependency = root.dependencies.create("com.sphereon.openapi:openapi-specs:$version@jar")
    val configuration = root.configurations.detachedConfiguration(dependency).apply { isTransitive = false }
    val archive = configuration.singleFile
    val result = extractOpenApiArchive(archive, root.layout.buildDirectory.dir("openapi-inputs").get().asFile, version)
    cached.set(cacheKey, result)
    return result
}

/** Resolve a spec without changing its sibling-reference layout. */
fun Project.openapiSpec(repoRelativePath: String): File {
    validateOpenApiPath(repoRelativePath)
    val checkout = openapiCheckout()
    val spec = File(checkout, repoRelativePath)
    if (!spec.isFile) throw GradleException("OpenAPI spec not found: $repoRelativePath under $checkout")
    return spec
}
