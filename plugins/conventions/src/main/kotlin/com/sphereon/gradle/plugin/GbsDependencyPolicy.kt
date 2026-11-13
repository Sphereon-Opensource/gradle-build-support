package com.sphereon.gradle.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Every external library and its version come from the gradle-build-support BOMs, which reach a
 * build as the `sphereonlib` and `sphereonplug` catalogs. A dependency declared in a build script
 * or a repository-local catalog must name a module those catalogs list, and must not pin a
 * different version. That keeps the gbs BOMs a complete catalog of our third-party software, so
 * dependency overviews and vulnerability scans can be produced from gbs alone.
 *
 * `sphereon.dependencyPolicy` selects the mode:
 * - `fail` (default) stops the build at a declaration that breaks the policy, and logs a runtime
 *   classpath that resolves a gbs-pinned module to another version;
 * - `strict` also stops the build on such a resolved version;
 * - `report` only logs violations and records them for `gbsDependencyPolicyReport`;
 * - `off` disables the check.
 *
 * A resolved version can differ without any local declaration, when a library pulls a newer
 * release transitively, so by default that is a warning to act on, not a broken build. npm packages of Kotlin/JS targets are not Gradle module
 * dependencies and stay outside this policy, like the rest of the frontend toolchain.
 */
object GbsDependencyPolicy {
    const val PROPERTY = "sphereon.dependencyPolicy"
    const val DEFAULT_MODE = "fail"
    val MODES = setOf("fail", "strict", "report", "off")
    val CATALOGS = listOf("sphereonlib", "sphereonplug")

    /** Added by the Kotlin and Android plugins themselves, aligned with the toolchain version. */
    private val TOOLCHAIN_IMPLICIT = setOf(
        "org.jetbrains.kotlin:kotlin-stdlib",
        "org.jetbrains.kotlin:kotlin-stdlib-common",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk7",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8",
        "org.jetbrains.kotlin:kotlin-stdlib-js",
        "org.jetbrains.kotlin:kotlin-stdlib-wasm-js",
        "org.jetbrains.kotlin:kotlin-test",
        "org.jetbrains.kotlin:kotlin-test-common",
        "org.jetbrains.kotlin:kotlin-test-annotations-common",
        "org.jetbrains.kotlin:kotlin-test-junit",
        "org.jetbrains.kotlin:kotlin-test-junit5",
        "org.jetbrains.kotlin:kotlin-test-js",
        "org.jetbrains.kotlin:kotlin-test-wasm-js",
        "org.jetbrains.kotlin:kotlin-dom-api-compat",
        "org.jetbrains.kotlin:kotlin-bom",
    )

    /** The violation for one declared dependency, or null when it complies. */
    fun evaluate(group: String?, name: String, version: String?, approved: Map<String, String?>): String? {
        if (group == null || group.startsWith("com.sphereon")) return null
        val module = "$group:$name"
        if (module in TOOLCHAIN_IMPLICIT) return null
        if (module !in approved) return "$module is not listed in the gradle-build-support BOMs; add it there and use the sphereonlib catalog"
        val expected = approved[module]
        if (!version.isNullOrBlank() && expected != null && version != expected) {
            return "$module:$version differs from gradle-build-support ($expected); drop the local version and use the sphereonlib catalog"
        }
        return null
    }

    /** A resolved module that gbs pins must resolve to that pin; unlisted transitives are not judged here. */
    fun evaluateResolved(group: String, name: String, version: String, approved: Map<String, String?>): String? {
        if (group.startsWith("com.sphereon")) return null
        val module = "$group:$name"
        val expected = approved[module] ?: return null
        return if (version != expected) "$module resolved to $version but gradle-build-support pins $expected" else null
    }
}

/**
 * Applies the dependency policy to this project. The conventions plugin calls it for every product
 * module; build logic outside gradle-build-support, such as the infrastructure service conventions,
 * calls it for its own modules so they follow the same rule.
 */
fun Project.configureGbsDependencyPolicy() {
    val mode = providers.gradleProperty(GbsDependencyPolicy.PROPERTY).orElse(GbsDependencyPolicy.DEFAULT_MODE).get()
    require(mode in GbsDependencyPolicy.MODES) { "${GbsDependencyPolicy.PROPERTY} must be one of ${GbsDependencyPolicy.MODES}, not '$mode'" }
    if (mode == "off") return
    val failOnDeclaration = mode == "fail" || mode == "strict"
    val catalogs = extensions.findByType(VersionCatalogsExtension::class.java) ?: return
    if (GbsDependencyPolicy.CATALOGS.none { catalogs.find(it).isPresent }) return
    val approved: Map<String, String?> by lazy {
        buildMap {
            for (name in GbsDependencyPolicy.CATALOGS) {
                val catalog = catalogs.find(name).orElse(null) ?: continue
                for (alias in catalog.libraryAliases) {
                    val dependency = catalog.findLibrary(alias).get().get()
                    put("${dependency.module.group}:${dependency.module.name}", dependency.versionConstraint.requiredVersion.ifBlank { null })
                }
            }
        }
    }
    val violations = sortedSetOf<String>()
    configurations.configureEach {
        // Only the buckets build scripts declare into; resolvable and tooling configurations are
        // filled by plugins (compiler classpaths, native distributions) and are not our choice.
        if (!isCanBeDeclared || isCanBeResolved || isCanBeConsumed) return@configureEach
        val configurationName = name
        dependencies.configureEach {
            if (this !is ExternalModuleDependency) return@configureEach
            val violation = GbsDependencyPolicy.evaluate(group, name, version, approved) ?: return@configureEach
            val entry = "$path $configurationName: $violation"
            if (failOnDeclaration) throw GradleException("Dependency policy: $entry")
            if (violations.add(entry)) logger.warn("w: Dependency policy: $entry")
        }
    }
    // A declaration can comply while resolution still picks another version, for example when a
    // library pulls a newer one transitively. Shipped runtime classpaths must resolve to the gbs pin.
    configurations.configureEach {
        if (!isCanBeResolved || !name.endsWith("RuntimeClasspath") || name.contains("Test", ignoreCase = true)) return@configureEach
        val configurationName = name
        val projectPath = path
        // A plain map and strings only: the listener must not hold the project or its catalogs.
        val pins = HashMap(approved)
        val failOnResolved = mode == "strict"
        val policyLogger = logger
        incoming.afterResolve {
            for (component in resolutionResult.allComponents) {
                val id = component.moduleVersion ?: continue
                val violation = GbsDependencyPolicy.evaluateResolved(id.group, id.name, id.version, pins) ?: continue
                val entry = "$projectPath $configurationName: $violation"
                if (failOnResolved) throw GradleException("Dependency policy: $entry")
                policyLogger.warn("w: Dependency policy: $entry")
            }
        }
    }
    tasks.register("gbsDependencyPolicyReport", GbsDependencyPolicyReportTask::class.java) {
        group = "verification"
        description = "Writes the dependencies of ${project.path} that are not taken from gradle-build-support"
        projectPath.set(path)
        this.violations.set(provider { violations.toList() })
        report.set(layout.buildDirectory.file("reports/gbs-dependency-policy.txt"))
    }
}

@DisableCachingByDefault(because = "Writes a small report of configuration-time findings")
abstract class GbsDependencyPolicyReportTask : DefaultTask() {
    @get:Input abstract val projectPath: Property<String>
    @get:Input abstract val violations: ListProperty<String>
    @get:OutputFile abstract val report: RegularFileProperty

    @TaskAction
    fun write() {
        val lines = violations.get()
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(lines.joinToString("\n", postfix = if (lines.isEmpty()) "" else "\n"))
        logger.lifecycle("${lines.size} dependency policy violation(s) in ${projectPath.get()}")
    }
}
