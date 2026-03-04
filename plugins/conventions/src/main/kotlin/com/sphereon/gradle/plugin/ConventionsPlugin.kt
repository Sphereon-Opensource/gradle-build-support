package com.sphereon.gradle.plugin

import io.github.gradlenexus.publishplugin.NexusPublishExtension
import org.gradle.api.DomainObjectCollection
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

private const val COMPILE_KOTLIN = "compileKotlin"
lateinit var Logger: Logger
val optIns = listOf(
    "kotlin.js.ExperimentalJsExport",
    "kotlin.js.ExperimentalJsStatic",
    "kotlinx.serialization.ExperimentalSerializationApi",
    "kotlin.uuid.ExperimentalUuidApi",
    "kotlin.ExperimentalUnsignedTypes",
    "kotlin.time.ExperimentalTime",
    "com.github.michaelbull.result.annotation.UnsafeResultValueAccess",
    "com.github.michaelbull.result.annotation.UnsafeResultErrorAccess"
)

private fun log(message: String) = Logger.lifecycle("Sphereon: $message")

class ConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        Logger = project.logger
        var isApplied = false

        with(project) {
            if (project == project.rootProject) {
                // Apply Nexus publishing configuration to the root project
                // (includes subprojects maven-publish wiring)
                configureNexusPublishing()
            }

            // Hook into Multiplatform projects
            project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
                if (isApplied) { // Ensure configuration is only applied once
                    log("Kotlin Multiplatform conventions already applied to project: ${project.name}. Skipping.")
                    return@withId
                }

                isApplied = true
                log("Applying Kotlin Multiplatform conventions for project: ${project.name}")

                val kmp = extensions.findByType(KotlinMultiplatformExtension::class)
                if (kmp == null) {
                    Logger.info("Could not find Kotlin Multiplatform plugin for project: ${project.name}")
                    return@withId
                }
                with(kmp) {
                    configureKotlinMultiplatform()
                    commonOptIns()

                    // Configure all Kotlin/JS compilation tasks with additive compiler args
                    // Note: moduleKind and target must be set per-module in js { compilerOptions { } }
                    // because the KMP plugin overrides convention plugin settings at a later lifecycle point
                    project.tasks.withType<Kotlin2JsCompile>().configureEach {
                        compilerOptions {
                            freeCompilerArgs.add("-Xes-long-as-bigint")
                            freeCompilerArgs.add("-XXLanguage:+JsAllowLongInExportedDeclarations")
                            freeCompilerArgs.add("-Xenable-suspend-function-exporting")
                        }
                    }

                    targets.configureEach {
                        compilations.configureEach {
                            //                        setCommonCompilerOptions()
                        }
                        when (name) {
                            //                        "jvm" -> setJvmCompilerOptions()
                            "js", "wasmJs" -> {
                                setupNodeJsEnvironment()
                                (this as KotlinJsTargetDsl).useEsModules()
                            }
                        }
                    }

                    // kotlinx-io uses eval('require')('os') internally, which fails in ESM mode.
                    // Provide require to ESM modules via a CJS preload shim.
                    // See: https://github.com/Kotlin/kotlinx-io/issues/345
                    configureEsmRequireShim()

                    // KSP2: Auto-wire generated sources for non-JVM targets
                    configureKspSourceSets()
                }
            }
        }
    }

    /**
     * Ensures the project has the proper Apache 2.0 license.
     * Throws an exception if the license isn't Apache 2.0 or no license file is found.
     */
    private fun Project.ensureApacheLicense() {
        // Define the expected license file
        val licenseFile = project.file("LICENSE.md").takeIf { it.exists() }
            ?: project.file("LICENSE").takeIf { it.exists() }

        if (licenseFile == null) {
            throw GradleException("Publishing to Sonatype is not allowed: LICENSE.md or LICENSE file is missing.")
        }

        // Check if the license content mentions Apache 2.0
        val licenseContent = licenseFile.readText()
        val isApacheLicense = licenseContent.contains("Apache License", ignoreCase = true) &&
                licenseContent.contains("Version 2.0", ignoreCase = true)

        if (!isApacheLicense) {
            throw GradleException("Publishing to Sonatype is not allowed: The license is not Apache 2.0.")
        }
    }

    /**
     * Function to configure nexus-publish plugin for the provided root project.
     */
    private fun Project.configureNexusPublishing() {
        if (project != project.rootProject) {
            return
        }
        ensureApacheLicense()

        plugins.apply("io.github.gradle-nexus.publish-plugin") // Apply the Nexus plugin
        extensions.configure<NexusPublishExtension> {
            repositories {
                sonatype {
                    // Fetch Sonatype Nexus properties from gradle.properties or fallback defaults
                    nexusUrl.set(
                        uri(
                            project.findProperty("nexusUrl") as String?
                                ?: "https://s01.oss.sonatype.org/service/local/"
                        )
                    )
                    snapshotRepositoryUrl.set(
                        uri(
                            project.findProperty("snapshotRepositoryUrl") as String?
                                ?: "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                        )
                    )

                    // Credentials for connecting to Sonatype
                    username.set(findProperty("ossrhUsername") as String? ?: "")
                    password.set(findProperty("ossrhPassword") as String? ?: "")
                }
            }
        }

        // Configure subprojects
        project.subprojects {
            plugins.withId("maven-publish") {
                apply(plugin = "com.sphereon.gradle.plugin.project-publication")
            }
        }
    }
}

internal fun KotlinMultiplatformExtension.configureKotlinMultiplatform() {
    log("Applying Kotlin Multiplatform kotlin configuration to project: ${project.name}")
    val sphereonlib = project.sphereonlib

    apply {
        jvmToolchain(17)
        applyDefaultHierarchyTemplate()

        //common dependencies
        sourceSets.apply {
            commonMain {
                dependencies {
                    // TODO: Add BOMs here

                    // These deps are looked up in the target project catalogs! Which always happen to be the same in our case
                    implementation(sphereonlib.findLibrary("org.jetbrains.kotlin.stdlib").get())
                    implementation(sphereonlib.findLibrary("org.jetbrains.kotlinx.datetime").get())
//                    implementation(sphereonlib.findLibrary("org.jetbrains.kotlinx.serialization.cbor").get())
                    implementation(sphereonlib.findLibrary("org.jetbrains.kotlinx.serialization.json").get())
                    implementation(sphereonlib.findLibrary("org.jetbrains.kotlinx.coroutines.core").get())
                }
            }
            configureEach {
                compilerOptions {
                    optIn.addAll(optIns)
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                    freeCompilerArgs.addAll(optIns.map { "-opt-in=$it" })
//                    progressiveMode.set(true) //https://kotlinlang.org/docs/whatsnew13.html#progressive-mode
                    // Pinned to KOTLIN_2_0 for React Native / legacy external client compatibility
                    languageVersion.set(KotlinVersion.KOTLIN_2_0)
//                    apiVersion.set(KOTLIN_2_1)
                }
            }

            /* commonTest{
                 dependencies {
                     implementation(conventionLibs.findLibrary("kotlin-test").get())
                     implementation(kotlin("test"))
                 }
             }*/
        }
    }
}

private fun KotlinMultiplatformExtension.commonOptIns() {
    log("Applying default opt-ins to common source sets")

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
        optIn.addAll(optIns)

        // opt-in at the source-set level for IDE support
        sourceSets.all {
            optIns.forEach { languageSettings.optIn(it) }
        }
    }
    sourceSets.all {
        optIns.forEach { languageSettings.optIn(it) }
    }
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                    freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
                    freeCompilerArgs.addAll(optIns.map { "-opt-in=$it" })
                }
            }
        }
    }
}

private fun Project.setJvmCompilerOptions() {
    tasks.named<KotlinJvmCompile>(COMPILE_KOTLIN).configure {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

/**
 * Configures a CJS preload shim for JS/wasmJs test tasks.
 *
 * kotlinx-io uses `eval('require')('os')` internally, which fails in ESM mode because
 * `require` is not available in ES modules. This configures a preload script that provides
 * `require` on globalThis via Node.js `--require` flag.
 *
 * See: https://github.com/Kotlin/kotlinx-io/issues/345
 */
private fun KotlinMultiplatformExtension.configureEsmRequireShim() {
    val shimFile = project.rootProject.file("gradle-build-support/js/esm-require-shim.cjs")
    if (!shimFile.exists()) {
        log("ESM require shim not found at ${shimFile.absolutePath}, skipping")
        return
    }
    project.tasks.withType<KotlinJsTest>().configureEach {
        nodeJsArgs.add("--require")
        nodeJsArgs.add(shimFile.absolutePath)
    }
}

/**
 * KSP2 fix: Auto-configure srcDir for KSP-generated output on non-JVM targets.
 *
 * JVM targets automatically include KSP-generated sources, but JS, wasmJs, and
 * native targets require explicit srcDir configuration. Without this, test source
 * sets cannot see @ContributesBinding implementations discovered during main KSP,
 * causing empty @MergeComponent interfaces and missing provider errors.
 */
private fun KotlinMultiplatformExtension.configureKspSourceSets() {
    project.plugins.withId("com.google.devtools.ksp") {
        val kmpSourceSets = sourceSets
        targets.configureEach {
            val targetName = name
            // JVM handles KSP srcDir automatically; skip metadata pseudo-target too
            if (targetName == "metadata" || targetName == "jvm") return@configureEach

            kmpSourceSets.findByName("${targetName}Main")?.kotlin?.srcDir(
                project.layout.buildDirectory.dir("generated/ksp/$targetName/${targetName}Main/kotlin")
            )
            kmpSourceSets.findByName("${targetName}Test")?.kotlin?.apply {
                srcDir(project.layout.buildDirectory.dir("generated/ksp/$targetName/${targetName}Main/kotlin"))
                srcDir(project.layout.buildDirectory.dir("generated/ksp/$targetName/${targetName}Test/kotlin"))
            }
        }
    }
}

private fun Project.setupNodeJsEnvironment(): DomainObjectCollection<NodeJsPlugin> = plugins.withType<NodeJsPlugin> {
    with(the<NodeJsEnvSpec>()) {
        version.set("22.15.0")
        download.set(true)
        allowInsecureProtocol.set(false)
    }

    // kotlinNpmInstall runs at root project level — ensure root uses the same Node.js version
    if (this@setupNodeJsEnvironment != rootProject) {
        rootProject.plugins.withType<NodeJsPlugin> {
            with(rootProject.the<NodeJsEnvSpec>()) {
                version.set("22.15.0")
                download.set(true)
                allowInsecureProtocol.set(false)
            }
        }
    }

    plugins.withType<YarnPlugin> {
        // TODO: This operates on the root project, not on this project
        with(the<YarnRootEnvSpec>()) {
            download.set(true)
            yarnLockMismatchReport.set(YarnLockMismatchReport.WARNING) // NONE | FAIL
            reportNewYarnLock.set(false) // true
            yarnLockAutoReplace.set(true) // true
        }
    }
}
