package com.sphereon.gradle.plugin

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile

private const val MANIFEST = "META-INF/sphereon-openapi.json"
private const val RECIPE = "sphereon-openapi-specs-v1"
private const val FILE_LIMIT = 64 * 1024 * 1024
private const val ARCHIVE_LIMIT = 256 * 1024 * 1024

private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

internal fun validateOpenApiPath(path: String): String {
    val parts = path.split('/')
    if (path.isEmpty() || path.contains('\\') || path.any { it.code < 32 || it == ':' } ||
        parts.any { it.isEmpty() || it == "." || it == ".." || it.endsWith('.') || it.endsWith(' ') ||
            it.substringBefore('.').uppercase(Locale.ROOT).matches(Regex("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) }) {
        throw GradleException("Unsafe OpenAPI archive path: $path")
    }
    return path
}

private fun requirePlainAncestors(file: File) {
    var path = file.toPath().toAbsolutePath().normalize()
    while (true) {
        if (Files.exists(path, NOFOLLOW_LINKS) && (Files.isSymbolicLink(path) ||
                !Files.isDirectory(path, NOFOLLOW_LINKS))) {
            throw GradleException("OpenAPI cache must use ordinary directories: $path")
        }
        path = path.parent ?: break
    }
}

/** Extracts one immutable spec coordinate, preserving relative references and verifying reuse. */
internal fun extractOpenApiArchive(jar: File, cache: File, expectedVersion: String): File {
    if (!expectedVersion.matches(Regex("[0-9a-f]{32}"))) throw GradleException("Invalid OpenAPI specs version")
    val archiveDigest = MessageDigest.getInstance("SHA-256")
    jar.inputStream().use { input ->
        val buffer = ByteArray(65536)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            archiveDigest.update(buffer, 0, count)
        }
    }
    val key = archiveDigest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    val contents = linkedMapOf<String, ByteArray>()
    val folded = mutableSetOf<String>()
    var total = 0L
    ZipFile(jar).use { zip ->
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val name = validateOpenApiPath(entry.name)
            if (entry.isDirectory || contents.size >= 20000 || !folded.add(name.lowercase(Locale.ROOT))) {
                throw GradleException("Duplicate or unsupported OpenAPI archive entry: $name")
            }
            val bytes = zip.getInputStream(entry).use { it.readNBytes(FILE_LIMIT + 1) }
            total += bytes.size
            if (bytes.size > FILE_LIMIT || total > ARCHIVE_LIMIT) throw GradleException("OpenAPI archive exceeds size limits")
            contents[name] = bytes
        }
    }
    val metadata = contents[MANIFEST] ?: throw GradleException("OpenAPI archive manifest is missing")
    val manifest = JsonSlurper().parseText(metadata.toString(Charsets.UTF_8)) as? Map<*, *>
        ?: throw GradleException("Invalid OpenAPI archive manifest")
    if (manifest["schemaVersion"] != 1 || manifest["recipe"] != RECIPE || manifest["version"] != expectedVersion) {
        throw GradleException("OpenAPI archive does not match the pinned coordinate")
    }
    val records = manifest["files"] as? List<*> ?: throw GradleException("OpenAPI file receipts are missing")
    val paths = mutableSetOf<String>()
    val input = StringBuilder(RECIPE + "\n")
    var previous = ""
    for (value in records) {
        val record = value as? Map<*, *> ?: throw GradleException("Invalid OpenAPI file receipt")
        val path = validateOpenApiPath(record["path"] as? String ?: "")
        val bytes = contents[path] ?: throw GradleException("OpenAPI archive entry is missing: $path")
        val sha = record["sha256"] as? String
        val size = record["size"] as? Number ?: throw GradleException("Invalid OpenAPI file size: $path")
        if (path == MANIFEST || path <= previous || !paths.add(path) || size.toLong() != bytes.size.toLong() || sha != digest(bytes)) {
            throw GradleException("OpenAPI archive receipt mismatch: $path")
        }
        previous = path
        input.append(path).append('\u0000').append(sha).append('\u0000').append(size.toLong()).append('\n')
    }
    val sourceId = digest(input.toString().toByteArray(Charsets.UTF_8))
    if (manifest["sourceInputId"] != sourceId || sourceId.take(32) != expectedVersion ||
        contents.keys != paths + MANIFEST || !paths.containsAll(listOf("common-components.yml", "manifest-catalog.json"))) {
        throw GradleException("OpenAPI archive identity or inventory mismatch")
    }
    val normalizedCache = cache.absoluteFile.normalize()
    requirePlainAncestors(normalizedCache)
    Files.createDirectories(normalizedCache.toPath())
    val target = File(normalizedCache, key)
    val lockFile = File(normalizedCache, ".extract.lock").toPath()
    if (Files.exists(lockFile, NOFOLLOW_LINKS) && !Files.isRegularFile(lockFile, NOFOLLOW_LINKS)) {
        throw GradleException("Invalid OpenAPI cache lock")
    }
    FileChannel.open(lockFile, CREATE, WRITE).use { channel ->
        channel.lock().use {
            if (target.exists()) {
                requirePlainAncestors(target)
                val actual = Files.walk(target.toPath()).use { stream ->
                    stream.filter { !Files.isDirectory(it, NOFOLLOW_LINKS) }.map { path ->
                        if (!Files.isRegularFile(path, NOFOLLOW_LINKS)) throw GradleException("Invalid cached OpenAPI input: $path")
                        target.toPath().relativize(path).toString().replace('\\', '/')
                    }.toList().toSet()
                }
                if (actual != contents.keys || contents.any { (path, bytes) ->
                        val file = File(target, path)
                        file.length() != bytes.size.toLong() || !file.readBytes().contentEquals(bytes)
                    }) throw GradleException("Cached OpenAPI specs were modified: $target")
                return target
            }
            val staging = Files.createTempDirectory(normalizedCache.toPath(), ".extract-")
            try {
                contents.forEach { (path, bytes) ->
                    val destination = staging.resolve(path).normalize()
                    if (!destination.startsWith(staging)) throw GradleException("Unsafe extraction destination")
                    Files.createDirectories(destination.parent)
                    Files.write(destination, bytes)
                }
                Files.move(staging, target.toPath(), ATOMIC_MOVE)
            } finally {
                // This freshly allocated directory belongs exclusively to this extraction.
                if (Files.exists(staging, NOFOLLOW_LINKS)) {
                    check(staging.toAbsolutePath().normalize().parent == normalizedCache.toPath())
                    Files.walk(staging).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
                }
            }
        }
    }
    return target
}
