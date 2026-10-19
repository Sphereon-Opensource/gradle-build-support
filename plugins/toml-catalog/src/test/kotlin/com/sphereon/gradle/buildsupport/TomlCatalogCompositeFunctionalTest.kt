package com.sphereon.gradle.buildsupport

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class TomlCatalogCompositeFunctionalTest {
    @TempDir
    lateinit var temporary: Path

    @Test
    fun includedBomResolvesPrimaryAndClassifiedCatalogCoordinates() {
        val producer = Files.createDirectory(temporary.resolve("library-bom"))
        val consumer = Files.createDirectory(temporary.resolve("consumer"))
        write(producer, "settings.gradle.kts", "rootProject.name = \"library-bom\"\n")
        write(producer, "build.gradle.kts", """
            buildscript {
                dependencies { classpath(files(${pluginClasspathLiteral()})) }
            }
            plugins {
                `java-platform`
                `maven-publish`
            }
            group = "com.sphereon.gradle"
            version = "1.0"
            apply(plugin = "com.sphereon.gradle.toml-catalog")
            javaPlatform { allowDependencies() }
            dependencies {
                constraints { api("org.example:sample-lib:1.2.3") }
            }
            tasks.register("verifyCatalogMetadata") {
                dependsOn("generateTomlCatalog")
                doLast {
                    val artifacts = configurations.getByName("sphereonLibraryBomCatalogElements").outgoing.artifacts
                    check(artifacts.size == 2)
                    check(artifacts.toList().all { it.name == "library-bom" && it.extension == "toml" })
                    check(artifacts.map { it.classifier }.toSet() == setOf(null, "bom"))
                    check(artifacts.map { it.file.name }.toSet() == setOf(
                        "sphereonLibraryBom.versioned.toml", "sphereonLibraryBom.toml"
                    ))
                }
            }
        """.trimIndent())
        assertFalse(Files.exists(producer.resolve("build/tomlCatalog")))
        val generated = runner(producer, "generateTomlCatalog").build()
        assertEquals(TaskOutcome.SUCCESS, generated.task(":generateTomlCatalog")?.outcome)
        assertTrue(Files.isRegularFile(producer.resolve("build/tomlCatalog/sphereonLibraryBom.versioned.toml")))
        assertTrue(Files.isRegularFile(producer.resolve("build/tomlCatalog/sphereonLibraryBom.toml")))

        // Settings-time catalog GAV substitution is unsupported: https://github.com/gradle/gradle/issues/18847
        write(consumer, "settings.gradle.kts", """
            rootProject.name = "catalog-consumer"
            includeBuild("../library-bom") {
                dependencySubstitution {
                    substitute(module("com.sphereon.gradle:library-bom")).using(project(":"))
                }
            }
            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        from(files("../library-bom/build/tomlCatalog/sphereonLibraryBom.versioned.toml"))
                    }
                }
            }
        """.trimIndent())
        write(consumer, "build.gradle.kts", """
            import org.gradle.api.artifacts.VersionCatalogsExtension
            import org.gradle.api.attributes.Category
            import org.gradle.api.attributes.Usage

            // Check both GAV selectors independently of the generated-file catalog bootstrap.
            val primaryCatalog = configurations.create("primaryCatalog") {
                isCanBeResolved = true
                isCanBeConsumed = false
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.REGULAR_PLATFORM))
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.VERSION_CATALOG))
                }
            }
            dependencies.add(primaryCatalog.name, "com.sphereon.gradle:library-bom:1.0@toml")
            val classifiedCatalog = configurations.create("classifiedCatalog") {
                isCanBeResolved = true
                isCanBeConsumed = false
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.REGULAR_PLATFORM))
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.VERSION_CATALOG))
                }
            }
            dependencies.add(classifiedCatalog.name, "com.sphereon.gradle:library-bom:1.0:bom@toml")
            tasks.register("checkCatalog") {
                doLast {
                    val primary = primaryCatalog.singleFile
                    val imported = file("../library-bom/build/tomlCatalog/sphereonLibraryBom.versioned.toml")
                    check(primary.name == "sphereonLibraryBom.versioned.toml")
                    check(primary.canonicalFile == imported.canonicalFile)
                    check(primary.readText() == imported.readText())
                    val classified = classifiedCatalog.singleFile
                    check(classified.name == "sphereonLibraryBom.toml")
                    check(classified.readText() == file("../library-bom/build/tomlCatalog/sphereonLibraryBom.toml").readText())
                    val catalogs = project.extensions.getByType(VersionCatalogsExtension::class.java)
                    val library = catalogs.named("libs").findLibrary("org-example-sample-lib").get().get()
                    check(library.module.toString() == "org.example:sample-lib")
                    check(library.versionConstraint.requiredVersion == "1.2.3")
                }
            }
        """.trimIndent())
        val resolved = runner(consumer, "checkCatalog").build()
        assertEquals(TaskOutcome.SUCCESS, resolved.task(":checkCatalog")?.outcome)
        val metadata = runner(producer, "verifyCatalogMetadata").build()
        assertEquals(TaskOutcome.SUCCESS, metadata.task(":verifyCatalogMetadata")?.outcome)
    }

    private fun runner(directory: Path, vararg tasks: String) = GradleRunner.create()
        .withProjectDir(directory.toFile())
        .withArguments(*tasks, "--stacktrace")
        .forwardOutput()

    private fun pluginClasspathLiteral(): String {
        val properties = Properties()
        requireNotNull(javaClass.classLoader.getResourceAsStream("plugin-under-test-metadata.properties"))
            .use(properties::load)
        return properties.getProperty("implementation-classpath")
            .split(File.pathSeparator)
            .joinToString(", ") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }
    }

    private fun write(directory: Path, name: String, content: String) {
        Files.writeString(directory.resolve(name), content + "\n")
    }
}
