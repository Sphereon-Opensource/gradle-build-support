package com.sphereon.gradle.plugin

import org.gradle.api.Project

/**
 * Records the third-party versions that shipped runtime classpaths resolve in a `gradle.lockfile`
 * per module, so a release can be rebuilt with the same libraries and a transitive version change
 * shows up as a reviewed diff.
 *
 * Only runtime classpaths of production code are locked: those are what a service or published
 * library ships. Compile, test and tooling classpaths are not.
 *
 * Sphereon modules are left out of the lock state. A workspace that builds a product from source
 * substitutes them with projects while a standalone build resolves them from Maven, so recording
 * them would make the same lockfile disagree between the two modes; their versions follow the
 * product versions instead.
 *
 * Gradle ignores a missing lockfile in its default lock mode, so enabling locking changes nothing
 * for a module until its lockfile is written with `--write-locks`. `sphereon.dependencyLocking=off`
 * disables it.
 */
object GbsDependencyLocking {
    const val PROPERTY = "sphereon.dependencyLocking"
    val IGNORED = listOf("com.sphereon*:*")

    /** True for a resolvable runtime classpath of production code, such as `jvmRuntimeClasspath`. */
    fun isLocked(configurationName: String): Boolean =
        configurationName.endsWith("RuntimeClasspath", ignoreCase = true) &&
            !configurationName.contains("Test", ignoreCase = true)
}

fun Project.configureGbsDependencyLocking() {
    if (providers.gradleProperty(GbsDependencyLocking.PROPERTY).orNull == "off") return
    dependencyLocking {
        ignoredDependencies.addAll(GbsDependencyLocking.IGNORED)
    }
    configurations.configureEach {
        if (isCanBeResolved && GbsDependencyLocking.isLocked(name)) {
            resolutionStrategy.activateDependencyLocking()
        }
    }
}
