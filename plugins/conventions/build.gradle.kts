plugins {
    `kotlin-dsl`
    idea
    `maven-publish`
    alias(libs.plugins.vanniktech.mavenPublish)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
allprojects {
    group = "com.sphereon.gradle.plugin"
}

repositories {
    gradlePluginPortal()   // for kotlin-dsl & java-gradle-plugin
    mavenCentral()
    google()
    // Keep maven local at the end!!!!
    // https://slack-chats.kotlinlang.org/t/27045384/hi-there-i-have-a-very-annoying-internal-compiler-error-here
    mavenLocal {
        content {
            includeGroupAndSubgroups("com.sphereon")
        }
    }
}


dependencies {
    gradleApi()
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.dependency.analysis.gradlePlugin)
    implementation(libs.nexus.publish)
    api("com.vanniktech.maven.publish:com.vanniktech.maven.publish.gradle.plugin:0.31.0")
}

gradlePlugin {
    plugins {
        create("conventions") {
            id = "com.sphereon.gradle.plugin.conventions"
            implementationClass = "com.sphereon.gradle.plugin.ConventionsPlugin"
        }
        create("serviceDeployable") {
            id = "com.sphereon.gradle.plugin.service-deployable"
            implementationClass = "com.sphereon.gradle.plugin.ServiceDeployablePlugin"
        }
        create("dependencyAnalysis") {
            id = "com.sphereon.gradle.plugin.dependency-analysis"
            implementationClass = "com.sphereon.gradle.plugin.DependencyAnalysisConventionPlugin"
        }
    }
}
