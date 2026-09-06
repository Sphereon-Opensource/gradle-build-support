package com.sphereon.gradle.plugin

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Minimum Android API level for every Sphereon module.
 *
 * This is the single place the floor is declared. A module that genuinely needs a higher level
 * sets `minSdk` in its own Android block, which takes precedence; every other module inherits
 * this value and declares nothing.
 */
const val ANDROID_MIN_SDK = 27

private val ANDROID_PLUGIN_IDS = listOf(
    "com.android.application",
    "com.android.library",
    "com.android.kotlin.multiplatform.library",
)

/**
 * Applies [ANDROID_MIN_SDK] to whichever Android plugin a module uses: the application plugin,
 * the classic library plugin, or the Kotlin Multiplatform library plugin, whose Android settings
 * live on a Kotlin target rather than on a project extension.
 *
 * A module that applies an Android plugin the floor could not be attached to is reported, so a
 * module silently falling back to the Android default is a visible failure rather than a quiet one.
 */
internal fun Project.configureAndroidConventions() {
    var reached = false

    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            reached = true
            if (defaultConfig.minSdk == null) {
                defaultConfig.minSdk = ANDROID_MIN_SDK
                logAndroidMinSdk("application")
            }
        }
    }

    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> {
            reached = true
            if (defaultConfig.minSdk == null) {
                defaultConfig.minSdk = ANDROID_MIN_SDK
                logAndroidMinSdk("library")
            }
        }
    }

    // Nested so the Kotlin extension is guaranteed to exist regardless of the order the two
    // plugins appear in a module's plugins block.
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        plugins.withId("com.android.kotlin.multiplatform.library") {
            // `all` rather than `configureEach`: the floor has to be in place while the module's
            // own kotlin block is still being evaluated, not deferred to task graph creation.
            extensions.getByType<KotlinMultiplatformExtension>()
                .targets
                .withType<KotlinMultiplatformAndroidLibraryTarget>()
                .all {
                    reached = true
                    if (minSdk == null) {
                        minSdk = ANDROID_MIN_SDK
                        logAndroidMinSdk("multiplatform library")
                    }
                }
        }
    }

    afterEvaluate {
        val applied = ANDROID_PLUGIN_IDS.filter { plugins.hasPlugin(it) }
        if (!reached && applied.isNotEmpty()) {
            logger.warn(
                "Sphereon: $name applies ${applied.joinToString()} but minSdk $ANDROID_MIN_SDK " +
                    "could not be applied, so it falls back to the Android default.",
            )
        }
    }
}

private fun Project.logAndroidMinSdk(kind: String) =
    logger.lifecycle("Sphereon: Applying Android minSdk $ANDROID_MIN_SDK to $kind: $name")
