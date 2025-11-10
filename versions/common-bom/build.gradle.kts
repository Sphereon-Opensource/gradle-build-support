// versions/common/build.gradle.kts
plugins {
    `java-platform`
    `maven-publish`
    id("com.sphereon.gradle.toml-catalog")
}

javaPlatform { allowDependencies() }

dependencies {
    constraints {

        api(platform(project(":versions:gradle-plugin-bom")))

        // Kotlin
        api("org.jetbrains.kotlin:kotlin-test:2.2.21")
        api("org.jetbrains.kotlin:kotlin-test-junit:2.2.21")
        api("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
        api("org.jetbrains.kotlin:kotlin-reflect:2.2.21")


        // Kotlinx
        api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
        api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        api("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.9.0")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.10.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core-linuxx64:1.10.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core-iosx64:1.10.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core-iosarm64:1.10.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core-iossimulatorarm64:1.10.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core-wasm-js:1.10.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core-wasm-wasi:1.10.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.10.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-test-js:1.10.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-test-wasm-js:1.10.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-test-wasm-wasi:1.10.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
        api("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

        api("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1-0.6.x-compat")
        api("org.jetbrains.kotlinx:kotlinx-io-core:0.8.0")




        api("org.slf4j:slf4j-simple:2.0.17")

        // JUnit
        api("org.junit.jupiter:junit-jupiter-engine:5.13.4")

        // Kotest
        api("io.kotest:kotest-assertions-core:6.0.4")
        api("io.kotest:kotest-framework-engine:6.0.4")
        api("io.kotest:kotest-runner-junit5:6.0.4")
        api("io.kotest:kotest-property:6.0.4")

            // assertk
        api("com.willowtreeapps.assertk:assertk:0.28.1")


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
        create<MavenPublication>("bomCatalog") {
            //  only one publication
            groupId    = group.toString()
            artifactId = project.name
            version    = version.toString()

            // POM-only “platform” BOM:
            pom { packaging = "pom" }

            // attach the two TOML files as classifier artifacts
            artifact("build/tomlCatalog/sureCommonBom.versioned.toml") {
                builtBy(tasks.named("generateTomlCatalog"))
                extension  = "toml"
                classifier = "catalog-versioned"
            }
            artifact("build/tomlCatalog/sureCommonBom.toml") {
                builtBy(tasks.named("generateTomlCatalog"))
                extension  = "toml"
                classifier = "catalog-bom"
            }
        }
    }
}*/
