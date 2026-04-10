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

        // Sphereon plugins — use ${version} so the TOML generator resolves it to the BOM version.
        // Do NOT use local variables like $gbsVersion — the generator parses this file as text
        // and cannot evaluate Gradle expressions.
        api("com.sphereon.gradle.plugin.conventions:${version}")
        api("com.sphereon.gradle.plugin.integration-tests:${version}")
        api("com.sphereon.gradle.plugin.project-publication:${version}")
        api("com.sphereon.gradle.plugin.npm-publication:${version}")

        // Kotlin
        api("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21-RC")
        api("org.jetbrains.kotlin.jvm:2.3.21-RC")
        api("org.jetbrains.kotlin.multiplatform:2.3.21-RC")
        api("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21-RC")
        api("org.jetbrains.kotlin.plugin.serialization:2.3.21-RC")
        api("org.jetbrains.kotlin.android:2.3.21-RC")
        api("org.jetbrains.kotlin.plugin.compose:2.3.21-RC")

        // Android
        api("com.android.tools.build:gradle:9.1.0")
        api("com.android.application:9.1.0")
        api("com.android.library:9.1.0")
        api("com.android.kotlin.multiplatform.library:9.1.0")

        // Compose
        api("org.jetbrains.compose.hot-reload:1.0.0-beta08")
        api("org.jetbrains.compose:1.9.2")

        // Kotest
        api("io.kotest:io.kotest.gradle.plugin:6.0.4")

        // Publishing
        api("com.vanniktech.maven.publish:0.31.0")

        // KSP
        api("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.6")

        // DI
        api("software.amazon.app.platform:0.0.10-SNAPSHOT")
        api("org.jetbrains.kotlinx.atomicfu:0.32.1")

        // Code Coverage
        api("org.jetbrains.kotlinx.kover:0.9.4")

        // NPM Publish (JetBrains fork, supports wasmJs)
        api("org.jetbrains.kotlin.npm-publish:org.jetbrains.kotlin.npm-publish.gradle.plugin:3.6.0")

        // BuildKonfig
        api("com.codingfeline.buildkonfig:0.17.0")

        // Nexus Publish
        api("io.github.gradle-nexus.publish-plugin:io.github.gradle-nexus.publish-plugin.gradle.plugin:2.0.0")

        // OpenAPI
        api("org.openapi.generator:7.16.0")

        // Spring boot
        api("org.springframework.boot:3.5.13")
        api("io.spring.dependency-management:1.1.7")

        // Ktor
        api("io.ktor.plugin:3.4.2")

        // Formatting
        api("org.jlleitschuh.gradle.ktlint:14.2.0")
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
