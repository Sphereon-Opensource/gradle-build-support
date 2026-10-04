package com.sphereon.gradle.plugin

import dev.petuska.npm.publish.extension.NpmPublishExtension
import dev.petuska.npm.publish.extension.domain.json.PackageJson
import dev.petuska.npm.publish.task.NpmPublishTask
import java.io.File
import java.net.URI
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBinaryMode
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.js.npm.PublicPackageJsonTask
import org.jetbrains.kotlin.gradle.targets.js.npm.npmProject

/**
 * Centralizes npm package publication for Sphereon IDK modules.
 *
 * Applies the JetBrains npm-publish plugin and configures it with:
 * - `@sphereon/idk-<projectName>` package naming (auto-derived from Gradle project name)
 * - npmjs registry with NPM_TOKEN env var authentication
 * - package.json enrichment for dual-target support (Node.js + browser + React Native)
 * - Conditional exports with `node`, `browser`, `react-native`, `types`, and `default` conditions
 * - TypeScript definition references
 * - Configurable SPDX license and repository metadata
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
            buildVersion.convention(project.providers.gradleProperty("buildVersion.${project.name}"))
            repositoryUrl.convention("https://github.com/sphereon-opensource/idk")
            license.convention("Apache-2.0")
        }

        // Skip npm-publish plugin entirely when no JS/wasmJs targets are configured
        val kmpTargets = (System.getProperty("kmp.targets") ?: "jvm").split(",").map { it.trim().lowercase() }
        val hasJsTargets = "all" in kmpTargets || "js" in kmpTargets || "wasmjs" in kmpTargets || "wasm" in kmpTargets
        if (!hasJsTargets) {
            log.info("Skipping npm-publish for ${project.name}: no JS/wasmJs targets (kmp.targets=${kmpTargets.joinToString(",")})")
            return
        }

        // Apply the JetBrains npm-publish plugin
        project.pluginManager.apply("org.jetbrains.kotlin.npm-publish")

        // Fix wasmJs main provider: the npm-publish plugin auto-registers a wasmJs package
        // but its main file has no value on Kotlin 2.3.x. Set a dummy value eagerly to prevent crash.
        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            try {
                val npmPublishExt = project.extensions.getByType(NpmPublishExtension::class.java)
                npmPublishExt.packages(Action {
                    try {
                        named("wasmJs").configure {
                            main.set("${project.name}.uninstantiated.mjs")
                        }
                    } catch (_: Exception) { }
                })
            } catch (_: Exception) { }
        }

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
            val baseVersion = project.version.toString()

            // The .mjs filename: Kotlin strips the scope prefix from outputModuleName
            // @sphereon/idk-lib-cbor-public -> idk-lib-cbor-public.mjs
            val entryFile = "./$npmPkgName.mjs"
            val typesFile = "./$npmPkgName.d.mts"

            log.info("Configuring npm publication: $fullName from component build version ($baseVersion)")

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

            fun configurePackageJson(pkgJson: PackageJson, entryMjs: String, typesMts: String,
                                     npmVersion: Provider<String>) {
                pkgJson.apply {
                    name.set(fullName)
                    version.set(npmVersion)
                    types.set(typesMts)
                    license.set(ext.license.get())
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

            // Package configuration (only when JS target is present)
            npmPublish.packages(Action {
                if (names.contains("js")) {
                    named("js").configure {
                        val pkg = this
                        val target = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
                            .targets.getByName("js") as KotlinJsIrTarget
                        val compilation = target.compilations.getByName("main")
                        val publicJson = project.tasks.named(compilation.npmProject.publicPackageJsonTaskName,
                            PublicPackageJsonTask::class.java)
                        val versionTask = project.tasks.register("generateNpmBuildVersion", GenerateNpmBuildVersion::class.java) {
                            this.baseVersion.set(baseVersion)
                            this.packageName.set(fullName)
                            buildVersion.set(ext.buildVersion)
                            versionFile.set(project.layout.buildDirectory.file("npm-build-version/version.txt"))
                            buildLogicInputs.from(File(NpmPublicationPlugin::class.java.protectionDomain.codeSource.location.toURI()))
                            // Hash the JS production build, including bundled dependencies and npm metadata.
                            // No source inventory, Git, CI counter or workspace launcher is required.
                            productionInputs.from(pkg.files,
                                pkg.readme.map { listOf(it.asFile) }.orElse(emptyList()),
                                pkg.npmIgnore.map { listOf(it.asFile) }.orElse(emptyList()),
                                pkg.packageJsonTemplateFile.map { listOf(it.asFile) }.orElse(emptyList()),
                                publicJson.map { it.packageJsonFile })
                            productionInputs.from(project.buildFile, project.rootProject.buildFile,
                                project.rootProject.fileTree("gradle") { exclude("**/build/**") })
                            productionInputs.from(project.rootProject.files("gradle.properties",
                                "platform-version.properties", "settings.gradle.kts", "settings.gradle")
                                .filter { it.isFile })
                            dependsOn(publicJson, project.tasks.named(compilation.processResourcesTaskName),
                                target.binaries.filter { it.mode == KotlinJsBinaryMode.PRODUCTION }.map { it.linkTask })
                        }
                        val npmVersion = versionTask.flatMap { it.versionFile }.map { it.asFile.readText().trim() }
                        scope.set(npmScope)
                        packageName.set(npmPkgName)
                        version.set(npmVersion)
                        packageJson(Action<PackageJson> { configurePackageJson(this, entryFile, typesFile, npmVersion) })
                        project.tasks.named("assembleJsPackage").configure { dependsOn(versionTask) }
                    }
                } else {
                    log.info("Skipping npm package configuration for ${project.name}: no JS target")
                }
            })

            // Route SNAPSHOT publishes to the `snapshot` dist-tag so that
            // `npm install @sphereon/idk-foo` (no version) keeps resolving to the last
            // released version rather than getting bumped to a snapshot on every CI run.
            // Released versions still publish to `latest` (the npm-publish default).
            if (baseVersion.endsWith("-SNAPSHOT")) {
                project.tasks.withType(NpmPublishTask::class.java).configureEach {
                    tag.set("snapshot")
                    onlyIf("This component build version is not already published", UnpublishedNpmBuild())
                }
            }
        }
    }
}
