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
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

private const val HOST_CHECK_TASK = "checkLinuxX64TestHost"

/** Android compile SDK used by [configureAndroidLibraryTargetIfEnabled] unless a module overrides it. */
internal const val ANDROID_COMPILE_SDK = 36

/** True when iOS targets must be declared: `ios` named on a macOS host, or `all` on a macOS host. */
fun Project.iosTargetsEnabled(): Boolean = kmpTargetEnabled("ios")

/**
 * True when the linuxX64 target must be declared. Kotlin/Native cross-compiles and links linuxX64 on every host;
 * only running `linuxX64Test` needs Linux x86_64, which [gateLinuxX64TestExecution] enforces.
 */
internal fun Project.linuxX64TargetEnabled(): Boolean = kmpTargetEnabled("native")

/**
 * Off Linux x86_64, `linuxX64Test` cannot run. When `native` was named the task fails with the host in the
 * message, so a requested test run is never reported as passed; when only `all` asked for it, the task is
 * disabled and one line per build says so.
 */
internal fun Project.gateLinuxX64TestExecution() {
    val service = kmpTargetsService()
    if (service.host.isLinuxX64) return
    val message = "linuxX64Test needs a Linux x86_64 host; this host is ${service.host.description}"
    val named = KmpTarget.NATIVE in service.selection.named
    if (!named) service.logOnce(logger, "$message. Skipped for kmp.targets=all.")
    // A dependency, not a doFirst: a test task without a test binary is skipped as NO-SOURCE before its actions run.
    val hostCheck = if (named) {
        if (HOST_CHECK_TASK in tasks.names) return
        tasks.register(HOST_CHECK_TASK) {
            group = "verification"
            description = "Fails: linuxX64Test was requested on a host that cannot run it"
            doLast { throw GradleException(message) }
        }
    } else {
        null
    }
    tasks.withType(KotlinNativeTest::class.java).configureEach {
        if (name == "linuxX64Test") {
            if (hostCheck != null) dependsOn(hostCheck) else enabled = false
        }
    }
}

/**
 * Declares the Android multiplatform library target only when [androidTargetEnabled]; otherwise it does
 * nothing and the Android Gradle Plugin is never applied. Call it inside `kotlin { }`. `kmp.targets=all` does
 * not include Android: it needs an SDK, so `android` must be named.
 *
 * The plugin is applied to [project] first, then the target that AGP registers as the `android`
 * extension of the Kotlin extension gets [namespace], [compileSdk] and a JVM 17 target; [configure] runs last.
 *
 * Every AGP type is touched at runtime only from AndroidLibraryTarget.kt, which is reached solely when Android
 * is requested, so AGP classes are never loaded for a build that does not request it. The signature still names
 * [KotlinMultiplatformAndroidLibraryTarget], so every build script that calls this helper needs AGP on its
 * script compile classpath (for example the root project's `plugins { ... apply false }` block).
 */
fun KotlinMultiplatformExtension.configureAndroidLibraryTargetIfEnabled(
    project: Project,
    namespace: String,
    compileSdk: Int = ANDROID_COMPILE_SDK,
    configure: (KotlinMultiplatformAndroidLibraryTarget.() -> Unit)? = null,
) {
    if (!project.androidTargetEnabled()) return
    applyAndroidLibraryTarget(project, namespace, compileSdk, configure)
}
