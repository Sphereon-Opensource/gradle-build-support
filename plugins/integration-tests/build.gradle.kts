plugins {
    `kotlin-dsl`
    idea
    `maven-publish`
    alias(libs.plugins.vanniktech.mavenPublish)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
allprojects {
    group = "com.sphereon.gradle.plugin"
    version = "$version"
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
    implementation(libs.nexus.publish)
    api("com.vanniktech.maven.publish:com.vanniktech.maven.publish.gradle.plugin:0.31.0")
}

gradlePlugin {
    plugins {
        create("integration-tests") {
            id = "com.sphereon.gradle.plugin.integration-tests"
            implementationClass = "com.sphereon.gradle.plugin.IntegrationTestPlugin"
        }
    }
}
