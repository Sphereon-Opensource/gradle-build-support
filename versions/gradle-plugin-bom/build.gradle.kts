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

        // Sphereon plugins — version must match the published gradle-build-support version
        val gbsVersion = project.version.toString().also {
            require(it != "unspecified" && it.isNotBlank()) {
                "gradle-build-support project.version is not set — cannot generate plugin BOM with correct versions"
            }
        }
        api("com.sphereon.gradle.plugin.conventions:$gbsVersion")
        api("com.sphereon.gradle.plugin.integration-tests:$gbsVersion")
        api("com.sphereon.gradle.plugin.project-publication:$gbsVersion")
        api("com.sphereon.gradle.plugin.npm-publication:$gbsVersion")

        // Kotlin
        api("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
        api("org.jetbrains.kotlin.jvm:2.3.20")
        api("org.jetbrains.kotlin.multiplatform:2.3.20")
        api("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
        api("org.jetbrains.kotlin.plugin.serialization:2.3.20")
        api("org.jetbrains.kotlin.android:2.3.20")
        api("org.jetbrains.kotlin.plugin.compose:2.3.20")

        // Android
        api("com.android.tools.build:gradle:9.0.1")
        api("com.android.application:9.0.1")
        api("com.android.library:9.0.1")
        api("com.android.kotlin.multiplatform.library:9.0.1")

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
        api("org.jetbrains.kotlinx.atomicfu:0.31.0")

        // NPM Publish (JetBrains fork, supports wasmJs)
        api("org.jetbrains.kotlin.npm-publish:org.jetbrains.kotlin.npm-publish.gradle.plugin:3.6.0")

        // BuildKonfig
        api("com.codingfeline.buildkonfig:0.17.0")

        // Nexus Publish
        api("io.github.gradle-nexus.publish-plugin:io.github.gradle-nexus.publish-plugin.gradle.plugin:2.0.0")

        // OpenAPI
        api("org.openapi.generator:7.16.0")

        // Spring boot
        api("org.springframework.boot:3.5.8")
        api("io.spring.dependency-management:1.1.7")

        // Ktor
        api("io.ktor.plugin:3.3.3")
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
