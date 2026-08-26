package com.sphereon.gradle.plugin

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
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
