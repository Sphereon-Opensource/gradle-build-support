rootProject.name = "gradle-build-support"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("plugins/toml-catalog")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://nexus.sphereon.com/repository/sphereon-opensource-releases")
            mavenContent { releasesOnly() }
            content {
                includeGroupAndSubgroups("com.sphereon")
                includeGroupAndSubgroups("software.amazon")
            }
        }
        maven {
            url = uri("https://nexus.sphereon.com/repository/sphereon-opensource-snapshots")
            mavenContent { snapshotsOnly() }
            content {
                includeGroupAndSubgroups("com.sphereon")
                includeGroupAndSubgroups("software.amazon")
            }
        }
        // Keep maven local at the end!!!!
        // https://slack-chats.kotlinlang.org/t/27045384/hi-there-i-have-a-very-annoying-internal-compiler-error-here
        mavenLocal {
            content {
                includeGroupAndSubgroups("com.sphereon")
            }
        }
    }

}

dependencyResolutionManagement {
    repositories {

        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
            mavenContent { snapshotsOnly() }
        }
        maven {
            url = uri("https://aws.oss.sonatype.org/content/repositories/snapshots/")
            mavenContent { snapshotsOnly() }
            content { includeGroupAndSubgroups("software.amazon") }
        }
        maven {
            url = uri("https://nexus.sphereon.com/repository/sphereon-opensource-releases")
            mavenContent { releasesOnly() }
            content {
                includeGroupAndSubgroups("com.sphereon")
                includeGroupAndSubgroups("software.amazon")
            }
        }
        maven {
            url = uri("https://nexus.sphereon.com/repository/sphereon-opensource-snapshots")
            mavenContent { snapshotsOnly() }
            content {
                includeGroupAndSubgroups("com.sphereon")
                includeGroupAndSubgroups("software.amazon")
            }
        }
        // Keep maven local at the end!!!!
        // https://slack-chats.kotlinlang.org/t/27045384/hi-there-i-have-a-very-annoying-internal-compiler-error-here
        mavenLocal {
            content {
                includeGroupAndSubgroups("com.sphereon")
            }
        }
    }
}

val selectedModules = System.getenv("WORKSPACE_SOURCE_MODULES")
    ?.split(',')?.filter { it.isNotBlank() }?.toSet()
val modulePaths = listOf(
    ":versions:common-bom",
    ":versions:gradle-plugin-bom",
    ":versions:library-bom",
    ":plugins:kmp-targets",
    ":plugins:conventions",
    ":plugins:project-publication",
    ":plugins:npm-publication",
    ":plugins:integration-tests",
)
if (selectedModules != null) {
    require(selectedModules.isNotEmpty() && modulePaths.map { it.substringAfterLast(':') }.containsAll(selectedModules)) {
        "WORKSPACE_SOURCE_MODULES must select existing build-support modules"
    }
}
modulePaths.filter { selectedModules == null || it.substringAfterLast(':') in selectedModules }
    .forEach { include(it) }

