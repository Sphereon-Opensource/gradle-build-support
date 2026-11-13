package com.sphereon.gradle.plugin

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GbsSupplyChainConventionsTest {
    @Test
    fun shippedRuntimeClasspathsAreLocked() {
        assertTrue(GbsDependencyLocking.isLocked("jvmRuntimeClasspath"))
        assertTrue(GbsDependencyLocking.isLocked("runtimeClasspath"))
        assertTrue(GbsDependencyLocking.isLocked("wasmJsRuntimeClasspath"))
    }

    @Test
    fun testCompileAndToolingClasspathsAreNotLocked() {
        assertFalse(GbsDependencyLocking.isLocked("jvmTestRuntimeClasspath"))
        assertFalse(GbsDependencyLocking.isLocked("testRuntimeClasspath"))
        assertFalse(GbsDependencyLocking.isLocked("jvmCompileClasspath"))
        assertFalse(GbsDependencyLocking.isLocked("kotlinCompilerClasspath"))
    }

    @Test
    fun sphereonModulesStayOutOfTheLockState() {
        assertTrue("com.sphereon*:*" in GbsDependencyLocking.IGNORED)
    }

    @Test
    fun aMultiplatformModuleAttachesItsSbomToTheRootPublicationOnly() {
        assertTrue(GbsModuleSbom.attachesTo("kotlinMultiplatform", multiplatform = true))
        assertFalse(GbsModuleSbom.attachesTo("jvm", multiplatform = true))
        assertFalse(GbsModuleSbom.attachesTo("js", multiplatform = true))
    }

    @Test
    fun anyOtherModuleAttachesItsSbomToEachPublication() {
        assertTrue(GbsModuleSbom.attachesTo("maven", multiplatform = false))
        assertTrue(GbsModuleSbom.attachesTo("pluginMaven", multiplatform = false))
    }

    @Test
    fun theSbomCoversProductionRuntimeClasspathsOnly() {
        val include = Regex(GbsModuleSbom.INCLUDE)
        val skip = Regex(GbsModuleSbom.SKIP)
        assertTrue(include.matches("jvmRuntimeClasspath") && !skip.matches("jvmRuntimeClasspath"))
        assertTrue(skip.matches("jvmTestRuntimeClasspath"))
        assertFalse(include.matches("jvmCompileClasspath"))
    }
}
