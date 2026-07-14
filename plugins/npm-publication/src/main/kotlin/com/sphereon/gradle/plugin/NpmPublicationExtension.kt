package com.sphereon.gradle.plugin

import org.gradle.api.provider.Property

/**
 * Extension for configuring npm package publication.
 *
 * Default npm package name is `idk-${project.name}`, published under `@sphereon` scope.
 * Modules can override these defaults:
 * ```kotlin
 * npmPublication {
 *     packageName.set("idk-my-custom-name")
 *     license.set("AGPL-3.0-only")
 * }
 * ```
 */
interface NpmPublicationExtension {
    /** Override the auto-derived npm package name. Default: `idk-${project.name}` */
    val packageName: Property<String>

    /** Override the npm scope. Default: `@sphereon` */
    val scope: Property<String>

    /** Set to false to disable npm publishing for this module. Default: true */
    val enabled: Property<Boolean>

    /** Repository URL for package.json metadata. Default: `https://github.com/nicolo-ribeiro/npm-publish` is overridden */
    val repositoryUrl: Property<String>

    /** SPDX license expression written to the publishable package.json. Default: `Apache-2.0`. */
    val license: Property<String>
}
