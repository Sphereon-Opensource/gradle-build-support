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
    mavenLocal()
    gradlePluginPortal()   // for kotlin-dsl & java-gradle-plugin
    mavenCentral()
    google()
}


dependencies {
    gradleApi()
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.nexus.publish)
    api("com.vanniktech.maven.publish:com.vanniktech.maven.publish.gradle.plugin:0.31.0")
}

gradlePlugin {
    plugins {
        create("project-publication") {
            id = "com.sphereon.gradle.plugin.project-publication"
            implementationClass = "com.sphereon.gradle.plugin.ProjectPublicationPlugin"
        }
    }
}
