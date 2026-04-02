package com.sphereon.gradle.plugin

import dev.petuska.npm.publish.extension.NpmPublishExtension
import dev.petuska.npm.publish.extension.domain.json.PackageJson
import java.net.URI
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget

/**
 * Centralizes npm package publication for Sphereon IDK modules.
 *
 * Applies the JetBrains npm-publish plugin and configures it with:
 * - `@sphereon/idk-<projectName>` package naming (auto-derived from Gradle project name)
 * - npmjs registry with NPM_TOKEN env var authentication
 * - package.json enrichment for dual-target support (Node.js + browser + React Native)
 * - Conditional exports with `node`, `browser`, `react-native`, `types`, and `default` conditions
 * - TypeScript definition references
 * - Apache-2.0 license and repository metadata
 *
 * Usage in module build.gradle.kts:
 * ```kotlin
 * plugins {
 *     alias(sphereonplug.plugins.com.sphereon.gradle.plugin.npm.publication)
 * }
 * ```
 */
class NpmPublicationPlugin : Plugin<Project> {
    private val log = Logging.getLogger(NpmPublicationPlugin::class.java)

    override fun apply(project: Project) {
        // Create our extension with defaults
        val ext = project.extensions.create("npmPublication", NpmPublicationExtension::class.java).apply {
            packageName.convention("idk-${project.name}")
            scope.convention("@sphereon")
            enabled.convention(true)
            repositoryUrl.convention("https://github.com/sphereon-opensource/idk")
        }

        // Apply the JetBrains npm-publish plugin
        project.pluginManager.apply("org.jetbrains.kotlin.npm-publish")

        // Set outputModuleName eagerly so KGP picks it up for intermediate package.json.
        // Uses a lazy provider so the extension value resolves when needed.
        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            kmp.targets.configureEach(Action {
                // Only set scoped outputModuleName for JS targets, not wasmJs.
                // The wasmJs compiler doesn't create parent directories for scoped names
                // containing '/', causing FileNotFoundException for the .wasm output.
                if (this is KotlinJsIrTarget && this.platformType.name == "js") {
                    val scopedName = project.provider { "${ext.scope.get()}/${ext.packageName.get()}" }
                    outputModuleName.set(scopedName)
                }
            })
        }

        // Configure npm-publish extension after evaluation so all values are finalized
        project.afterEvaluate {
            if (!ext.enabled.get()) {
                log.info("npm publication disabled for ${project.name}")
                return@afterEvaluate
            }

            val npmScope = ext.scope.get()
            val npmPkgName = ext.packageName.get()
            val fullName = "$npmScope/$npmPkgName"
            val npmVersion = project.rootProject.extensions.extraProperties["npmVersion"] as String

            // The .mjs filename: Kotlin strips the scope prefix from outputModuleName
            // @sphereon/idk-lib-cbor-public -> idk-lib-cbor-public.mjs
            val entryFile = "./$npmPkgName.mjs"
            val typesFile = "./$npmPkgName.d.mts"

            log.info("Configuring npm publication: $fullName@$npmVersion")

            // Configure the npm-publish extension
            val npmPublish = project.extensions.getByType(NpmPublishExtension::class.java)

            // Registry: npmjs with token auth
            npmPublish.registries(Action {
                val registryName = "npmjs"
                if (names.contains(registryName)) {
                    named(registryName).configure {
                        uri.set(URI("https://registry.npmjs.org/"))
                        authToken.set(System.getenv("NPM_TOKEN") ?: "")
                    }
                } else {
                    register(registryName) {
                        uri.set(URI("https://registry.npmjs.org/"))
                        authToken.set(System.getenv("NPM_TOKEN") ?: "")
                    }
                }
            })

            fun configurePackageJson(pkgJson: PackageJson, entryMjs: String, typesMts: String) {
                pkgJson.apply {
                    name.set(fullName)
                    version.set(npmVersion)
                    types.set(typesMts)
                    license.set("Apache-2.0")
                    homepage.set("https://github.com/sphereon-opensource/idk")
                    keywords.addAll("sphereon", "idk", "identity", "kotlin-multiplatform", "esm", "typescript")

                    "type" by "module"

                    "exports" by json(Action {
                        "." by json(Action {
                            "types" by typesMts
                            "react-native" by entryMjs
                            "browser" by entryMjs
                            "node" by entryMjs
                            "default" by entryMjs
                        })
                    })

                    "repository" by json(Action {
                        "type" by "git"
                        "url" by "https://github.com/sphereon-opensource/idk"
                        "directory" by "lib/"
                    })
                    "bugs" by json(Action {
                        "url" by "https://github.com/sphereon-opensource/idk/issues"
                    })
                }
            }

            // Package configuration
            npmPublish.packages(Action {
                named("js").configure {
                    scope.set(npmScope)
                    packageName.set(npmPkgName)
                    packageJson(Action<PackageJson> { configurePackageJson(this, entryFile, typesFile) })
                }
            })

        }
    }
}
