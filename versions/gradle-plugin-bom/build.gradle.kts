// versions/common/build.gradle.kts
plugins {
    `java-platform`
    `maven-publish`
    id("com.sphereon.gradle.toml-catalog")
//    alias(libs.plugins.vanniktech.mavenPublish)
}

javaPlatform { allowDependencies() }

dependencies {
    constraints {
        // GRADLE PLUGINS

        // Sphereon
        api("com.sphereon.gradle.plugin.conventions:${version}")
        api("com.sphereon.gradle.plugin.integration-tests:${version}")
        api("com.sphereon.gradle.plugin.project-publication:${version}")

        // Kotlin
        api("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20")
        api("org.jetbrains.kotlin.jvm:2.2.20")
        api("org.jetbrains.kotlin.multiplatform:2.2.20")
        api("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20")
        api("org.jetbrains.kotlin.plugin.serialization:2.2.20")
        api("org.jetbrains.kotlin:kotlin-android-extensions:2.2.20")
        api("org.jetbrains.kotlin.android:2.2.20")
        api("org.jetbrains.kotlin.plugin.compose:2.2.20")

        // Android
        api("com.android.tools.build:gradle:8.12.0")
        api("com.android.application:8.12.0")
        api("com.android.library:8.12.0")
        api("com.android.kotlin.multiplatform.library:8.12.0")

        // Compose
        api("org.jetbrains.compose.hot-reload:1.0.0-beta08")
        api("org.jetbrains.compose:1.9.0")

        // Kotest
        api("io.kotest.multiplatform:io.kotest.multiplatform.gradle.plugin:5.9.1")

        // Publishing
        api("com.vanniktech.maven.publish:0.31.0")

        // KSP
        api("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.2.20-2.0.3")

        // DI
        api("software.amazon.app.platform:0.0.9-SNAPSHOT")
        api("org.jetbrains.kotlinx.atomicfu:0.27.0")

        // NPM Publish
        api("dev.petuska.npm.publish:dev.petuska.npm.publish.gradle.plugin:3.5.3")

        // BuildKonfig
        api("com.codingfeline.buildkonfig:0.17.0")

        // Nexus Publish
        api("io.github.gradle-nexus.publish-plugin:io.github.gradle-nexus.publish-plugin.gradle.plugin:2.0.0")

        // OpenAPI
        api("org.openapi.generator:7.13.0")
    }
}

// Ensure the generateTomlCatalog task runs before the publish task
tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn(tasks.named("generateTomlCatalog"))
}

tasks.withType<PublishToMavenLocal>().configureEach {
    dependsOn(tasks.named("generateTomlCatalog"))
}
/*

publishing {
    publications {
        create<MavenPublication>("bom") {
            from(components["javaPlatform"])
            artifactId = "gradle-plugin-bom"
        }
    }
}
*/
