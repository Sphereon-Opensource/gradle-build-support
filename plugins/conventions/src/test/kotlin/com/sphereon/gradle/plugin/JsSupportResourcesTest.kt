package com.sphereon.gradle.plugin

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class JsSupportResourcesTest {
    @TempDir
    lateinit var temporary: File

    @Test
    fun resolvesBothPublishedResourcesWithoutCheckoutAndReusesBytes() {
        val cache = File(temporary, "build/support")
        for (name in listOf("esm-require-shim.cjs", "webpack-node-scheme.js")) {
            val resource = bundledJsSupportFile(cache, name)
            val expected = javaClass.getResourceAsStream("/com/sphereon/gradle/plugin/js/$name")!!.use { it.readBytes() }
            assertTrue(resource.readBytes().contentEquals(expected))
            val stamp = resource.lastModified()
            assertEquals(resource, bundledJsSupportFile(cache, name))
            assertEquals(stamp, resource.lastModified())
        }
        assertTrue(!File(temporary, "gradle-build-support").exists())
    }

    @Test
    fun refusesUnknownResourceAndAlteredCachedSupport() {
        val cache = File(temporary, "support")
        assertFails { bundledJsSupportFile(cache, "../escaped.cjs") }
        val resource = bundledJsSupportFile(cache, "esm-require-shim.cjs")
        resource.writeText("changed")
        assertFails { bundledJsSupportFile(cache, "esm-require-shim.cjs") }
    }
}
