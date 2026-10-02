package com.sphereon.gradle.plugin

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test

class NpmRegistryVersionsTest {
    private fun registry(status: Int, body: String, test: (URI, AtomicReference<String?>) -> Unit) {
        val authorization = AtomicReference<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            authorization.set(exchange.requestHeaders.getFirst("Authorization"))
            assertEquals("/registry/@sphereon%2Fa/0.26.0-SNAPSHOT.build.b${"a".repeat(64)}",
                exchange.requestURI.rawPath.replace("%40", "@"))
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { test(URI("http://127.0.0.1:${server.address.port}/registry/"), authorization) }
        finally { server.stop(0) }
    }

    private val version = "0.26.0-SNAPSHOT.build.b${"a".repeat(64)}"

    @Test fun existingExactComponentVersionIsReused() {
        registry(200, "{\"name\":\"@sphereon/a\",\"version\":\"$version\"}") { uri, auth ->
            assertFalse(NpmRegistryVersions.absent(uri, "@sphereon/a", version, "test-token"))
            assertEquals("Bearer test-token", auth.get())
        }
    }

    @Test fun missingComponentVersionCanBePublished() {
        registry(404, "{}") { uri, _ ->
            assertTrue(NpmRegistryVersions.absent(uri, "@sphereon/a", version, null))
        }
    }

    @Test fun authenticationFailureDoesNotPretendArtifactIsMissing() {
        registry(401, "{}") { uri, _ ->
            assertFailsWith<GradleException> { NpmRegistryVersions.absent(uri, "@sphereon/a", version, null) }
        }
    }

    @Test fun unexpectedMetadataDoesNotSkipPublication() {
        registry(200, "{\"name\":\"@sphereon/other\",\"version\":\"$version\"}") { uri, _ ->
            assertFailsWith<GradleException> { NpmRegistryVersions.absent(uri, "@sphereon/a", version, null) }
        }
    }
}
