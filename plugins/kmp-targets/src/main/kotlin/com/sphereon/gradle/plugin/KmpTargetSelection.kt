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

import org.gradle.api.GradleException

/** The Kotlin target families that `kmp.targets` can turn on. */
enum class KmpTarget(val token: String) {
    JVM("jvm"),
    JS("js"),
    WASM_JS("wasmjs"),
    NATIVE("native"),
    ANDROID("android"),
    IOS("ios"),
}

/** The host a build runs on, as far as target selection cares. */
data class KmpHost(val osName: String, val osArch: String) {
    val isMacOs: Boolean get() = osName.startsWith("Mac")
    val isLinuxX64: Boolean get() = osName.startsWith("Linux") && osArch in setOf("amd64", "x86_64")
    val description: String get() = "$osName $osArch"
}

/**
 * The parsed value of the `kmp.targets` system property.
 *
 * Accepted tokens, case-insensitive and comma-separated: `jvm`, `js`, `wasmJs` (alias `wasm`), `native`
 * (alias `linuxX64`), `android`, `ios` and `all`. Any other token fails the build with the allowed values.
 * No property means `jvm`. A property that is present but blank is an error: it almost always comes from an
 * unset script variable, and silently building only the JVM would hide that.
 *
 * `all` means every target this host can build, except `android`, which needs an SDK and must be named.
 * The JVM is always built; the other tokens add targets next to it.
 */
class KmpTargetSelection private constructor(
    /** The targets named explicitly. */
    val named: Set<KmpTarget>,
    /** True when `all` was named. */
    val all: Boolean,
) {
    /** True when [target] was asked for, by name or through `all`. Says nothing about the host. */
    fun requested(target: KmpTarget): Boolean =
        target in named || (all && target != KmpTarget.ANDROID)

    /**
     * True when [target] must be declared on [host]. A target named explicitly that the host cannot build
     * fails with the host in the message; one that only `all` asked for is dropped and [onDropped] is called.
     */
    fun enabledOn(host: KmpHost, target: KmpTarget, onDropped: (String) -> Unit = {}): Boolean {
        if (!requested(target)) return false
        if (target == KmpTarget.IOS && !host.isMacOs) {
            if (target in named) {
                throw GradleException("kmp.targets names ios, but iOS targets need a macOS host with Xcode; this host is ${host.description}")
            }
            onDropped("iOS targets skipped for kmp.targets=all: they need a macOS host; this host is ${host.description}")
            return false
        }
        return true
    }

    override fun toString(): String =
        (named.map { target -> target.token } + if (all) listOf("all") else emptyList()).joinToString(",")

    companion object {
        const val PROPERTY = "kmp.targets"

        /** Every token the property accepts, in the spelling the docs use. */
        val ALLOWED_VALUES: List<String> = listOf("jvm", "js", "wasmJs", "wasm", "native", "linuxX64", "android", "ios", "all")

        private val TOKENS: Map<String, KmpTarget?> = mapOf(
            "jvm" to KmpTarget.JVM,
            "js" to KmpTarget.JS,
            "wasmjs" to KmpTarget.WASM_JS,
            "wasm" to KmpTarget.WASM_JS,
            "native" to KmpTarget.NATIVE,
            "linuxx64" to KmpTarget.NATIVE,
            "android" to KmpTarget.ANDROID,
            "ios" to KmpTarget.IOS,
            "all" to null,
        )

        /** Parses [raw], the property value or null when it is not set. */
        fun parse(raw: String?): KmpTargetSelection {
            if (raw == null) return KmpTargetSelection(setOf(KmpTarget.JVM), all = false)
            val tokens = raw.split(",").map { token -> token.trim() }.filter { token -> token.isNotEmpty() }
            if (tokens.isEmpty()) {
                throw GradleException("$PROPERTY is set but empty; use $PROPERTY=jvm for a JVM-only build. Allowed values: ${ALLOWED_VALUES.joinToString(", ")}")
            }
            val named = linkedSetOf(KmpTarget.JVM)
            var all = false
            for (token in tokens) {
                val lowered = token.lowercase()
                if (lowered !in TOKENS) {
                    throw GradleException("Unknown $PROPERTY value '$token' in '$raw'. Allowed values: ${ALLOWED_VALUES.joinToString(", ")}")
                }
                val target = TOKENS[lowered]
                if (target == null) all = true else named += target
            }
            return KmpTargetSelection(named, all)
        }

        /** Parses a single token as [KmpTarget], with the same aliases and errors as the property. */
        fun target(token: String): KmpTarget =
            TOKENS[token.trim().lowercase()]
                ?: throw GradleException("Unknown Kotlin target '$token'. Allowed values: ${ALLOWED_VALUES.filter { value -> value != "all" }.joinToString(", ")}")
    }
}
