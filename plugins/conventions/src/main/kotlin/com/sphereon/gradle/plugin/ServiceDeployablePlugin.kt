package com.sphereon.gradle.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File
import java.util.jar.JarFile

/**
 * Convention plugin for deployable IDK service assemblies.
 *
 * Provides:
 * - mergeServiceFiles: Merges SPI (META-INF/services) files from all runtime JARs
 * - buildFatJar: Builds an executable fat JAR with merged SPI files
 * - runServer: Runs the service from compiled classes
 *
 * Usage in build.gradle.kts:
 * ```
 * plugins {
 *     id("com.sphereon.gradle.plugin.service-deployable")
 * }
 * serviceDeployable {
 *     mainClass.set("com.sphereon.myapp.MainKt")
 * }
 * ```
 *
 * The plugin must be applied AFTER the kotlin multiplatform plugin and the
 * kotlin {} block that declares the jvm() target.
 */
class ServiceDeployablePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "serviceDeployable",
            ServiceDeployableExtension::class.java
        )

        project.afterEvaluate {
            val kotlin = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
                ?: error("ServiceDeployablePlugin requires the Kotlin Multiplatform plugin")

            val mainClassName = extension.mainClass.get()
            val mainCompilation = kotlin.jvm().compilations.getByName("main")

            val runtimeJars = project.configurations.named("jvmRuntimeClasspath").map { config ->
                config.filter { it.name.endsWith(".jar") }
            }

            val mergeServiceFiles = project.tasks.register<MergeServiceFilesTask>("mergeServiceFiles") {
                inputJars.from(runtimeJars)
                outputDir.set(project.layout.buildDirectory.dir("merged-services/META-INF/services"))
            }

            project.tasks.register<Jar>("buildFatJar") {
                group = "build"
                description = "Build executable fat JAR for ${project.name}"
                archiveClassifier.set("all")
                duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE

                manifest {
                    attributes["Main-Class"] = mainClassName
                    attributes["Implementation-Version"] = project.version.toString()
                }

                from(project.provider { mainCompilation.output.classesDirs })
                from(project.provider { mainCompilation.output.resourcesDir })
                from(project.layout.buildDirectory.dir("merged-services"))
                from(runtimeJars.map { jars -> jars.map { project.zipTree(it) } })

                exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
                dependsOn("jvmMainClasses", mergeServiceFiles)
            }

            project.tasks.register<org.gradle.api.tasks.JavaExec>("runServer") {
                group = "application"
                description = "Run the ${project.name} service"
                dependsOn("compileKotlinJvm")
                classpath = project.files(
                    project.provider { mainCompilation.output.allOutputs },
                    project.configurations.named("jvmRuntimeClasspath")
                )
                mainClass.set(mainClassName)
                workingDir = project.projectDir
            }
        }
    }
}

abstract class ServiceDeployableExtension {
    abstract val mainClass: org.gradle.api.provider.Property<String>
}

abstract class MergeServiceFilesTask : DefaultTask() {
    @get:InputFiles
    abstract val inputJars: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun merge() {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        for (jar in inputJars) {
            if (!jar.name.endsWith(".jar")) continue
            val jf = JarFile(jar)
            try {
                val entries = jf.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.name.startsWith("META-INF/services/") || entry.isDirectory) continue
                    val svcName = entry.name.substringAfterLast("/")
                    val content = jf.getInputStream(entry).bufferedReader().readText().trim()
                    if (content.isEmpty()) continue
                    val target = File(dir, svcName)
                    if (target.exists()) {
                        val existingLines = target.readText().lines().toSet()
                        val newLines = mutableListOf<String>()
                        for (line in content.lines()) {
                            if (line.isNotBlank() && line !in existingLines) newLines.add(line)
                        }
                        if (newLines.isNotEmpty()) target.appendText("\n" + newLines.joinToString("\n"))
                    } else {
                        target.writeText(content)
                    }
                }
            } finally {
                jf.close()
            }
        }
    }
}
