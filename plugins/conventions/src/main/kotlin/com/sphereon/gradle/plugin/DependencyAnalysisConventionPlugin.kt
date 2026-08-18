package com.sphereon.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Applies Autonomous Apps dependency analysis from the same build-support
 * classloader as the Kotlin Gradle plugin.
 *
 * Composite builds must apply this plugin only from the top-level build being
 * analyzed. Applying independent copies in included builds registers the same
 * global analysis services from incompatible classloaders.
 */
class DependencyAnalysisConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("com.autonomousapps.dependency-analysis")
    }
}
