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

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

private const val KMP_TARGETS_PROPERTY = "kmp.targets"
private const val IOS_SKIP_LOGGED_KEY = "sphereonIosSkipLogged"

/** Android compile SDK used by [configureAndroidLibraryTargetIfEnabled] unless a module overrides it. */
internal const val ANDROID_COMPILE_SDK = 36

/**
 * The one parse of the `kmp.targets` system property: lowercase, trimmed tokens, default `jvm`.
 * The aliases `wasm` (for `wasmjs`) and `linuxx64` (for `native`) are folded into their canonical token.
 */
fun requestedKmpTargets(): Set<String> =
    (System.getProperty(KMP_TARGETS_PROPERTY) ?: "jvm")
        .split(",")
        .map { token -> token.trim().lowercase() }
        .filter { token -> token.isNotEmpty() }
        .map { token ->
            when (token) {
                "wasm" -> "wasmjs"
                "linuxx64" -> "native"
                else -> token
            }
        }
        .toSet()

/** True when `all` or the given canonical [token] (see [requestedKmpTargets]) was requested. */
fun kmpTargetEnabled(token: String): Boolean {
    val requested = requestedKmpTargets()
    return "all" in requested || token in requested
}

/** True when the build runs on macOS, the only host that can build Kotlin/Native iOS targets. */
fun isMacOsHost(): Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

/**
 * iOS targets need both a request (`ios` or `all`) and a macOS host. When `ios` is named explicitly
 * on another host, one lifecycle line per build says why nothing was configured.
 */
fun Project.iosTargetsEnabled(): Boolean {
    if (!kmpTargetEnabled("ios")) return false
    if (isMacOsHost()) return true
    if ("ios" in requestedKmpTargets()) {
        val extra = rootProject.extensions.extraProperties
        if (!extra.has(IOS_SKIP_LOGGED_KEY)) {
            extra.set(IOS_SKIP_LOGGED_KEY, true)
            logger.lifecycle("iOS targets skipped: they need a macOS host")
        }
    }
    return false
}

/** True when `android` or `all` was requested; only then may a module apply the Android Gradle Plugin. */
fun Project.androidTargetEnabled(): Boolean = kmpTargetEnabled("android")

/**
 * Declares the Android multiplatform library target only when [androidTargetEnabled]; otherwise it does
 * nothing and the Android Gradle Plugin is never applied. Call it inside `kotlin { }`.
 *
 * The plugin is applied to [project] first, then the target that AGP registers as the `android`
 * extension of the Kotlin extension gets [namespace], [compileSdk] and a JVM 17 target; [configure] runs last.
 *
 * Every AGP type is touched only from AndroidLibraryTarget.kt, which is reached solely when Android is
 * requested, so modules that never request it do not need AGP on their classpath. The default `configure`
 * is annotated with [JvmSerializableLambda] on purpose: that compiles it to a class rather than an
 * invokedynamic call site, and the call site would resolve the AGP receiver type even when Android is off.
 */
fun KotlinMultiplatformExtension.configureAndroidLibraryTargetIfEnabled(
    project: Project,
    namespace: String,
    compileSdk: Int = ANDROID_COMPILE_SDK,
    configure: KotlinMultiplatformAndroidLibraryTarget.() -> Unit = @JvmSerializableLambda {},
) {
    if (!project.androidTargetEnabled()) return
    applyAndroidLibraryTarget(project, namespace, compileSdk, configure)
}
