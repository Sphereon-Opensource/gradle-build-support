package com.sphereon.gradle.plugin

import groovy.json.JsonOutput
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

class OpenApiResolverArchiveTest {
    @TempDir
    lateinit var temporary: File

    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun bundle(entries: Map<String, String>): Pair<File, String> {
        val files = entries.toSortedMap().map { (path, text) ->
            mapOf("path" to path, "sha256" to hash(text.toByteArray()), "size" to text.toByteArray().size)
        }
        val recipe = "sphereon-openapi-specs-v1"
        val input = recipe + "\n" + files.joinToString("") { "${it["path"]}\u0000${it["sha256"]}\u0000${it["size"]}\n" }
        val sourceId = hash(input.toByteArray())
        val version = sourceId.take(32)
        val manifest = mapOf("schemaVersion" to 1, "recipe" to recipe, "version" to version,
            "sourceInputId" to sourceId, "files" to files)
        val jar = File(temporary, "specs-${System.nanoTime()}.jar")
        ZipOutputStream(jar.outputStream()).use { zip ->
            (entries + ("META-INF/sphereon-openapi.json" to JsonOutput.toJson(manifest))).forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return jar to version
    }

    @Test
    fun archiveKeepsSiblingRefsAndReusesVerifiedExtractedBytes() {
        val (jar, version) = bundle(mapOf("common-components.yml" to "components: {}\n",
            "manifest-catalog.json" to "{}\n", "example-openapi.yml" to "components:\n  ref: ./common-components.yml\n"))
        val cache = File(temporary, "cache")
        val directory = extractOpenApiArchive(jar, cache, version)
        val common = File(directory, "common-components.yml")
        assertEquals("components: {}\n", common.readText())
        val stamp = common.lastModified()
        assertEquals(directory, extractOpenApiArchive(jar, cache, version))
        assertEquals(stamp, common.lastModified())
    }

    @Test
    fun archiveTraversalAndWrongCoordinateFailWithoutWritingOutsideCache() {
        val (jar, version) = bundle(mapOf("common-components.yml" to "components: {}\n",
            "manifest-catalog.json" to "{}\n", "../escaped.yml" to "invalid"))
        assertFails { extractOpenApiArchive(jar, File(temporary, "cache"), version) }
        assertFalse(File(temporary, "escaped.yml").exists())
        val (valid, _) = bundle(mapOf("common-components.yml" to "components: {}\n", "manifest-catalog.json" to "{}\n"))
        assertFails { extractOpenApiArchive(valid, File(temporary, "other-cache"), "wrong-version") }
    }

    @Test
    fun modifiedExtractedSpecsAreRejected() {
        val (jar, version) = bundle(mapOf("common-components.yml" to "components: {}\n", "manifest-catalog.json" to "{}\n"))
        val cache = File(temporary, "cache")
        val directory = extractOpenApiArchive(jar, cache, version)
        File(directory, "common-components.yml").writeText("changed")
        assertFails { extractOpenApiArchive(jar, cache, version) }
    }

    @Test
    fun standaloneProductResolvesPinnedMavenBundleWithoutSourceCheckout() {
        val (jar, version) = bundle(mapOf("common-components.yml" to "components: {}\n", "manifest-catalog.json" to "{}\n"))
        val repository = File(temporary, "maven")
        val artifact = File(repository, "com/sphereon/openapi/openapi-specs/$version/openapi-specs-$version.jar")
        artifact.parentFile.mkdirs()
        jar.copyTo(artifact)
        val product = File(temporary, "product").apply { mkdirs() }
        File(product, "openapi-input.properties").writeText("openapiSpecsVersion=$version\n")
        val project = ProjectBuilder.builder().withProjectDir(product).build()
        project.repositories.maven(org.gradle.api.Action<org.gradle.api.artifacts.repositories.MavenArtifactRepository> {
            setUrl(repository.toURI())
        })
        val result = project.openapiCheckout()
        assertEquals("components: {}\n", File(result, "common-components.yml").readText())
        assertEquals(result, project.openapiCheckout())
        assertFalse(File(product, "openapi").exists())
    }

    @Test
    fun unpinnedStandaloneProductFailsRatherThanSelectingNearbyCheckout() {
        val product = File(temporary, "unpinned").apply { mkdirs() }
        val checkout = File(product, "openapi").apply { mkdirs() }
        File(checkout, "common-components.yml").writeText("components: {}")
        val project = ProjectBuilder.builder().withProjectDir(product).build()
        assertFails { project.openapiCheckout() }
    }
}
