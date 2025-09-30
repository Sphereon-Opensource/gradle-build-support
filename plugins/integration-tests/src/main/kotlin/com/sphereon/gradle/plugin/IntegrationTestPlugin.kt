package com.sphereon.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.JvmTestSuitePlugin
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.dependencies
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.testing.base.TestingExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File

private const val INTEGRATION_TEST = "IntegrationTest"
private const val VERIFICATION = "verification"
const val INTEGRATION = "integration"

@Suppress("UnstableApiUsage")
class IntegrationTestPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (isKotlinMultiplatformProject(project)) {
            configureMultiplatformCompilation(project)
        } else {
            setupTestSuite(project)
            setupTestTask(project)
            if (isKotlinJvmProject(project)) {
                configureKotlinJvmCompilation(project)
            }
        }
    }

    private fun setupTestSuite(project: Project) {
        if (!project.plugins.hasPlugin(JvmTestSuitePlugin::class.java)) {
            project.plugins.apply(JvmTestSuitePlugin::class.java)
        }
        val testing = project.extensions.getByType(TestingExtension::class.java)

        val maybeTestSuite = testing.suites.findByName("test")
        if (maybeTestSuite !is JvmTestSuite) {
            project.logger.lifecycle("Skipping test suite wiring: 'test' suite not found or not a JvmTestSuite")
        } else {
            maybeTestSuite.targets.all {
                this.testTask.configure {
                    this.enabled = true
                }
            }
        }

        testing.suites.register(INTEGRATION, JvmTestSuite::class.java) {
            this.targets.all {
                this.testTask.configure {
                    maybeTestSuite?.let { shouldRunAfter(it) }
                    enabled = true
                }
            }
            setupIntegrationSourceSet(project, this)
        }
    }

    private fun setupIntegrationSourceSet(project: Project, testSuite: JvmTestSuite) {
        val sourceSets = project.extensions.findByType(JavaPluginExtension::class.java)?.sourceSets ?: return
        val mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        val testSourceSet = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME)
        val integrationSourceSet = testSuite.sources

        project.configurations.getByName(integrationSourceSet.implementationConfigurationName)
            .extendsFrom(project.configurations.getByName(testSourceSet.implementationConfigurationName))

        project.configurations.getByName(integrationSourceSet.runtimeOnlyConfigurationName)
            .extendsFrom(project.configurations.getByName(testSourceSet.runtimeOnlyConfigurationName))

        project.configurations.getByName(integrationSourceSet.compileOnlyConfigurationName)
            .extendsFrom(project.configurations.getByName(testSourceSet.compileOnlyConfigurationName))

        project.configurations.getByName(integrationSourceSet.annotationProcessorConfigurationName)
            .extendsFrom(project.configurations.getByName(testSourceSet.annotationProcessorConfigurationName))

        integrationSourceSet.compileClasspath += testSourceSet.output + mainSourceSet.output
        integrationSourceSet.runtimeClasspath += testSourceSet.output + mainSourceSet.output
    }

    private fun setupTestTask(project: Project) {
        val integrationTestName = "jvm${INTEGRATION_TEST}"
        project.tasks.register(integrationTestName, DummyTestTask::class.java) {
            description = "Runs integration test suites."
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            enabled = true
            dependsOn(INTEGRATION)
        }
        project.tasks.named(JavaBasePlugin.CHECK_TASK_NAME) {
            dependsOn(integrationTestName)
            dependsOn(INTEGRATION)
        }
    }

    private fun configureMultiplatformCompilation(project: Project) {
        project.afterEvaluate {
            val kotlinExt = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return@afterEvaluate
            val jvmTarget = kotlinExt.targets.find { it.name == "jvm" } ?: return@afterEvaluate

            val testCompilation = jvmTarget.compilations.findByName(SourceSet.TEST_SOURCE_SET_NAME) ?: return@afterEvaluate

            val integrationTestCompilation = jvmTarget.compilations.maybeCreate(INTEGRATION_TEST)
            integrationTestCompilation.defaultSourceSet.kotlin.srcDir("src/jvmIntegrationTest/kotlin")
            integrationTestCompilation.defaultSourceSet.resources.srcDir("src/jvmIntegrationTest/resources")
            integrationTestCompilation.associateWith(testCompilation)

            // Add necessary dependencies to integrationTestCompilation
            project.dependencies {
                add(
                    integrationTestCompilation.defaultSourceSet.implementationConfigurationName,
                    getKotlinTestDependencyForTarget(jvmTarget.name)
                )
            }

            val integrationTestTask = project.tasks.register("jvmIntegrationTest", Test::class.java) {
                description = "Runs JVM integration tests"
                group = LifecycleBasePlugin.VERIFICATION_GROUP
                useJUnitPlatform()

                dependsOn("compileIntegrationTestKotlinJvm")

                val integrationTestClassesDir = project.layout.buildDirectory.dir("classes/kotlin/jvm/integrationTest")
                val mainTestClassesDir = project.layout.buildDirectory.dir("classes/kotlin/jvm/main")

                setTestClassesDirs(
                    project.files(
                        integrationTestClassesDir.map { it.asFile }
                    )
                )
                classpath = project.files(
                    integrationTestClassesDir.map { it.asFile },
                    mainTestClassesDir.map { it.asFile },
                    project.configurations.findByName("jvmIntegrationTestRuntimeClasspath")
                )

                testLogging {
                    events = setOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
                }
            }

            // Disabled, because we want to run IT/E2E tests explicitly instead of every time
            // Leaving it here, so another developer does not have the great Idea of adding the below lines ;-)
            /*project.tasks.named(JavaBasePlugin.CHECK_TASK_NAME) {
                dependsOn(integrationTestTask)
            }*/
        }
    }

    private fun configureKotlinJvmCompilation(project: Project) {
        val kotlinJvm = project.extensions.findByType(KotlinJvmProjectExtension::class.java) ?: return
        val test = kotlinJvm.target.compilations.findByName(SourceSet.TEST_SOURCE_SET_NAME) ?: return
        kotlinJvm.target.compilations.findByName(INTEGRATION)?.associateWith(test)
    }

    private fun isKotlinMultiplatformProject(project: Project): Boolean {
        return project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
    }

    private fun isKotlinJvmProject(project: Project): Boolean {
        return project.plugins.hasPlugin("org.jetbrains.kotlin.jvm")
    }
}

private fun getKotlinTestDependencyForTarget(targetName: String): String {
    return when (targetName) {
        "jvm" -> "org.jetbrains.kotlin:kotlin-test-junit5"
        "js" -> "org.jetbrains.kotlin:kotlin-test-js"
        "wasmJs" -> "org.jetbrains.kotlin:kotlin-test-wasm-js"
        else -> "org.jetbrains.kotlin:kotlin-test"
    }
}

private fun ensureTestSourceFolders(project: Project) {
    val kotlinExt = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return

    kotlinExt.targets.configureEach {
        val targetName = this.name.lowercase()
        listOf("IntegrationTest", "E2eTest").forEach { testType ->
            val dir = File(project.projectDir, "src/${targetName}${testType}/kotlin")
            if (!dir.exists()) {
                println("Creating missing test source directory: ${dir.path}")
                dir.mkdirs()
            }
        }
    }
}

/**
 * A dummy Test task to represent the integration test lifecycle.
 *
 * This task is intentionally left empty and is used only for:
 * - ensuring integration tests are properly wired into the check lifecycle
 * - satisfying Gradle validation rules for test tasks
 * - improving IDE integration visibility
 *
 * The actual integration tests are run by the dynamically registered test suites and tasks.
 */
abstract class DummyTestTask : Test() {
    override fun executeTests() {
        // deliberately empty
    }
}