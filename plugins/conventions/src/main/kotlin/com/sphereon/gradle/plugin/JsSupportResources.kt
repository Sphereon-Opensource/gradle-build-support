package com.sphereon.gradle.plugin

import org.gradle.api.GradleException
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

private object JsSupportResources

/** Resolve support bytes from the published plugin, without a GBS source checkout. */
internal fun bundledJsSupportFile(cache: File, name: String): File {
    if (name !in setOf("esm-require-shim.cjs", "webpack-node-scheme.js")) {
        throw GradleException("Unknown bundled JS support resource: $name")
    }
    val bytes = JsSupportResources::class.java.getResourceAsStream("/com/sphereon/gradle/plugin/js/$name")
        ?.use { it.readBytes() } ?: throw GradleException("Published conventions plugin lacks JS support resource: $name")
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    val directory = cache.toPath().toAbsolutePath().normalize().resolve(hash)
    fun checkDirectories() {
        var path = directory
        while (true) {
            if (Files.exists(path, NOFOLLOW_LINKS) &&
                (Files.isSymbolicLink(path) || !Files.isDirectory(path, NOFOLLOW_LINKS))) {
                throw GradleException("JS support cache must use ordinary directories: $path")
            }
            path = path.parent ?: break
        }
    }
    checkDirectories()
    Files.createDirectories(directory)
    checkDirectories()
    val target = directory.resolve(name)
    if (Files.exists(target, NOFOLLOW_LINKS)) {
        if (!Files.isRegularFile(target, NOFOLLOW_LINKS) || !Files.readAllBytes(target).contentEquals(bytes)) {
            throw GradleException("JS support cache differs from the published resource: $target")
        }
        return target.toFile()
    }
    val temporary = Files.createTempFile(directory, ".js-support-", ".tmp")
    try {
        Files.write(temporary, bytes)
        checkDirectories()
        try {
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target, REPLACE_EXISTING)
        }
        if (!Files.isRegularFile(target, NOFOLLOW_LINKS) || !Files.readAllBytes(target).contentEquals(bytes)) {
            throw GradleException("Bundled JS support installation changed: $target")
        }
        return target.toFile()
    } finally {
        Files.deleteIfExists(temporary)
    }
}
