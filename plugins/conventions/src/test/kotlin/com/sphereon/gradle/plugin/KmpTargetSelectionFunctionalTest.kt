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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir

class KmpTargetSelectionFunctionalTest {
    @TempDir
    lateinit var projectDirectory: Path

    /** The host the TestKit builds run on, read without touching the shared test JVM's properties. */
    private val host = KmpHost(System.getProperty("os.name").orEmpty(), System.getProperty("os.arch").orEmpty())

    @Test
    fun noPropertyStaysJvmOnly() {
        writeFixture()

        // A separate TestKit directory gives this build a fresh daemon, so no earlier -Dkmp.targets can linger.
        // It lives under the build directory: its daemon keeps files open after the test's temp folder is removed.
        val output = runner("lib", kmpTargets = null, environmentWithoutAndroidSdk())
            .withTestKitDir(File("build/tmp/testkit-no-property").absoluteFile)
            .build().output

        assertContains(output, "compileKotlinJvm")
        assertJvmOnly(output)
    }

    @Test
    fun jvmOnlyConfiguresNoJsWasmNativeOrAndroidTasks() {
        writeFixture()

        val output = listTasks("lib", "jvm", environmentWithoutAndroidSdk())

        assertContains(output, "compileKotlinJvm")
        assertJvmOnly(output)
    }

    @Test
    fun commandLineBeatsGradlePropertiesSystemProperty() {
        writeFixture()
        projectDirectory.resolve("gradle.properties").writeText("systemProp.kmp.targets=jvm\n")

        val output = listTasks("lib", "jvm,wasmJs", environmentWithoutAndroidSdk())

        assertContains(output, "compileKotlinWasmJs")
    }

    @Test
    fun jsTargetCompilesEs2015WithGenerators() {
        writeFixture()

        val output = listTasks("lib", "js", environmentWithoutAndroidSdk())

        assertContains(output, "compileKotlinJs")
        assertContains(output, "JS_TARGET=es2015")
        assertContains(output, "JS_GENERATORS=true")
    }

    @Test
    fun nativeDeclaresLinuxX64OnEveryHost() {
        writeFixture()

        val output = listTasks("lib", "jvm,native", environmentWithoutAndroidSdk())

        assertContains(output, "compileKotlinLinuxX64")
        assertContains(output, "linuxX64Test")
    }

    @Test
    fun namedNativeFailsLinuxX64TestOffLinuxX64() {
        assumeFalse(host.isLinuxX64, "linuxX64Test runs on this host")
        writeFixture()

        val output = runner("lib", "jvm,native", environmentWithoutAndroidSdk(), ":lib:linuxX64Test", "-x", ":lib:linkDebugTestLinuxX64")
            .buildAndFail().output

        assertContains(output, "linuxX64Test needs a Linux x86_64 host; this host is ${host.description}")
    }

    @Test
    fun allBuildsEveryHostTargetButAndroid() {
        writeFixture()

        val output = listTasks("lib", "all", environmentWithoutAndroidSdk())

        assertContains(output, "compileKotlinJs")
        assertContains(output, "compileKotlinWasmJs")
        assertContains(output, "compileKotlinLinuxX64")
        assertTrue(androidTaskNames(output).isEmpty(), "Android tasks present for kmp.targets=all:\n$output")
        if (!host.isMacOs) {
            assertContains(output, "iOS targets skipped for kmp.targets=all")
            assertFalse(output.contains("compileKotlinIos"), "iOS tasks present on a non-macOS host:\n$output")
        }
        if (!host.isLinuxX64) assertContains(output, "Skipped for kmp.targets=all.")
    }

    @Test
    fun unknownTokenFailsTheBuild() {
        writeFixture()

        val output = runner("lib", "jvm,andriod", environmentWithoutAndroidSdk(), ":lib:tasks").buildAndFail().output

        assertContains(output, "Unknown kmp.targets value 'andriod'")
    }

    @Test
    fun namedIosFailsOffMacOs() {
        assumeFalse(host.isMacOs, "iOS is enabled on macOS hosts")
        writeFixture()

        val output = runner("lib", "jvm,ios", environmentWithoutAndroidSdk(), ":lib:tasks").buildAndFail().output

        assertContains(output, "iOS targets need a macOS host with Xcode; this host is ${host.description}")
    }

    @Test
    fun settingsPluginExposesTheSelection() {
        writeFixture(settingsPlugin = true)

        val output = listTasks("lib", "jvm,wasmJs", environmentWithoutAndroidSdk())

        assertContains(output, "SETTINGS_WASM=true SETTINGS_ANDROID=false")
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
        val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        val hasSdk = androidHome != null && Path.of(androidHome).toFile().isDirectory
        // CI runners carry an SDK, so there a missing SDK is a broken runner rather than a reason to skip.
        if (!hasSdk && System.getenv("CI") == "true") fail("ANDROID_HOME is not set to an SDK directory on this CI runner")
        assumeTrue(hasSdk, "ANDROID_HOME is not set to an SDK directory on this host")
        writeFixture(withAndroidReport = true)

        val output = listTasks("androidlib", "jvm,android", System.getenv(), extraClasspath = agpClasspath())

        assertContains(output, "ANDROID_PLUGIN_APPLIED=true")
        assertContains(output, "ANDROID_TARGET_COUNT=1")
        assertContains(output, "ANDROID_TARGET namespace=com.sphereon.fixture.androidlib compileSdk=36 minSdk=$ANDROID_MIN_SDK")
        // The build coordinator allowlists this name for KMP Android libraries made by the AGP KMP library plugin.
        assertContains(output, "compileAndroidMain")
    }

    private fun assertJvmOnly(output: String) {
        assertFalse(output.contains("compileKotlinJs"), "JS compile task present:\n$output")
        assertFalse(output.contains("compileKotlinWasmJs"), "wasmJs compile task present:\n$output")
        assertFalse(output.contains("compileKotlinLinuxX64"), "linuxX64 compile task present:\n$output")
        assertTrue(androidTaskNames(output).isEmpty(), "Android tasks present:\n$output")
    }

    private fun environmentWithoutAndroidSdk(): Map<String, String> =
        System.getenv().filterKeys { key -> key != "ANDROID_HOME" && key != "ANDROID_SDK_ROOT" }

    private fun runner(
        module: String,
        kmpTargets: String?,
        environment: Map<String, String>,
        vararg tasks: String,
        extraClasspath: List<File> = emptyList(),
    ): GradleRunner {
        val taskArguments = if (tasks.isEmpty()) listOf(":$module:tasks", "--all") else tasks.toList()
        val propertyArguments = if (kmpTargets == null) emptyList() else listOf("-Dkmp.targets=$kmpTargets")
        return GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withPluginClasspath(pluginUnderTestClasspath() + extraClasspath)
            .withEnvironment(environment)
            .withArguments(taskArguments + propertyArguments + "--stacktrace")
            .forwardOutput()
    }

    private fun listTasks(
        module: String,
        kmpTargets: String,
        environment: Map<String, String>,
        extraClasspath: List<File> = emptyList(),
    ): String = runner(module, kmpTargets, environment, extraClasspath = extraClasspath).build().output

    /** Names of listed tasks that belong to the Android target, from the `name - description` rows of `tasks --all`. */
    private fun androidTaskNames(output: String): List<String> =
        output.lines()
            .filter { line -> line.contains(" - ") }
            .map { line -> line.substringBefore(" - ").trim() }
            .filter { name -> name.contains("android", ignoreCase = true) }

    private fun writeFixture(withAndroidReport: Boolean = false, settingsPlugin: Boolean = false) {
        val settingsPlugins = if (settingsPlugin) {
            """
            plugins {
                id("com.sphereon.gradle.plugin.kmp-targets")
            }
            """.trimIndent() + "\n"
        } else {
            ""
        }
        val settingsReport = if (settingsPlugin) {
            "println(\"SETTINGS_WASM=\" + kmpTargetEnabled(\"wasmJs\") + \" SETTINGS_ANDROID=\" + kmpTargetEnabled(\"android\"))\n"
        } else {
            ""
        }
        val settingsImports = if (settingsPlugin) "import com.sphereon.gradle.plugin.kmpTargetEnabled\n\n" else ""
        projectDirectory.resolve("settings.gradle.kts").writeText(
            settingsImports + """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            """.trimIndent() + "\n" + settingsPlugins +
                """
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
            """.trimIndent() + "\n" + settingsReport,
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
            import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile

            plugins {
                id("org.jetbrains.kotlin.multiplatform")
            }
            kotlin {
                configureStandardTargets()
            }
            afterEvaluate {
                tasks.withType(Kotlin2JsCompile::class.java).matching { task -> task.name == "compileKotlinJs" }.forEach { task ->
                    println("JS_TARGET=" + task.compilerOptions.target.get())
                    println("JS_GENERATORS=" + task.compilerOptions.freeCompilerArgs.get().contains("-Xes-generators"))
                }
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
     * The Android Gradle Plugin and its dependencies, resolved by a helper build at the catalog's AGP version.
     * TestKit injects the plugin under test through its own class loader, so AGP must sit in that same loader
     * to see the Kotlin classes. Kotlin artifacts are excluded: the plugin classpath already carries them.
     */
    private fun agpClasspath(): List<File> {
        val agpVersion = checkNotNull(System.getProperty("sphereon.test.agpVersion")) {
            "sphereon.test.agpVersion is not set; the conventions build passes the catalog's agp version"
        }
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
                add("agp", "com.android.tools.build:gradle:$agpVersion")
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
