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

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir

/** npm publication follows the targets a module declares in its build script, not the requested kmp.targets. */
class NpmPublicationTargetsFunctionalTest {
    @TempDir
    lateinit var projectDirectory: Path

    @Test
    fun aModuleThatDeclaresJsGetsNpmTasksAndAJvmOnlyModuleDoesNot() {
        projectDirectory.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                }
            }
            rootProject.name = "npm-target-fixture"
            include(":web", ":server")
            """.trimIndent() + "\n",
        )
        projectDirectory.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform") apply false
                id("com.sphereon.gradle.plugin.npm-publication") apply false
            }
            """.trimIndent() + "\n",
        )
        // The js target is declared while the build script runs, after npm-publication was applied.
        module(
            "web",
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform")
                id("com.sphereon.gradle.plugin.npm-publication")
            }
            kotlin {
                jvm()
                run {
                    js {
                        nodejs()
                        binaries.library()
                    }
                }
            }
            """,
        )
        module(
            "server",
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform")
                id("com.sphereon.gradle.plugin.npm-publication")
            }
            kotlin {
                jvm()
            }
            """,
        )

        val web = tasks("web")
        assertContains(web, "assembleJsPackage")

        val server = tasks("server")
        assertFalse(server.contains("assembleJsPackage"), "npm tasks on a JVM-only module:\n$server")
    }

    private fun module(name: String, script: String) {
        val directory = projectDirectory.resolve(name).createDirectories()
        directory.resolve("build.gradle.kts").writeText(script.trimIndent() + "\n")
    }

    private fun tasks(module: String): String =
        GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withPluginClasspath()
            .withArguments(":$module:tasks", "--all", "-Dkmp.targets=jvm,js", "--stacktrace")
            .forwardOutput()
            .build()
            .output
}
