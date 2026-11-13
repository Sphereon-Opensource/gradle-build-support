package com.sphereon.gradle.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GbsDependencyPolicyTest {
    private val approved = mapOf(
        "io.ktor:ktor-server-core" to "3.3.1",
        "org.postgresql:postgresql" to "42.7.12",
        "org.junit.jupiter:junit-jupiter" to null,
    )

    @Test
    fun aCatalogModuleWithoutLocalVersionComplies() {
        assertNull(GbsDependencyPolicy.evaluate("io.ktor", "ktor-server-core", null, approved))
        assertNull(GbsDependencyPolicy.evaluate("io.ktor", "ktor-server-core", "3.3.1", approved))
    }

    @Test
    fun aModuleOutsideGbsIsReported() {
        val violation = assertNotNull(GbsDependencyPolicy.evaluate("org.eclipse.rdf4j", "rdf4j-rio-turtle", "5.1.0", approved))
        assertTrue(violation.startsWith("org.eclipse.rdf4j:rdf4j-rio-turtle is not listed"))
    }

    @Test
    fun aDifferentLocalVersionIsReported() {
        assertEquals(
            "org.postgresql:postgresql:42.7.11 differs from gradle-build-support (42.7.12); drop the local version and use the sphereonlib catalog",
            GbsDependencyPolicy.evaluate("org.postgresql", "postgresql", "42.7.11", approved),
        )
    }

    @Test
    fun ourOwnModulesAndToolchainImplicitLibrariesAreExempt() {
        assertNull(GbsDependencyPolicy.evaluate("com.sphereon.idk", "lib-core-api-public", "0.26.0-SNAPSHOT", approved))
        assertNull(GbsDependencyPolicy.evaluate("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.20", approved))
        assertNull(GbsDependencyPolicy.evaluate(null, "local-file", null, approved))
    }

    @Test
    fun aResolvedVersionOtherThanThePinIsReported() {
        assertEquals(
            "org.postgresql:postgresql resolved to 42.7.11 but gradle-build-support pins 42.7.12",
            GbsDependencyPolicy.evaluateResolved("org.postgresql", "postgresql", "42.7.11", approved),
        )
        assertNull(GbsDependencyPolicy.evaluateResolved("org.postgresql", "postgresql", "42.7.12", approved))
        assertNull(GbsDependencyPolicy.evaluateResolved("org.example", "transitive-only", "1.0", approved))
    }

    @Test
    fun aCatalogEntryWithoutVersionAcceptsAnyBomAlignedDeclaration() {
        assertNull(GbsDependencyPolicy.evaluate("org.junit.jupiter", "junit-jupiter", "5.12.1", approved))
    }
}
