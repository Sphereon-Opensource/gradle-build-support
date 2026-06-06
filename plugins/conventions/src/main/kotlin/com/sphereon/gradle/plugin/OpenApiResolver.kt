package com.sphereon.gradle.plugin

import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File

/**
 * Resolves OpenAPI specs from the `openapi` git submodule (the single source of truth shared by
 * IDK / EDK / VDX), replacing the old per-module `syncOpenApiCommonComponents` copy task.
 *
 * Build scripts call [openapiSpec] to point an openapi-generator `inputSpec` at a spec in the
 * submodule. Every spec keeps same-directory `./common-components.yml` (and `./<bundle>.yml`)
 * refs, and the submodule ships those siblings alongside each spec, so the generator resolves
 * them in place — no copy step.
 *
 * A full VDX-infra checkout contains THREE physical openapi checkouts (`vdx/openapi`,
 * `vdx/edk/openapi`, `vdx/edk/idk/openapi`), one per nested product submodule. They must be
 * pinned in lockstep; [openapiCheckout] fails loud if any two point at different commits, and
 * returns the SHALLOWEST present checkout (the outermost product is authoritative, matching the
 * dependency arrow VDX -> EDK -> IDK).
 */

// SHALLOWEST-first: the outermost product's pinned checkout wins.
private val OPENAPI_CHECKOUT_CANDIDATES = listOf(
    "openapi",             // standalone IDK / EDK / VDX root
    "vdx/openapi",         // VDX-infra composite: VDX layer (authoritative outer)
    "vdx/edk/openapi",     // EDK layer
    "vdx/edk/idk/openapi", // IDK layer (innermost)
)

private fun Project.openapiCheckoutCandidates(): List<File> =
    OPENAPI_CHECKOUT_CANDIDATES
        .map { rootProject.layout.projectDirectory.dir(it).asFile }
        .filter { it.isDirectory && File(it, "shared/common-components.yml").isFile }

/**
 * The authoritative `openapi` submodule checkout for this build. Throws if none is present
 * (submodule not initialised) or if present checkouts have diverged (gitlink pins out of lockstep).
 */
fun Project.openapiCheckout(): File {
    val present = openapiCheckoutCandidates()
    if (present.isEmpty()) {
        throw GradleException(
            "openapi submodule not found under $rootDir. " +
                "Run `git submodule update --init --recursive`.",
        )
    }
    val shaByCheckout = present.associateWith { headSha(it) }
    val distinctShas = shaByCheckout.values.filterNotNull().toSet()
    if (distinctShas.size > 1) {
        val detail = shaByCheckout.entries.joinToString("\n  ") { "${it.key} = ${it.value}" }
        throw GradleException(
            "openapi submodule SHA mismatch across nested checkouts " +
                "(bump all gitlink pins in lockstep):\n  $detail",
        )
    }
    return present.first() // shallowest present == outermost authoritative
}

/** The commit checked out in an openapi submodule dir, or null if it cannot be determined. */
private fun headSha(checkout: File): String? {
    val dotGit = File(checkout, ".git")
    val gitDir: File? = when {
        dotGit.isDirectory -> dotGit
        dotGit.isFile ->
            dotGit.readText().lineSequence()
                .firstOrNull { it.startsWith("gitdir:") }
                ?.substringAfter("gitdir:")?.trim()
                ?.let { checkout.resolve(it).normalize() }
        else -> null
    }
    if (gitDir == null) return null
    val head = File(gitDir, "HEAD").takeIf { it.isFile }?.readText()?.trim() ?: return null
    return if (head.startsWith("ref:")) {
        File(gitDir, head.substringAfter("ref:").trim()).takeIf { it.isFile }?.readText()?.trim()
    } else {
        head
    }
}

/**
 * Resolves a repo-relative spec path (e.g. `"idk/kms-openapi.yml"`, `"vdx/party-manager-openapi.yml"`)
 * to its file in the openapi submodule. Throws if the spec is missing.
 */
fun Project.openapiSpec(repoRelativePath: String): File {
    val checkout = openapiCheckout()
    val spec = checkout.resolve(repoRelativePath)
    if (!spec.isFile) {
        throw GradleException("openapi spec not found: $repoRelativePath under $checkout")
    }
    return spec
}

/** The shared component directory of the openapi submodule (`shared/`). */
fun Project.openapiSharedDir(): File = openapiCheckout().resolve("shared")
