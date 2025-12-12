// versions/common/build.gradle.kts
plugins {
    `java-platform`
    `maven-publish`
    id("com.sphereon.gradle.toml-catalog")
    `version-catalog`
//    alias(libs.plugins.vanniktech.mavenPublish)
}

javaPlatform { allowDependencies() }

dependencies {
    constraints {
        api(platform(project(":versions:common-bom")))

        // KotlinCrypto
        api("org.kotlincrypto.core:digest:0.8.0")
        api("org.kotlincrypto.hash:sha1:0.8.0")
        api("org.kotlincrypto.hash:sha2:0.8.0")
        api("org.kotlincrypto.hash:sha3:0.8.0")

        // WhyOleg Cryptography
        api("dev.whyoleg.cryptography:cryptography-core:0.5.0")
        api("dev.whyoleg.cryptography:cryptography-serialization-pem:0.5.0")
        api("dev.whyoleg.cryptography:cryptography-serialization-asn1:0.5.0")
        api("dev.whyoleg.cryptography:cryptography-serialization-asn1-modules:0.5.0")
        api("dev.whyoleg.cryptography:cryptography-random:0.5.0")
        api("dev.whyoleg.cryptography:cryptography-provider-optimal:0.5.0")
        api("dev.whyoleg.cryptography:cryptography-provider-cryptokit:0.5.0")
        api("dev.whyoleg.cryptography:cryptography-provider-webcrypto:0.5.0")
        api("dev.whyoleg.cryptography:cryptography-provider-jdk:0.5.0")
        api("dev.whyoleg.cryptography:cryptography-provider-openssl3-api:0.5.0")
        api("dev.whyoleg.cryptography:cryptography-provider-openssl3-shared:0.5.0")
        api("dev.whyoleg.cryptography:cryptography-provider-openssl3-prebuilt:0.5.0")


        // A-sit plus signum
        api("at.asitplus.signum:indispensable:3.16.3")
        api("at.asitplus.signum:indispensable-asn1:3.16.3")
        api("at.asitplus.signum:indispensable-josef:3.16.3")
        api("at.asitplus.signum:indispensable-cosef:3.16.3")
        api("at.asitplus.signum:supreme:0.8.3")

        // Android
        api("androidx.startup:startup-runtime:1.2.0")
        api("androidx.datastore:datastore-preferences-core:1.1.7")

        // Other libraries
        api("co.touchlab:kermit:2.0.5")
        api("io.matthewnelson.encoding:base64:2.4.0")
        api("com.mayakapps.kache:kache:2.1.1")
        api("com.mayakapps.kache:file-kache:2.1.1")
        api("com.russhwolf:multiplatform-settings:1.3.0")
        api("com.russhwolf:multiplatform-settings-datastore:1.3.0")
        api("com.russhwolf:multiplatform-settings-coroutines:1.3.0")

        // DI
        api("me.tatarka.inject:kotlin-inject-compiler-ksp:0.8.1-SNAPSHOT")
        api("me.tatarka.inject:kotlin-inject-runtime:0.8.1-SNAPSHOT")
        api("me.tatarka.inject:kotlin-inject-runtime-kmp:0.8.1-SNAPSHOT")
        api("software.amazon.lastmile.kotlin.inject.anvil:runtime:0.1.6")
        api("software.amazon.lastmile.kotlin.inject.anvil:runtime-optional:0.1.6")
        api("software.amazon.lastmile.kotlin.inject.anvil:compiler:0.1.6")
        api("software.amazon.app.platform:kotlin-inject-contribute-public:0.0.9-SNAPSHOT")
        api("software.amazon.app.platform:kotlin-inject-contribute-impl-code-generators:0.0.9-SNAPSHOT")
        api("com.willowtreeapps.assertk:assertk:0.28.1")


        api("com.michael-bull.kotlin-result:kotlin-result:2.0.1")


        // KTOR
        api("io.ktor:ktor-client-core:3.3.0")
        api("io.ktor:ktor-client-core-jvm:3.3.0")
        api("io.ktor:ktor-client-core-js:3.3.0")
        api("io.ktor:ktor-client-core-wasm-js:3.3.0")
        api("io.ktor:ktor-client-cio:3.3.0")
        api("io.ktor:ktor-client-cio-jvm:3.3.0")
        api("io.ktor:ktor-client-cio-js:3.3.0")
        api("io.ktor:ktor-client-cio-wasm-js:3.3.0")
        api("io.ktor:ktor-client-okhttp:3.3.0")
        api("io.ktor:ktor-client-okhttp-jvm:3.3.0")
        api("io.ktor:ktor-client-darwin:3.3.0")
        api("io.ktor:ktor-client-js:3.3.0")
        api("io.ktor:ktor-client-java:3.3.0")
        api("io.ktor:ktor-client-logging:3.3.0")
        api("io.ktor:ktor-client-mock:3.3.0")
        api("io.ktor:ktor-client-mock-js:3.3.0")
        api("io.ktor:ktor-client-auth:3.3.0")
        api("io.ktor:ktor-client-content-negotiation:3.3.0")
        api("io.ktor:ktor-client-serialization:3.3.0")
        api("io.ktor:ktor-io:3.3.0")
        api("io.ktor:ktor-server-core:3.3.0")
        api("io.ktor:ktor-server-netty:3.3.0")
        api("io.ktor:ktor-server-jetty:3.3.0")
        api("io.ktor:ktor-serialization-kotlinx-json:3.3.0")
        api("io.ktor:ktor-server-test-host:3.3.0")
        api("io.ktor:ktor-serialization-kotlinx-cbor:3.3.0")
        api("io.ktor:ktor-serialization-kotlinx-protobuf:3.3.0")
        api("io.ktor:ktor-server-test-host:3.3.0")

        // Azure
        api("com.azure:azure-sdk-bom:1.3.0")
        api("com.azure:azure-identity:1.18.1")
        api("com.azure:azure-security-keyvault-administration:4.7.3")
        api("com.azure:azure-security-keyvault-certificates:4.8.3")
        api("com.azure:azure-security-keyvault-keys:4.10.3")


        // Apps, todo move to separate bom
        api("androidx.core:core-ktx:1.16.0")
        api("androidx.test.ext:junit:1.2.1")
        api("androidx.test.espresso:espresso-core:3.6.1")
        api("androidx.appcompat:appcompat:1.7.1")
        api("androidx.constraintlayout:constraintlayout:2.2.1")
        api("androidx.activity:activity-compose:1.10.1")
        api("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.9.1")
        api("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.1")

        // Spring
        api("org.springframework.boot:spring-boot-starter-web:3.5.6")
        api("org.springframework.boot:spring-boot-starter-test:3.5.6")

        //Jakarta
        api("jakarta.validation:jakarta.validation-api:3.1.1")

        // Reactive
        api("org.reactivestreams:reactive-streams:1.0.4")
        api("io.projectreactor:reactor-core:3.7.11")
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
catalog {
    versionCatalog {
        from(files("build/tomlCatalog/sureCommonBom.toml"))
    }
}*/
/*

publishing {
    publications {
        create<MavenPublication>("bom") {
            from(components["javaPlatform"])
            artifactId = "library-bom"
        }
    }
}
*/
