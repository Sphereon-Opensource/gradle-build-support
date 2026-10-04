package com.sphereon.gradle.plugin

import dev.petuska.npm.publish.task.NpmPublishTask
import groovy.json.JsonSlurper
import java.io.Serializable
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.specs.Spec

internal object NpmRegistryVersions {
    fun absent(registry: URI, name: String, version: String, token: String?): Boolean {
        val path = URLEncoder.encode(name, Charsets.UTF_8)
        val connection = URI(registry.toString().trimEnd('/') + "/$path/$version")
            .toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            if (!token.isNullOrEmpty()) connection.setRequestProperty("Authorization", "Bearer $token")
            return when (val status = connection.responseCode) {
                404 -> true
                200 -> {
                    val metadata = connection.inputStream.bufferedReader().use { JsonSlurper().parse(it) } as? Map<*, *>
                    if (metadata == null || metadata["name"] != name || metadata["version"] != version) {
                        throw GradleException("Registry returned unexpected npm component metadata")
                    }
                    false
                }
                else -> throw GradleException("Cannot check published npm build version: HTTP $status")
            }
        } finally {
            connection.disconnect()
        }
    }
}

/** Recheck immutable snapshot existence at execution, including on cache reuse. */
internal class UnpublishedNpmBuild : Spec<Task>, Serializable {
    override fun isSatisfiedBy(task: Task): Boolean {
        val publish = task as NpmPublishTask
        if (publish.dry.get()) return true
        val metadata = JsonSlurper().parse(publish.packageDir.file("package.json").get().asFile) as Map<*, *>
        val version = metadata["version"] as String
        if (!Regex(".+-SNAPSHOT\\.build\\.b[a-f0-9]{64}").matches(version)) return true
        val registry = publish.registry.get()
        val absent = NpmRegistryVersions.absent(registry.uri.get(), metadata["name"] as String,
            version, registry.authToken.orNull)
        if (!absent) task.logger.lifecycle("Reusing published npm component ${metadata["name"]}@$version")
        return absent
    }
}
