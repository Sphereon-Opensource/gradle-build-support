plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("com.vanniktech.maven.publish") version "0.31.0"
    `maven-publish`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

allprojects {
    group = "$group"
    version = "$version"
}

gradlePlugin {
    plugins {
        create("nexusPublishing") {
            // the ID you'll use in other builds: plugins { id("com.sphereon.gradle.nexus-publishing") }
            id = "com.sphereon.gradle.nexus-publishing"
            group = "com.sphereon.gradle"
            // your fully-qualified plugin class
            implementationClass = "com.sphereon.gradle.buildsupport.NexusPublishingPlugin"
        }
    }
}


dependencies {
    implementation(gradleApi())
}
