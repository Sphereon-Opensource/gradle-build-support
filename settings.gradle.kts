rootProject.name = "gradle-build-support"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
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
        }
        maven {
            url = uri("https://nexus.sphereon.com/repository/sphereon-opensource-snapshots")
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
        }
        maven {
            url = uri("https://aws.oss.sonatype.org/content/repositories/snapshots/")
        }
        maven {
            url = uri("https://nexus.sphereon.com/repository/sphereon-opensource-releases")
        }
        maven {
            url = uri("https://nexus.sphereon.com/repository/sphereon-opensource-snapshots")
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

include(":versions:common-bom")
include(":versions:gradle-plugin-bom")
include(":versions:library-bom")



include(":plugins:conventions")
include(":plugins:project-publication")
include(":plugins:integration-tests")

