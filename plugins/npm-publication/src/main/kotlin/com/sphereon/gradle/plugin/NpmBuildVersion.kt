package com.sphereon.gradle.plugin

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** A component build version, calculated by ordinary Gradle in any checkout. */
internal object NpmBuildVersion {
    fun identity(baseVersion: String, packageName: String, files: Collection<File>,
                 buildLogic: Collection<File> = emptyList()): String {
        val records = files.flatMap { root ->
            if (!root.exists()) return@flatMap emptyList()
            if (root.isDirectory) {
                root.walkTopDown().filter(File::isFile).map {
                    root.name + "/" + it.relativeTo(root).invariantSeparatorsPath to hash(it)
                }.toList()
            } else listOf(root.name to hash(root))
        } + buildLogic.flatMap { root ->
            if (root.isDirectory) {
                root.walkTopDown().filter(File::isFile).map {
                    "build-logic/" + it.relativeTo(root).invariantSeparatorsPath to hash(it)
                }.toList()
            } else ZipFile(root).use { archive ->
                archive.entries().asSequence().filter { !it.isDirectory }.map { entry ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    archive.getInputStream(entry).use { input ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val length = input.read(buffer)
                            if (length < 0) break
                            digest.update(buffer, 0, length)
                        }
                    }
                    "build-logic/" + entry.name to digest.digest().joinToString("") { "%02x".format(it) }
                }.toList()
            }
        }
        val ordered = records.sortedWith(compareBy({ it.first }, { it.second }))
        if (records.isEmpty()) throw GradleException("No production inputs for npm build version")
        val digest = MessageDigest.getInstance("SHA-256")
        for (value in listOf("sphereon-npm-build-v1", baseVersion, packageName) +
            ordered.flatMap { listOf(it.first, it.second) }) {
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun version(baseVersion: String, buildVersion: String): String {
        if (!baseVersion.endsWith("-SNAPSHOT")) return baseVersion
        if (!Regex("[a-f0-9]{64}").matches(buildVersion)) {
            throw GradleException("npm buildVersion must be a complete SHA-256 component build identity")
        }
        return "${baseVersion.removeSuffix("-SNAPSHOT")}-SNAPSHOT.build.b$buildVersion"
    }

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val length = input.read(buffer)
                if (length < 0) break
                digest.update(buffer, 0, length)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

@CacheableTask
abstract class GenerateNpmBuildVersion : DefaultTask() {
    @get:Input abstract val baseVersion: Property<String>
    @get:Input abstract val packageName: Property<String>
    @get:Input @get:Optional abstract val buildVersion: Property<String>
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productionInputs: ConfigurableFileCollection
    @get:Classpath abstract val buildLogicInputs: ConfigurableFileCollection
    @get:OutputFile abstract val versionFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val base = baseVersion.get()
        val identity = buildVersion.orNull ?: if (base.endsWith("-SNAPSHOT")) {
            NpmBuildVersion.identity(base, packageName.get(), productionInputs.files, buildLogicInputs.files)
        } else ""
        val version = NpmBuildVersion.version(base, identity)
        versionFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(version + "\n")
        }
        logger.info("npm component build version: ${packageName.get()}@$version")
    }
}
