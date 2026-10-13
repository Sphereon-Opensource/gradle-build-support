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
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

private const val ANDROID_LIBRARY_PLUGIN_ID = "com.android.kotlin.multiplatform.library"

internal fun KotlinMultiplatformExtension.applyAndroidLibraryTarget(
    project: Project,
    namespace: String,
    compileSdk: Int,
    configure: (KotlinMultiplatformAndroidLibraryTarget.() -> Unit)?,
) {
    project.pluginManager.apply(ANDROID_LIBRARY_PLUGIN_ID)
    val androidTarget = (this as ExtensionAware).extensions
        .getByType(KotlinMultiplatformAndroidLibraryTarget::class.java)
    androidTarget.namespace = namespace
    androidTarget.compileSdk = compileSdk
    androidTarget.compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
    configure?.invoke(androidTarget)
}
