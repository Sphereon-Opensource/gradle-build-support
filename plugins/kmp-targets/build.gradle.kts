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
    gradlePluginPortal()
    mavenCentral()
}

// Deliberately small: only the Gradle API. Settings scripts, the conventions plugin and the npm
// publication plugin all read kmp.targets through this artifact, so it must not drag Kotlin or AGP in.
dependencies {
    gradleApi()
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
}

tasks.test { useJUnitPlatform() }

gradlePlugin {
    plugins {
        create("kmpTargets") {
            id = "com.sphereon.gradle.plugin.kmp-targets"
            implementationClass = "com.sphereon.gradle.plugin.KmpTargetsSettingsPlugin"
        }
    }
}
