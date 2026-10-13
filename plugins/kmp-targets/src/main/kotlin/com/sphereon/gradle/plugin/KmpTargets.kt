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

import java.util.concurrent.ConcurrentHashMap
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

private const val SERVICE_NAME = "sphereonKmpTargets"

/**
 * Holds the one parse of `kmp.targets` for a build, read through Gradle's providers so the configuration cache
 * tracks it, and remembers which "target dropped" lines were already logged. Projects reach it through
 * [kmpTargetSelection] and [kmpTargetEnabled]; it keeps no state on the root project, so it works with
 * isolated projects.
 */
abstract class KmpTargetsService : BuildService<KmpTargetsService.Params> {
    interface Params : BuildServiceParameters {
        val targets: Property<String>
        val osName: Property<String>
        val osArch: Property<String>
    }

    val selection: KmpTargetSelection by lazy { KmpTargetSelection.parse(parameters.targets.orNull) }
    val host: KmpHost by lazy { KmpHost(parameters.osName.getOrElse(""), parameters.osArch.getOrElse("")) }
    private val logged = ConcurrentHashMap.newKeySet<String>()

    /** Logs [message] at lifecycle level the first time any project in the build reports it. */
    fun logOnce(logger: Logger, message: String) {
        if (logged.add(message)) logger.lifecycle(message)
    }

    /** See [KmpTargetSelection.enabledOn]; targets that `all` drops on this host are logged once per build. */
    fun enabled(target: KmpTarget, logger: Logger): Boolean =
        selection.enabledOn(host, target) { message -> logOnce(logger, message) }
}

/** The build's [KmpTargetsService]. */
fun Project.kmpTargetsService(): KmpTargetsService =
    gradle.sharedServices.registerIfAbsent(SERVICE_NAME, KmpTargetsService::class.java) {
        parameters.targets.set(providers.systemProperty(KmpTargetSelection.PROPERTY))
        parameters.osName.set(providers.systemProperty("os.name"))
        parameters.osArch.set(providers.systemProperty("os.arch"))
    }.get()

/** The parsed `kmp.targets` of this build. */
fun Project.kmpTargetSelection(): KmpTargetSelection = kmpTargetsService().selection

/**
 * True when the target named by [token] (`js`, `wasmJs`, `native`, `android`, `ios`, or an alias) must be
 * declared in this build on this host. A named target the host cannot build fails the build; see
 * [KmpTargetSelection.enabledOn].
 */
fun Project.kmpTargetEnabled(token: String): Boolean =
    kmpTargetsService().enabled(KmpTargetSelection.target(token), logger)

/** True when `android` was named; only then may a module apply the Android Gradle Plugin. `all` does not include it. */
fun Project.androidTargetEnabled(): Boolean = kmpTargetEnabled("android")

private fun ProviderFactory.kmpTargetSelection(): KmpTargetSelection =
    KmpTargetSelection.parse(systemProperty(KmpTargetSelection.PROPERTY).orNull)

/**
 * The parsed `kmp.targets`, for settings scripts that include modules per target, for example an Android
 * application only when `android` is named. Apply the `com.sphereon.gradle.plugin.kmp-targets` settings plugin
 * to put this function on the settings classpath.
 */
fun Settings.kmpTargetSelection(): KmpTargetSelection = providers.kmpTargetSelection()

/** Settings counterpart of [Project.kmpTargetEnabled], with the same host checks. */
fun Settings.kmpTargetEnabled(token: String): Boolean =
    providers.kmpTargetSelection().enabledOn(
        KmpHost(providers.systemProperty("os.name").getOrElse(""), providers.systemProperty("os.arch").getOrElse("")),
        KmpTargetSelection.target(token),
    )

/**
 * Settings plugin that validates `kmp.targets` before any project is configured, so a typo fails at once,
 * and puts [kmpTargetSelection] and [kmpTargetEnabled] on the settings script classpath.
 */
class KmpTargetsSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        settings.kmpTargetSelection()
    }
}
