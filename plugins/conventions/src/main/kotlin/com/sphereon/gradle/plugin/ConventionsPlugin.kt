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
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.gradle.api.file.ConfigurableFileCollection
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

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

                    // wasmJs npm-publish workaround (Kotlin 2.3.x mainFile provider bug)
                    configureWasmJsNpmPublishWorkaround()
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
 * native targets require explicit srcDir configuration.
 *
 * **Why main KSP is added to test source sets:**
 * KSP2 on non-JVM targets cannot discover `@ContributesBinding` implementations from
 * compiled dependencies (KLIBs / JS output). The `@MergeComponent` processor needs to
 * see main KSP-generated `@Origin` interfaces as SOURCE files to merge them into the
 * test component. Without this, `TestAppComponentMerged` is empty and kotlin-inject
 * fails with "Cannot find an @Inject constructor or provider" errors.
 *
 * **Compilation fix (all non-JVM targets):**
 * Adding main KSP sources to the test source set causes compile-time conflicts:
 * - Native: `-Xfragment-sources` conflicts — files get tagged as test-fragment
 *   sources, but the same classes already exist in the main KLIB.
 * - JS/wasmJs: Redeclaration errors — Amazon App Platform code generators
 *   produce `ScopedComponent` wrappers in both main and test KSP output with
 *   identical FQNs. When main KSP is added to the test source set, both copies
 *   are visible to the compiler.
 *
 * Fix: For ALL non-JVM test compile tasks, reconstruct `sources` from source
 * sets while excluding the main KSP directory. KSP has already processed them;
 * the compiled classes come from the main dependency at link/module-import time.
 */
private fun KotlinMultiplatformExtension.configureKspSourceSets() {
    project.plugins.withId("com.google.devtools.ksp") {
        val kmpSourceSets = sourceSets
        targets.configureEach {
            val targetName = name
            // JVM handles KSP srcDir automatically; skip metadata pseudo-target too
            if (targetName == "metadata" || targetName == "jvm") return@configureEach

            val mainKspDir = project.layout.buildDirectory.dir("generated/ksp/$targetName/${targetName}Main/kotlin")
            val testKspDir = project.layout.buildDirectory.dir("generated/ksp/$targetName/${targetName}Test/kotlin")

            // Main source set: own KSP output
            kmpSourceSets.findByName("${targetName}Main")?.kotlin?.srcDir(mainKspDir)

            // Test source set: own KSP output + main KSP output (for KSP discovery)
            kmpSourceSets.findByName("${targetName}Test")?.kotlin?.srcDir(testKspDir)
            kmpSourceSets.findByName("${targetName}Test")?.kotlin?.srcDir(mainKspDir)

            // All non-JVM targets: exclude main KSP output from the test compile task.
            // This includes both intra-module KSP output (this module's own main KSP dir)
            // AND cross-module KSP output (added via srcDir from dependency modules).
            // Native: avoids -Xfragment-sources duplicate class conflicts.
            // JS/wasmJs: avoids Redeclaration errors when test KSP regenerates
            //   the same ScopedComponent wrappers that exist in main KSP output.
            compilations.matching { it.name == "test" }.configureEach {
                val compilation = this
                compileTaskProvider.configure {
                    val task = this
                    if (task is KotlinNativeCompile || task is Kotlin2JsCompile) {
                        // Reconstruct sources from all associated source sets,
                        // excluding files under ANY KSP main output directory
                        // (own module or cross-module dependencies).
                        val filteredSources = project.objects.fileCollection()
                        compilation.allKotlinSourceSets.forEach { sourceSet ->
                            filteredSources.from(sourceSet.kotlin.filter { file ->
                                val path = file.absolutePath.replace('\\', '/')
                                // Exclude any file under a KSP main output directory
                                // Pattern: .../generated/ksp/<target>/<target>Main/kotlin/...
                                !path.contains("/generated/ksp/") || !path.contains("Main/kotlin")
                            })
                        }
                        when (task) {
                            // KGP declares `sources` as FileCollection (read-only API),
                            // but the runtime type is ConfigurableFileCollection.
                            is KotlinNativeCompile -> (task.sources as? ConfigurableFileCollection)?.setFrom(filteredSources)
                            // Kotlin2JsCompile inherits setSource() from AbstractKotlinCompileTool
                            // which replaces the internal sourceFiles backing the sources property.
                            is Kotlin2JsCompile -> task.setSource(filteredSources)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Standard KMP target set: jvm, js (browser + nodejs), wasmJs (browser + nodejs), iOS, linuxX64.
 *
 * Modules opt-in by calling this instead of declaring targets manually.
 * Modules needing custom targets (BLE, NFC, SQLite, AWS, Compose, mobile-only) keep their own declarations.
 *
 * JS/wasmJs are configured with ES modules, BigInt, and TypeScript generation.
 * moduleKind and target are set here because the KMP plugin overrides convention plugin
 * task-level settings at a later lifecycle point — they must be in the js {} block.
 */
@OptIn(ExperimentalWasmDsl::class)
fun KotlinMultiplatformExtension.configureStandardTargets() {
    jvm()
    js {
        compilerOptions {
            moduleKind.set(JsModuleKind.MODULE_ES)
            target.set("es2015")
        }
        browser {
            testTask { useMocha { timeout = "60000" } }
        }
        nodejs {
            testTask { useMocha { timeout = "60000" } }
        }
        binaries.library()
        generateTypeScriptDefinitions()
    }
    wasmJs {
        browser()
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()
}

/**
 * Automatically adds KSP dependencies (kotlin-inject compiler, Amazon kotlin-inject-anvil
 * contribute processors, and Anvil KSP compiler) to all KSP configurations for whatever
 * targets the module declares — including test configurations.
 *
 * Replaces the verbose per-target `addKspDependencies("kspFoo")` pattern duplicated across 72+ modules.
 *
 * Usage in build.gradle.kts:
 * ```
 * dependencies {
 *     addKspDependenciesForAllTargets()
 * }
 * ```
 */
fun Project.addKspDependenciesForAllTargets() {
    val projectLibs = libs
    val deps = dependencies
    configurations.configureEach {
        val configName = name
        if (configName.startsWith("ksp") && configName.length > 3 && !configName.contains("Classpath")) {
            deps.addProvider(configName, projectLibs.findLibrary("kotlin.inject.compiler.ksp").get())
            deps.addProvider(configName, projectLibs.findLibrary("amz.kotlin.inject.contribute.public").get())
            deps.addProvider(configName, projectLibs.findLibrary("amz.kotlin.inject.contribute.code.generators").get())
            deps.addProvider(configName, projectLibs.findLibrary("anvil.compiler.ksp").get())
        }
    }
}

/**
 * Adds KSP-generated source directories from dependency modules to this module's
 * non-JVM main source sets, so that `@MergeComponent` in this module (including test
 * components) can discover `@ContributesBinding` and `@Origin` interfaces from those dependencies.
 *
 * This is needed because KSP2 on non-JVM targets cannot discover `@ContributesBinding`
 * implementations from compiled KLIBs/JS output — it needs the `@Origin` interfaces as
 * source files. JVM doesn't need this because KSP can scan compiled JARs.
 *
 * Usage in build.gradle.kts:
 * ```
 * kotlin {
 *     addCrossModuleKspSources(project(":lib-core-api-default"))
 *     addCrossModuleKspSources(project(":lib-crypto-core-impl"))
 * }
 * ```
 */
fun KotlinMultiplatformExtension.addCrossModuleKspSources(vararg depProjects: Project) {
    val nonJvmTargets = targets.filter { it.name != "jvm" && it.name != "metadata" }.map { it.name }
    for (depProject in depProjects) {
        for (target in nonJvmTargets) {
            val kspDir = depProject.layout.buildDirectory.dir("generated/ksp/$target/${target}Main/kotlin")
            sourceSets.findByName("${target}Main")?.kotlin?.srcDir(kspDir)
        }
    }
}

/**
 * Workaround for broken wasmJs npm-publish tasks on Kotlin 2.3.x.
 *
 * The JetBrains npm-publish plugin's `mainFile` provider has no value for wasmJs targets,
 * causing assembleWasmJsPackage, packWasmJsPackage, and publishWasmJsPackageToNpmjsRegistry
 * to fail. This replaces those tasks with no-ops.
 *
 * Auto-applied when both the npm-publish plugin and a wasmJs target are present.
 * Consolidates the identical workaround previously duplicated in 6 modules.
 */
private fun KotlinMultiplatformExtension.configureWasmJsNpmPublishWorkaround() {
    // Only apply if npm-publish plugin is applied.
    // The wasmJs target check is deferred to afterEvaluate because this runs during
    // plugin application, before the module's kotlin {} block declares targets.
    project.plugins.withId("org.jetbrains.kotlin.npm.publish") {
        project.afterEvaluate {
            val hasWasmJs = targets.any { it.name == "wasmJs" }
            if (!hasWasmJs) return@afterEvaluate

            listOf("assembleWasmJsPackage", "packWasmJsPackage", "publishWasmJsPackageToNpmjsRegistry").forEach { taskName ->
                try { tasks.replace(taskName) } catch (_: Exception) {}
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
