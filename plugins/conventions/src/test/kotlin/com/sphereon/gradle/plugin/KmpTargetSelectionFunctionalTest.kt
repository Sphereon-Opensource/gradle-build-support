/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.gradle.plugin

import java.io.File
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir

class KmpTargetSelectionFunctionalTest {
    @TempDir
    lateinit var projectDirectory: Path

    @Test
    fun jvmOnlyConfiguresNoJsWasmOrAndroidTasks() {
        writeFixture()

        val output = listTasks("lib", "jvm", environmentWithoutAndroidSdk())

        assertContains(output, "compileKotlinJvm")
        assertFalse(output.contains("compileKotlinJs"), "JS compile task present for kmp.targets=jvm:\n$output")
        assertFalse(output.contains("compileKotlinWasmJs"), "wasmJs compile task present for kmp.targets=jvm:\n$output")
        assertTrue(androidTaskNames(output).isEmpty(), "Android tasks present for kmp.targets=jvm:\n$output")
    }

    @Test
    fun wasmJsRequestAddsTheWasmJsCompileTask() {
        writeFixture()

        val output = listTasks("lib", "jvm,wasmJs", environmentWithoutAndroidSdk())

        assertContains(output, "compileKotlinWasmJs")
        assertContains(output, "compileKotlinJvm")
    }

    @Test
    fun iosRequestOnNonMacHostIsSkippedWithOneLifecycleLine() {
        assumeFalse(isMacOsHost(), "iOS is enabled on macOS hosts")
        writeFixture()

        val output = listTasks("lib", "jvm,ios", environmentWithoutAndroidSdk())

        val skipLine = "iOS targets skipped: they need a macOS host"
        assertEquals(1, output.lines().count { line -> line.contains(skipLine) }, "Skip line count in:\n$output")
        assertFalse(output.contains("Ios", ignoreCase = false), "iOS tasks present on a non-macOS host:\n$output")
    }

    @Test
    fun androidHelperIsInertWithoutAndroidRequest() {
        writeFixture(withAndroidReport = false)

        val output = listTasks("androidlib", "jvm", environmentWithoutAndroidSdk())

        assertContains(output, "ANDROID_PLUGIN_APPLIED=false")
        assertTrue(androidTaskNames(output).isEmpty(), "Android tasks present for kmp.targets=jvm:\n$output")
    }

    @Test
    fun androidRequestDeclaresTheConfiguredAndroidTarget() {
        val androidHome = System.getenv("ANDROID_HOME")
        assumeTrue(androidHome != null && Path.of(androidHome).toFile().isDirectory, "ANDROID_HOME is not set to an SDK directory on this host")
        writeFixture(withAndroidReport = true)

        val output = listTasks("androidlib", "jvm,android", System.getenv(), extraClasspath = agpClasspath())

        assertContains(output, "ANDROID_PLUGIN_APPLIED=true")
        assertContains(output, "ANDROID_TARGET_COUNT=1")
        assertContains(output, "ANDROID_TARGET namespace=com.sphereon.fixture.androidlib compileSdk=36 minSdk=$ANDROID_MIN_SDK")
    }

    @Test
    fun requestedKmpTargetsMapsAliasesToCanonicalTokens() {
        val previous = System.getProperty("kmp.targets")
        try {
            System.setProperty("kmp.targets", "WASM, LinuxX64")
            assertEquals(setOf("wasmjs", "native"), requestedKmpTargets())
        } finally {
            if (previous == null) System.clearProperty("kmp.targets") else System.setProperty("kmp.targets", previous)
        }
    }

    @Test
    fun kmpTargetEnabledCanonicalisesItsArgument() {
        withSystemProperty("kmp.targets", "wasm,linuxx64") {
            assertTrue(kmpTargetEnabled("wasmJs"))
            assertTrue(kmpTargetEnabled(" WASM "))
            assertTrue(kmpTargetEnabled("linuxX64"))
            assertFalse(kmpTargetEnabled("js"))
        }
    }

    @Test
    fun macOsHostIsAnOsNameStartingWithMac() {
        withSystemProperty("os.name", "Mac OS X") { assertTrue(isMacOsHost()) }
        withSystemProperty("os.name", "Linux") { assertFalse(isMacOsHost()) }
        withSystemProperty("os.name", "Linux-mac80211") { assertFalse(isMacOsHost()) }
    }

    private fun withSystemProperty(name: String, value: String, block: () -> Unit) {
        val previous = System.getProperty(name)
        try {
            System.setProperty(name, value)
            block()
        } finally {
            if (previous == null) System.clearProperty(name) else System.setProperty(name, previous)
        }
    }

    private fun environmentWithoutAndroidSdk(): Map<String, String> =
        System.getenv().filterKeys { key -> key != "ANDROID_HOME" && key != "ANDROID_SDK_ROOT" }

    private fun listTasks(
        module: String,
        kmpTargets: String,
        environment: Map<String, String>,
        extraClasspath: List<File> = emptyList(),
    ): String =
        GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withPluginClasspath(pluginUnderTestClasspath() + extraClasspath)
            .withEnvironment(environment)
            .withArguments(":$module:tasks", "--all", "-Dkmp.targets=$kmpTargets", "--stacktrace")
            .forwardOutput()
            .build()
            .output

    /** Names of listed tasks that belong to the Android target, from the `name - description` rows of `tasks --all`. */
    private fun androidTaskNames(output: String): List<String> =
        output.lines()
            .filter { line -> line.contains(" - ") }
            .map { line -> line.substringBefore(" - ").trim() }
            .filter { name -> name.contains("android", ignoreCase = true) }

    private fun writeFixture(withAndroidReport: Boolean = false) {
        projectDirectory.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    google()
                    mavenCentral()
                }
                versionCatalogs {
                    create("sphereonlib") {
                        library("org.jetbrains.kotlin.stdlib", "org.jetbrains.kotlin:kotlin-stdlib:2.4.20")
                        library("org.jetbrains.kotlinx.datetime", "org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                        library("org.jetbrains.kotlinx.serialization.json", "org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                        library("org.jetbrains.kotlinx.coroutines.core", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                    }
                }
            }
            rootProject.name = "kmp-target-fixture"
            include(":lib", ":androidlib")
            """.trimIndent() + "\n",
        )
        // Conventions are applied to the subprojects only, so the root needs no license file. AGP is
        // compileOnly in the plugin under test and is not applied here: the module under test applies it
        // only when Android is requested, and only the Android case adds AGP to the plugin classpath.
        projectDirectory.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform") apply false
                id("com.sphereon.gradle.plugin.conventions") apply false
            }
            subprojects {
                apply(plugin = "com.sphereon.gradle.plugin.conventions")
            }
            """.trimIndent() + "\n",
        )
        writeModule(
            "lib",
            """
            import com.sphereon.gradle.plugin.configureStandardTargets

            plugins {
                id("org.jetbrains.kotlin.multiplatform")
            }
            kotlin {
                configureStandardTargets()
            }
            """.trimIndent(),
        )
        val androidReport = if (withAndroidReport) {
            """
            afterEvaluate {
                val androidTargets = kotlin.targets.withType(com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget::class.java)
                println("ANDROID_TARGET_COUNT=" + androidTargets.size)
                androidTargets.forEach { androidTarget ->
                    println("ANDROID_TARGET namespace=" + androidTarget.namespace + " compileSdk=" + androidTarget.compileSdk + " minSdk=" + androidTarget.minSdk)
                }
            }
            """.trimIndent()
        } else {
            ""
        }
        writeModule(
            "androidlib",
            """
            import com.sphereon.gradle.plugin.configureAndroidLibraryTargetIfEnabled
            import com.sphereon.gradle.plugin.configureStandardTargets

            plugins {
                id("org.jetbrains.kotlin.multiplatform")
            }
            kotlin {
                configureStandardTargets()
                configureAndroidLibraryTargetIfEnabled(project, "com.sphereon.fixture.androidlib")
            }
            afterEvaluate {
                println("ANDROID_PLUGIN_APPLIED=" + plugins.hasPlugin("com.android.kotlin.multiplatform.library"))
            }
            """.trimIndent() + "\n" + androidReport,
        )
    }

    /** The implementation classpath of the plugin under test, as TestKit's no-argument call would inject it. */
    private fun pluginUnderTestClasspath(): List<File> {
        val metadata = Properties()
        val resource = checkNotNull(javaClass.classLoader.getResourceAsStream("plugin-under-test-metadata.properties")) {
            "plugin-under-test-metadata.properties is missing from the test classpath"
        }
        resource.use { stream -> metadata.load(stream) }
        return metadata.getProperty("implementation-classpath").split(File.pathSeparator).map { entry -> File(entry) }
    }

    /**
     * The Android Gradle Plugin and its dependencies, resolved by a helper build. TestKit injects the plugin
     * under test through its own class loader, so AGP must sit in that same loader to see the Kotlin classes.
     * Kotlin artifacts are excluded: the plugin classpath already carries them.
     */
    private fun agpClasspath(): List<File> {
        val helperDirectory = projectDirectory.resolve("agp-resolver").createDirectories()
        helperDirectory.resolve("settings.gradle.kts").writeText("rootProject.name = \"agp-resolver\"\n")
        helperDirectory.resolve("build.gradle.kts").writeText(
            """
            import org.gradle.api.attributes.Usage
            import org.gradle.api.attributes.java.TargetJvmEnvironment

            plugins {
                base
            }
            repositories {
                google()
                mavenCentral()
            }
            val agp = configurations.create("agp") {
                isCanBeConsumed = false
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
                    attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM))
                }
                exclude(group = "org.jetbrains.kotlin")
            }
            dependencies {
                add("agp", "com.android.tools.build:gradle:9.2.1")
            }
            tasks.register("printAgpClasspath") {
                doLast {
                    agp.resolve().forEach { jar -> println("AGP_JAR=" + jar.absolutePath) }
                }
            }
            """.trimIndent() + "\n",
        )
        val output = GradleRunner.create()
            .withProjectDir(helperDirectory.toFile())
            .withArguments("printAgpClasspath", "-q", "--stacktrace")
            .forwardOutput()
            .build()
            .output
        return output.lines()
            .filter { line -> line.startsWith("AGP_JAR=") }
            .map { line -> File(line.removePrefix("AGP_JAR=")) }
    }

    private fun writeModule(name: String, buildScript: String) {
        val moduleDirectory = projectDirectory.resolve(name).createDirectories()
        moduleDirectory.resolve("build.gradle.kts").writeText(buildScript + "\n")
    }
}
