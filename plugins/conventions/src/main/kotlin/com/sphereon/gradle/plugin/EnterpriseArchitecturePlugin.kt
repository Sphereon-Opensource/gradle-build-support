package com.sphereon.gradle.plugin

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * The single architecture policy used by EDK, IDK and the outer service assemblies.
 *
 * The policy is deliberately modelled as roles and capabilities. A module name is only useful
 * as diagnostic text; it is not an authorization mechanism. The task resolves the complete JVM
 * runtime graph and scans production source and Gradle dependency rules so exclusions cannot hide
 * an authority boundary violation.
 */
abstract class EnterpriseArchitectureExtension {
    abstract val moduleRole: Property<String>
    abstract val runtimeRole: Property<String>
    abstract val capabilities: SetProperty<String>
    abstract val enforce: Property<Boolean>
}

private val knownModuleRoles = setOf("library", "deployable", "service-assembly")
private val knownRuntimeRoles = setOf(
    "central-platform",
    "tenant-kms",
    "did-satellite",
    "tenant-as-satellite",
    "issuer-satellite",
    "verifier-satellite",
    "satellite-workload",
    "monolith",
)
private val knownCapabilities = setOf(
    "workload-execution",
    "central-authority",
    "remote-authority-adapter",
    "local-authority-adapter",
    "authority-persistence-postgresql",
    "runtime-persistence-postgresql",
    "platform-database-route",
    "tenant-database-route",
)

private fun runtimeFamily(runtimeRole: String): String = when (runtimeRole) {
    "central-platform" -> "platform"
    "monolith" -> "monolith"
    "tenant-kms", "did-satellite", "tenant-as-satellite", "issuer-satellite", "verifier-satellite",
    "satellite-workload" -> "satellite"
    else -> "unknown"
}

private fun componentId(file: File): String = file.invariantSeparatorsPath

abstract class EnterpriseArchitectureReportTask : DefaultTask() {
    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val moduleRole: Property<String>

    @get:Input
    abstract val runtimeRole: Property<String>

    @get:Input
    abstract val capabilities: SetProperty<String>

    @get:Input
    abstract val enforce: Property<Boolean>

    @get:Input
    abstract val resolvedRuntimeComponents: ListProperty<String>

    @get:Input
    abstract val productionGradleExclusions: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productionSourceFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun writeReport() {
        val role = runtimeRole.get()
        val module = moduleRole.get()
        val declaredCapabilities = capabilities.get().toSet()
        val family = runtimeFamily(role)
        val resolved = resolvedRuntimeComponents.get().distinct().sorted()
        val violations = mutableListOf<String>()

        if (role == "unspecified") {
            writeReportFile(
                projectPath.get(),
                module,
                role,
                declaredCapabilities,
                resolved,
                productionGradleExclusions.get(),
                emptyList(),
            )
            return
        }
        if (!enforce.get()) {
            violations += "architecture enforcement cannot be disabled for runtime role '$role'"
        }

        if (module != "unspecified" && module !in knownModuleRoles) {
            violations += "unknown enterprise module role '$module'"
        }
        if (role != "unspecified" && role !in knownRuntimeRoles) {
            violations += "unknown enterprise runtime role '$role'"
        }
        (declaredCapabilities - knownCapabilities).sorted().forEach {
            violations += "unknown enterprise capability '$it'"
        }

        val requiredCapabilities = when (family) {
            "platform" -> setOf(
                "central-authority",
                "authority-persistence-postgresql",
                "runtime-persistence-postgresql",
                "platform-database-route",
            )
            "satellite" -> setOf(
                "workload-execution",
                "remote-authority-adapter",
                "runtime-persistence-postgresql",
                "tenant-database-route",
            )
            "monolith" -> setOf(
                "workload-execution",
                "central-authority",
                "local-authority-adapter",
                "authority-persistence-postgresql",
                "runtime-persistence-postgresql",
                "platform-database-route",
                "tenant-database-route",
            )
            else -> emptySet()
        }
        (requiredCapabilities - declaredCapabilities).sorted().forEach {
            violations += "$role must declare capability '$it'"
        }

        val forbiddenCapabilities = when (family) {
            "platform" -> setOf("remote-authority-adapter")
            "satellite" -> setOf("central-authority", "local-authority-adapter", "authority-persistence-postgresql", "platform-database-route")
            "monolith" -> setOf("remote-authority-adapter")
            else -> emptySet()
        }
        (declaredCapabilities intersect forbiddenCapabilities).sorted().forEach {
            violations += if (it == "authority-persistence-postgresql") {
                "$role must not resolve authority persistence capability '$it'"
            } else {
                "$role must not declare capability '$it'"
            }
        }

        val forbiddenComponents = when (family) {
            "platform" -> listOf(
                "lib-conf-secret-management-satellite-remote",
                "lib-conf-secret-management-remote",
            )
            "satellite" -> listOf(
                "lib-conf-secret-management-central",
                "lib-conf-secret-management-authority-api",
                "lib-conf-secret-management-authority-persistence",
                "lib-conf-secret-management-persistence-authority",
                "lib-conf-secret-management-persistence-mysql",
                "secret-management-authority-persistence-postgresql",
            )
            "monolith" -> listOf("lib-conf-secret-management-satellite-remote", "lib-conf-secret-management-remote")
            else -> emptyList()
        }
        resolved.filter { component -> forbiddenComponents.any(component::contains) }.forEach {
            violations += "$role resolves forbidden enterprise component '$it'"
        }

        val deployableDependencyPatterns = listOf(
            "services-oauth2-as-rest",
            "services-kms-rest",
            "services-did-rest",
            "services-oid4vci-issuer-rest",
            "services-oid4vp-verifier-rest",
        )
        if (module == "library") {
            resolved.filter { component -> deployableDependencyPatterns.any(component::contains) }.forEach {
                violations += "library module must not resolve deployable component '$it'"
            }
        }

        productionGradleExclusions.get().forEach {
            violations += "Gradle exclusion: $it"
        }
        val metroExclusion = Regex(
            "(?s)@(?:[A-Za-z0-9_]+\\.)*DependencyGraph\\s*\\([^)]*?\\bexcludes\\s*=.*?\\]",
        )
        val sensitiveMetroExclusion = Regex(
            "(?i)(secret[-.]management|kmsresource|authority[-.]persistence|platform[-.]database|" +
                "KmsResourcePublicHandleDirectory|KmsResourceAuthorityDirectory)",
        )
        productionSourceFiles.files.sortedBy(::componentId).forEach { sourceFile ->
            val content = sourceFile.readText()
            metroExclusion.findAll(content).forEach { match ->
                if (sensitiveMetroExclusion.containsMatchIn(match.value)) {
                    val line = content.take(match.range.first).count { it == '\n' } + 1
                    violations += "Metro authority exclusion: ${sourceFile.relativeTo(project.projectDir).invariantSeparatorsPath}:$line"
                }
            }
            if (family == "satellite") {
                sourceFile.readLines().forEachIndexed { index, line ->
                    if (line.trimStart().startsWith("import ") &&
                        line.contains("KmsResourcePublicHandleDirectory")
                    ) {
                        violations += "${sourceFile.relativeTo(project.projectDir).invariantSeparatorsPath}:${index + 1}: " +
                            "authority persistence seam import KmsResourcePublicHandleDirectory"
                    }
                }
            }
        }

        writeReportFile(
            projectPath.get(),
            module,
            role,
            declaredCapabilities,
            resolved,
            productionGradleExclusions.get(),
            violations,
        )
    }

    private fun writeReportFile(
        project: String,
        module: String,
        role: String,
        declaredCapabilities: Set<String>,
        resolved: List<String>,
        exclusions: List<String>,
        violations: List<String>,
    ) {
        val report = linkedMapOf(
            "project" to project,
            "moduleRole" to module,
            "runtimeRole" to role,
            "capabilities" to declaredCapabilities.sorted(),
            "resolvedRuntimeComponents" to resolved,
            "productionGradleExclusions" to exclusions,
            "violations" to violations.distinct().sorted(),
        )
        val output = reportFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(report)) + "\n")
        if (violations.isNotEmpty()) {
            logger.lifecycle("$project: enterprise architecture violations: ${violations.distinct().sorted().joinToString()}")
        }
    }
}

abstract class EnterpriseArchitectureCheckTask : DefaultTask() {
    @get:Input
    abstract val projectPath: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val reportFile: ConfigurableFileCollection

    @TaskAction
    fun checkReport() {
        val report = reportFile.singleFile
        @Suppress("UNCHECKED_CAST")
        val violations = (groovy.json.JsonSlurper().parseText(report.readText()) as Map<String, Any>)
            .getValue("violations") as List<*>
        check(violations.isEmpty()) {
            "${projectPath.get()} resolves forbidden enterprise dependencies or capabilities: " +
                violations.joinToString() + ". Correct the introducing module; do not add an exclusion. " +
                "See ${report.invariantSeparatorsPath}."
        }
    }
}

class EnterpriseArchitecturePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<EnterpriseArchitectureExtension>("enterpriseArchitecture")
        extension.moduleRole.convention("unspecified")
        extension.runtimeRole.convention("unspecified")
        extension.capabilities.convention(emptySet())
        extension.enforce.convention(project.provider { extension.runtimeRole.get() != "unspecified" })

        val report = project.tasks.register<EnterpriseArchitectureReportTask>("enterpriseArchitectureReport") {
            group = "verification"
            description = "Write the role and capability dependency-boundary report"
            projectPath.set(project.path)
            moduleRole.set(extension.moduleRole)
            runtimeRole.set(extension.runtimeRole)
            capabilities.set(extension.capabilities)
            enforce.set(extension.enforce)
            reportFile.set(project.layout.buildDirectory.file("reports/enterprise-architecture/${project.name}.json"))
            productionSourceFiles.from(project.fileTree("src") {
                include("**/*.kt")
                exclude("**/*Test/**", "**/test/**")
            })
        }

        // OpenAPI REST modules copy generated sources into src before the architecture report
        // scans that tree. Keep the task graph explicit so Gradle 9 cannot observe the report
        // reading a producer's output without a declared dependency.
        report.configure {
            dependsOn(project.tasks.matching { it.name == "copyGeneratedSources" })
        }

        project.afterEvaluate {
            report.configure {
                resolvedRuntimeComponents.set(
                    (project.configurations.findByName("jvmRuntimeClasspath")
                        ?: project.configurations.findByName("runtimeClasspath"))?.let { runtime ->
                        project.provider {
                            runtime.incoming.resolutionResult.allComponents
                                .map { component ->
                                    component.moduleVersion?.let { module ->
                                        "${module.group}:${module.name}:${module.version}"
                                    } ?: component.id.displayName
                                }
                                .distinct()
                                .sorted()
                        }
                    } ?: project.provider { emptyList() },
                )
                productionGradleExclusions.set(
                    project.configurations
                        .filter {
                            it.name.equals("implementation", ignoreCase = true) ||
                                it.name.equals("runtimeOnly", ignoreCase = true) ||
                                it.name.endsWith("Implementation", ignoreCase = true) ||
                                it.name.endsWith("RuntimeOnly", ignoreCase = true)
                        }
                        .flatMap { configuration ->
                            val configurationRules = configuration.excludeRules.map { rule ->
                                "${configuration.name}:configuration:${rule.group ?: "*"}:${rule.module ?: "*"}"
                            }
                            val dependencyRules = configuration.dependencies
                                .filterIsInstance<ModuleDependency>()
                                .flatMap { dependency ->
                                    dependency.excludeRules.map { rule ->
                                        "${configuration.name}:${dependency.group ?: "*"}:${dependency.name}:" +
                                            "${rule.group ?: "*"}:${rule.module ?: "*"}"
                                    }
                                }
                            configurationRules + dependencyRules
                        }
                        .distinct()
                        .sorted(),
                )
            }
        }

        val check = project.tasks.register<EnterpriseArchitectureCheckTask>("enterpriseArchitectureCheck") {
            group = "verification"
            description = "Fail when a role resolves a forbidden enterprise capability"
            projectPath.set(project.path)
            reportFile.from(report.flatMap { it.reportFile })
            dependsOn(report)
        }
        project.tasks.matching { it.name == "check" }.configureEach {
            dependsOn(check)
        }
        project.tasks.matching {
            it.name.startsWith("compile") && !it.name.contains("Test", ignoreCase = true)
        }.configureEach {
            dependsOn(check)
        }
    }
}
