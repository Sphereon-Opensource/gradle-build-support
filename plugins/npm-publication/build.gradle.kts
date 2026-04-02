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
    implementation("org.jetbrains.kotlin:npm-publish-gradle-plugin:3.6.0")
}

gradlePlugin {
    plugins {
        create("npm-publication") {
            id = "com.sphereon.gradle.plugin.npm-publication"
            implementationClass = "com.sphereon.gradle.plugin.NpmPublicationPlugin"
        }
    }
}
