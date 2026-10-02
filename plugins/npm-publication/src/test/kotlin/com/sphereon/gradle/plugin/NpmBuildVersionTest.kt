package com.sphereon.gradle.plugin

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class NpmBuildVersionTest {
    @TempDir lateinit var directory: Path

    private fun inputs(checkout: String, code: String = "export const value = 1") =
        directory.resolve(checkout).resolve("productionLibrary").toFile().apply {
            mkdirs()
            resolve("module.mjs").writeText(code)
            resolve("module.d.mts").writeText("export const value: number")
        }

    @Test fun unchangedBuildHasSameVersionInAnotherCheckout() {
        val first = NpmBuildVersion.identity("0.26.0-SNAPSHOT", "@sphereon/idk-a", listOf(inputs("local")))
        val second = NpmBuildVersion.identity("0.26.0-SNAPSHOT", "@sphereon/idk-a", listOf(inputs("ci")))
        assertEquals(first, second)
        assertEquals(NpmBuildVersion.version("0.26.0-SNAPSHOT", first),
            NpmBuildVersion.version("0.26.0-SNAPSHOT", second))
    }

    @Test fun changedCodeChangesOnlyAffectedPackage() {
        val a = inputs("a"); val b = inputs("b")
        val firstA = NpmBuildVersion.identity("0.26.0-SNAPSHOT", "a", listOf(a))
        val firstB = NpmBuildVersion.identity("0.26.0-SNAPSHOT", "b", listOf(b))
        a.resolve("module.mjs").writeText("export const value = 2")
        assertNotEquals(firstA, NpmBuildVersion.identity("0.26.0-SNAPSHOT", "a", listOf(a)))
        assertEquals(firstB, NpmBuildVersion.identity("0.26.0-SNAPSHOT", "b", listOf(b)))
    }

    @Test fun sameVersionDependencyAndTypeChangesChangeBuildVersion() {
        val files = inputs("module")
        files.resolve("dependency.mjs").writeText("export const dependency = 1")
        val first = NpmBuildVersion.identity("0.26.0-SNAPSHOT", "a", listOf(files))
        files.resolve("dependency.mjs").writeText("export const dependency = 2")
        val dependency = NpmBuildVersion.identity("0.26.0-SNAPSHOT", "a", listOf(files))
        assertNotEquals(first, dependency)
        files.resolve("module.d.mts").writeText("export const value: string")
        assertNotEquals(dependency, NpmBuildVersion.identity("0.26.0-SNAPSHOT", "a", listOf(files)))
    }

    @Test fun unrelatedGitAndTestFilesDoNotChangeProductionBuildVersion() {
        val files = inputs("module")
        val first = NpmBuildVersion.identity("0.26.0-SNAPSHOT", "a", listOf(files))
        Files.writeString(directory.resolve("HEAD"), "another commit")
        Files.writeString(directory.resolve("ExampleTest.kt"), "changed test")
        assertEquals(first, NpmBuildVersion.identity("0.26.0-SNAPSHOT", "a", listOf(files)))
    }

    @Test fun deletingResourceAndRenamingFileChangeBuildVersion() {
        val files = inputs("module")
        files.resolve("resource.json").writeText("{}")
        val first = NpmBuildVersion.identity("0.26.0-SNAPSHOT", "a", listOf(files))
        assertTrue(files.resolve("resource.json").delete())
        val deleted = NpmBuildVersion.identity("0.26.0-SNAPSHOT", "a", listOf(files))
        assertNotEquals(first, deleted)
        assertTrue(files.resolve("module.mjs").renameTo(files.resolve("renamed.mjs")))
        assertNotEquals(deleted, NpmBuildVersion.identity("0.26.0-SNAPSHOT", "a", listOf(files)))
    }

    @Test fun inputOrderDoesNotChangeBuildVersion() {
        val code = inputs("module")
        val metadata = directory.resolve("package.json").toFile().apply { writeText("{}") }
        assertEquals(NpmBuildVersion.identity("0.26.0-SNAPSHOT", "a", listOf(code, metadata)),
            NpmBuildVersion.identity("0.26.0-SNAPSHOT", "a", listOf(metadata, code)))
    }

    @Test fun releaseVersionRemainsDeclaredVersion() {
        assertEquals("0.26.0", NpmBuildVersion.version("0.26.0", ""))
        assertEquals("0.26.0-rc6", NpmBuildVersion.version("0.26.0-rc6", ""))
    }

    @Test fun pluginRepackagingDoesNotChangeIdentityButPluginCodeDoes() {
        val production = inputs("module")
        fun plugin(name: String, time: Long, code: String) = directory.resolve(name).toFile().apply {
            ZipOutputStream(outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("Plugin.class").apply { this.time = time })
                zip.write(code.toByteArray()); zip.closeEntry()
            }
        }
        val original = plugin("plugin.jar", 1000000000000L, "implementation one")
        val repackaged = plugin("instrumented-plugin.jar", 1500000000000L, "implementation one")
        val changed = plugin("updated-plugin.jar", 1500000000000L, "implementation two")
        val first = NpmBuildVersion.identity("0.26.0-SNAPSHOT", "a", listOf(production), listOf(original))
        assertEquals(first, NpmBuildVersion.identity("0.26.0-SNAPSHOT", "a", listOf(production), listOf(repackaged)))
        assertNotEquals(first, NpmBuildVersion.identity("0.26.0-SNAPSHOT", "a", listOf(production), listOf(changed)))
    }

    @Test fun snapshotVersionUsesFullBuildIdentityAndRejectsGitHash() {
        val id = "0".repeat(64)
        assertEquals("0.26.0-SNAPSHOT.build.b$id", NpmBuildVersion.version("0.26.0-SNAPSHOT", id))
        assertFailsWith<GradleException> { NpmBuildVersion.version("0.26.0-SNAPSHOT", "abcdef1") }
        assertFailsWith<GradleException> { NpmBuildVersion.identity("0.26.0-SNAPSHOT", "a", emptyList()) }
    }
}
