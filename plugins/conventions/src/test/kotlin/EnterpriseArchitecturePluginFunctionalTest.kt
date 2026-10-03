package com.sphereon.gradle.plugin

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.io.TempDir

class EnterpriseArchitecturePluginFunctionalTest {
    @TempDir
    lateinit var projectDirectory: Path

    @Test
    fun satelliteAuthorityPersistenceCapabilityIsRejected() {
        writeFixture(
            """
            plugins {
                id("java-library")
                id("com.sphereon.gradle.plugin.enterprise-architecture")
            }

            enterpriseArchitecture {
                moduleRole.set("deployable")
                runtimeRole.set("satellite-workload")
                capabilities.set(setOf(
                    "workload-execution",
                    "remote-authority-adapter",
                    "runtime-persistence-postgresql",
                    "tenant-database-route"
                ))
            }

            dependencies {
                implementation(project(":secret-management-authority-persistence-postgresql"))
            }
            """.trimIndent(),
            subproject = "secret-management-authority-persistence-postgresql",
        )

        val failure = assertFailsWith<UnexpectedBuildFailure> {
            runGate()
        }

        assertContains(failure.buildResult.output, "satellite-workload resolves forbidden enterprise component")
    }

    @Test
    fun unknownRuntimeRoleIsRejected() {
        writeFixture(
            """
            plugins {
                id("java-library")
                id("com.sphereon.gradle.plugin.enterprise-architecture")
            }

            enterpriseArchitecture {
                moduleRole.set("deployable")
                runtimeRole.set("unreviewed-role")
                capabilities.set(setOf("workload-execution"))
            }
            """.trimIndent(),
        )

        val failure = assertFailsWith<UnexpectedBuildFailure> {
            runGate()
        }

        assertContains(failure.buildResult.output, "unknown enterprise runtime role 'unreviewed-role'")
    }

    @Test
    fun satelliteAuthorityPersistenceImportIsRejectedAtTheCompilationBoundary() {
        writeFixture(
            """
            plugins {
                id("java-library")
                id("com.sphereon.gradle.plugin.enterprise-architecture")
            }

            enterpriseArchitecture {
                moduleRole.set("library")
                runtimeRole.set("satellite-workload")
                capabilities.set(setOf(
                    "workload-execution",
                    "remote-authority-adapter",
                    "runtime-persistence-postgresql",
                    "tenant-database-route"
                ))
            }
            """.trimIndent(),
            source = """
                package fixture

                import com.sphereon.edk.secretmanagement.KmsResourcePublicHandleDirectory

                class ForbiddenImport
            """.trimIndent(),
        )

        val failure = assertFailsWith<UnexpectedBuildFailure> {
            runGate()
        }

        assertContains(failure.buildResult.output, "authority persistence seam import KmsResourcePublicHandleDirectory")
    }

    @Test
    fun satelliteDependencyExclusionsCannotHideTheBoundary() {
        writeFixture(
            """
            plugins {
                id("java-library")
                id("com.sphereon.gradle.plugin.enterprise-architecture")
            }

            enterpriseArchitecture {
                moduleRole.set("library")
                runtimeRole.set("satellite-workload")
                capabilities.set(setOf(
                    "workload-execution",
                    "remote-authority-adapter",
                    "runtime-persistence-postgresql",
                    "tenant-database-route"
                ))
            }

            dependencies {
                implementation(project(":secret-management-authority-persistence-postgresql")) {
                    exclude(group = "example", module = "pretend-safe")
                }
            }
            """.trimIndent(),
            subproject = "secret-management-authority-persistence-postgresql",
        )

        val failure = assertFailsWith<UnexpectedBuildFailure> {
            runGate()
        }

        assertContains(failure.buildResult.output, "Gradle exclusion")
    }

    @Test
    fun satelliteSensitiveMetroExclusionsCannotHideTheBoundary() {
        writeFixture(
            """
            plugins {
                id("java-library")
                id("com.sphereon.gradle.plugin.enterprise-architecture")
            }

            enterpriseArchitecture {
                moduleRole.set("library")
                runtimeRole.set("satellite-workload")
                capabilities.set(setOf(
                    "workload-execution",
                    "remote-authority-adapter",
                    "runtime-persistence-postgresql",
                    "tenant-database-route"
                ))
            }
            """.trimIndent(),
            source = """
                package fixture

                @dev.zacsweers.metro.DependencyGraph(
                    excludes = [com.sphereon.conf.secret.management.runtime.KmsResourceAuthorityDirectory::class]
                )
                class ForbiddenGraphExclusion
            """.trimIndent(),
        )

        val failure = assertFailsWith<UnexpectedBuildFailure> {
            runGate()
        }

        assertContains(failure.buildResult.output, "Metro authority exclusion")
    }

    private fun runGate() = GradleRunner.create()
        .withProjectDir(projectDirectory.toFile())
        .withPluginClasspath()
        .withArguments("enterpriseArchitectureCheck", "--stacktrace")
        .forwardOutput()
        .build()

    @Test
    fun libraryRootWithHistoricalServiceNameIsNotItsOwnDependency() {
        writeFixture(libraryConsumer())
        projectDirectory.resolve("settings.gradle.kts").writeText("rootProject.name = \"services-kms-rest\"\n")
        runGate()
    }

    @Test
    fun publishedLibraryRoleSurvivesModuleMetadataAndPermitsHistoricalServiceName() {
        writeRoleProducer("services-kms-rest", "library")
        runner(":services-kms-rest:publish").build()
        val metadata = projectDirectory.resolve("repo/fixture/services-kms-rest/1.0/services-kms-rest-1.0.module").readText()
        assertContains(metadata, "com.sphereon.module-role")
        assertContains(metadata, "library")
        projectDirectory.resolve("build.gradle.kts").writeText(
            libraryConsumer() + """

            repositories { maven { url = uri("repo") } }
            dependencies { implementation("fixture:services-kms-rest:1.0") }
            """.trimIndent(),
        )
        runGate()
        // Same task inputs and resolved metadata must be reusable with configuration cache.
        runner("enterpriseArchitectureCheck", "--configuration-cache").build()
        val reused = runner("enterpriseArchitectureCheck", "--configuration-cache").build()
        assertContains(reused.output, "Reusing configuration cache")
    }

    @Test
    fun signingAuxiliaryConfigurationsRemainEmptyWhilePublishedVariantsCarryRole() {
        writeRoleProducer("services-kms-rest", "library")
        val producer = projectDirectory.resolve("services-kms-rest/build.gradle.kts")
        producer.writeText(producer.readText() + """

            apply(plugin = "signing")
            extensions.configure<org.gradle.plugins.signing.SigningExtension> {
                sign(publishing.publications["library"])
            }
            afterEvaluate {
                check(configurations["archives"].attributes.keySet().isEmpty())
                check(configurations["signatures"].attributes.keySet().isEmpty())
                val role = org.gradle.api.attributes.Attribute.of("com.sphereon.module-role", String::class.java)
                check(configurations["apiElements"].attributes.getAttribute(role) == "library")
                check(configurations["runtimeElements"].attributes.getAttribute(role) == "library")
            }
        """.trimIndent())
        projectDirectory.resolve("build.gradle.kts").writeText(
            libraryConsumer() + "\ndependencies { implementation(project(\":services-kms-rest\")) }\n",
        )
        runner("enterpriseArchitectureCheck", ":services-kms-rest:generateMetadataFileForLibraryPublication", "--configuration-cache").build()
        val metadata = projectDirectory.resolve("services-kms-rest/build/publications/library/module.json").readText()
        assertContains(metadata, "com.sphereon.module-role")
        assertContains(metadata, "library")
        val reused = runner("enterpriseArchitectureCheck", ":services-kms-rest:generateMetadataFileForLibraryPublication", "--configuration-cache").build()
        assertContains(reused.output, "Reusing configuration cache")
    }

    @Test
    fun explicitDeployableRoleIsRejectedWithoutHistoricalServiceName() {
        writeRoleProducer("ordinary-name", "deployable")
        projectDirectory.resolve("build.gradle.kts").writeText(
            libraryConsumer() + "\ndependencies { implementation(project(\":ordinary-name\")) }\n",
        )
        val failure = assertFailsWith<UnexpectedBuildFailure> { runGate() }
        assertContains(failure.buildResult.output, "library module must not resolve deployable component")
    }

    @Test
    fun serviceAssemblyRoleIsRejectedForLibraryConsumer() {
        writeRoleProducer("ordinary-name", "service-assembly")
        projectDirectory.resolve("build.gradle.kts").writeText(
            libraryConsumer() + "\ndependencies { implementation(project(\":ordinary-name\")) }\n",
        )
        val failure = assertFailsWith<UnexpectedBuildFailure> { runGate() }
        assertContains(failure.buildResult.output, "library module must not resolve deployable component")
    }

    @Test
    fun legacyServiceWithoutRoleMetadataRemainsRejected() {
        writeFixture(
            libraryConsumer() + "\ndependencies { implementation(project(\":services-kms-rest\")) }\n",
            subproject = "services-kms-rest",
        )
        val failure = assertFailsWith<UnexpectedBuildFailure> { runGate() }
        assertContains(failure.buildResult.output, "library module must not resolve deployable component")
    }

    @Test
    fun unknownRoleCannotBypassLegacyServiceBoundary() {
        writeRoleProducer("services-kms-rest", "unknown-role")
        projectDirectory.resolve("build.gradle.kts").writeText(
            libraryConsumer() + "\ndependencies { implementation(project(\":services-kms-rest\")) }\n",
        )
        val failure = assertFailsWith<UnexpectedBuildFailure> { runGate() }
        assertContains(failure.buildResult.output, "library module must not resolve deployable component")
    }

    @Test
    fun applicationCannotDeclareLibraryRole() {
        writeFixture("""
            plugins {
                application
                id("com.sphereon.gradle.plugin.enterprise-architecture")
            }
            enterpriseArchitecture { moduleRole.set("library") }
        """.trimIndent())
        val failure = assertFailsWith<UnexpectedBuildFailure> { runGate() }
        assertContains(failure.buildResult.output, "Executable module must declare deployable or service-assembly role")
    }

    @Test
    fun serviceDeployableCannotDeclareLibraryRole() {
        writeFixture("""
            plugins {
                `java-library`
                id("com.sphereon.gradle.plugin.service-deployable")
            }
            enterpriseArchitecture { moduleRole.set("library") }
        """.trimIndent())
        val failure = assertFailsWith<UnexpectedBuildFailure> { runGate() }
        assertContains(failure.buildResult.output, "Executable module must declare deployable or service-assembly role")
    }

    private fun runner(vararg tasks: String) = GradleRunner.create()
        .withProjectDir(projectDirectory.toFile())
        .withPluginClasspath()
        .withArguments(*tasks, "--stacktrace")
        .forwardOutput()

    private fun writeRoleProducer(name: String, role: String) {
        writeFixture("", subproject = name)
        projectDirectory.resolve(name).resolve("build.gradle.kts").writeText("""
            plugins {
                `java-library`
                `maven-publish`
                id("com.sphereon.gradle.plugin.enterprise-architecture")
            }
            group = "fixture"
            version = "1.0"
            enterpriseArchitecture { moduleRole.set("$role") }
            publishing {
                publications { create<MavenPublication>("library") { from(components["java"]) } }
                repositories { maven { url = rootProject.uri("repo") } }
            }
        """.trimIndent())
    }

    private fun libraryConsumer() = """
        plugins {
            `java-library`
            id("com.sphereon.gradle.plugin.enterprise-architecture")
        }
        enterpriseArchitecture {
            moduleRole.set("library")
            runtimeRole.set("satellite-workload")
            capabilities.set(setOf(
                "workload-execution", "remote-authority-adapter",
                "runtime-persistence-postgresql", "tenant-database-route"
            ))
        }
    """.trimIndent()

    private fun writeFixture(buildScript: String, subproject: String? = null, source: String? = null) {
        projectDirectory.resolve("settings.gradle.kts").writeText(
            if (subproject == null) {
                "rootProject.name = \"architecture-fixture\"\n"
            } else {
                "rootProject.name = \"architecture-fixture\"\ninclude(\":$subproject\")\n"
            },
        )
        projectDirectory.resolve("build.gradle.kts").writeText(buildScript + "\n")
        if (subproject != null) {
            projectDirectory.resolve(subproject).createDirectories()
            projectDirectory.resolve(subproject).resolve("build.gradle.kts").writeText("plugins { id(\"java-library\") }\n")
        }
        if (source != null) {
            projectDirectory.resolve("src/main/kotlin/fixture").createDirectories()
            projectDirectory.resolve("src/main/kotlin/fixture/ForbiddenImport.kt").writeText(source + "\n")
        }
    }
}
