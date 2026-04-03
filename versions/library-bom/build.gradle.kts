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

        // Atomicfu (runtime library; Kotlin 2.3+ compiler handles transformations natively)
        api("org.jetbrains.kotlinx:atomicfu:0.31.0")

        // KotlinCrypto
        api("org.kotlincrypto.core:digest:0.8.0")
        api("org.kotlincrypto.hash:sha1:0.8.0")
        api("org.kotlincrypto.hash:sha2:0.8.0")
        api("org.kotlincrypto.hash:sha3:0.8.0")

        // WhyOleg Cryptography
        api("dev.whyoleg.cryptography:cryptography-core:0.6.0")
        api("dev.whyoleg.cryptography:cryptography-serialization-pem:0.6.0")
        api("dev.whyoleg.cryptography:cryptography-serialization-asn1:0.6.0")
        api("dev.whyoleg.cryptography:cryptography-serialization-asn1-modules:0.6.0")
        api("dev.whyoleg.cryptography:cryptography-random:0.6.0")
        api("dev.whyoleg.cryptography:cryptography-provider-optimal:0.6.0")
        api("dev.whyoleg.cryptography:cryptography-provider-cryptokit:0.6.0")
        api("dev.whyoleg.cryptography:cryptography-provider-webcrypto:0.6.0")
        api("dev.whyoleg.cryptography:cryptography-provider-jdk:0.6.0")
        api("dev.whyoleg.cryptography:cryptography-provider-openssl3-api:0.6.0")
        api("dev.whyoleg.cryptography:cryptography-provider-openssl3-shared:0.6.0")
        api("dev.whyoleg.cryptography:cryptography-provider-openssl3-prebuilt:0.6.0")

        // A-SIT Plus awesn1 (ASN.1 & structural PKI models)
        api("at.asitplus.awesn1:core:0.1.1")
        api("at.asitplus.awesn1:crypto:0.1.1")

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

        api("io.konform:konform:0.11.1")

        // DI
        api("me.tatarka.inject:kotlin-inject-compiler-ksp:0.9.0")
        api("me.tatarka.inject:kotlin-inject-runtime:0.9.0")
        api("me.tatarka.inject:kotlin-inject-runtime-kmp:0.9.0")
        api("software.amazon.lastmile.kotlin.inject.anvil:runtime:0.1.7")
        api("software.amazon.lastmile.kotlin.inject.anvil:runtime-optional:0.1.7")
        api("software.amazon.lastmile.kotlin.inject.anvil:compiler:0.1.7")
        api("software.amazon.app.platform:kotlin-inject-public:0.0.10-SNAPSHOT")
        api("software.amazon.app.platform:kotlin-inject-impl:0.0.10-SNAPSHOT")
        api("software.amazon.app.platform:kotlin-inject-contribute-public:0.0.10-SNAPSHOT")
        api("software.amazon.app.platform:scope-public:0.0.10-SNAPSHOT")
        api("software.amazon.app.platform:di-common-public:0.0.10-SNAPSHOT")
        api("software.amazon.app.platform:presenter-molecule-impl:0.0.10-SNAPSHOT")
        api("software.amazon.app.platform:presenter-molecule-public:0.0.10-SNAPSHOT")
        api("software.amazon.app.platform:renderer-compose-multiplatform-public:0.0.10-SNAPSHOT")
        api("software.amazon.app.platform:kotlin-inject-contribute-impl-code-generators:0.0.10-SNAPSHOT")
        api("com.willowtreeapps.assertk:assertk:0.28.1")


        api("com.michael-bull.kotlin-result:kotlin-result:2.1.0")


        // KTOR
        api("io.ktor:ktor-http:3.4.1")
        api("io.ktor:ktor-client-core:3.4.1")
        api("io.ktor:ktor-client-core-jvm:3.4.1")
        api("io.ktor:ktor-client-core-js:3.4.1")
        api("io.ktor:ktor-client-core-wasm-js:3.4.1")
        api("io.ktor:ktor-client-cio:3.4.1")
        api("io.ktor:ktor-client-cio-jvm:3.4.1")
        api("io.ktor:ktor-client-cio-js:3.4.1")
        api("io.ktor:ktor-client-cio-wasm-js:3.4.1")
        api("io.ktor:ktor-client-okhttp:3.4.1")
        api("io.ktor:ktor-client-okhttp-jvm:3.4.1")
        api("io.ktor:ktor-client-darwin:3.4.1")
        api("io.ktor:ktor-client-js:3.4.1")
        api("io.ktor:ktor-client-js-wasm-js:3.4.1")
        api("io.ktor:ktor-client-java:3.4.1")
        api("io.ktor:ktor-client-logging:3.4.1")
        api("io.ktor:ktor-client-mock:3.4.1")
        api("io.ktor:ktor-client-mock-js:3.4.1")
        api("io.ktor:ktor-client-auth:3.4.1")
        api("io.ktor:ktor-client-content-negotiation:3.4.1")
        api("io.ktor:ktor-client-serialization:3.4.1")
        api("io.ktor:ktor-io:3.4.1")
        api("io.ktor:ktor-server-core:3.4.1")
        api("io.ktor:ktor-server-cio:3.4.1")
        api("io.ktor:ktor-server-netty:3.4.1")
        api("io.ktor:ktor-server-jetty:3.4.1")
        api("io.ktor:ktor-server-content-negotiation:3.4.1")
        api("io.ktor:ktor-serialization-kotlinx-json:3.4.1")
        api("io.ktor:ktor-server-test-host:3.4.1")
        api("io.ktor:ktor-serialization-kotlinx-cbor:3.4.1")
        api("io.ktor:ktor-serialization-kotlinx-protobuf:3.4.1")
        api("io.ktor:ktor-server-auth:3.4.1")
        api("io.ktor:ktor-server-auth-jwt:3.4.1")
        api("io.ktor:ktor-server-status-pages:3.4.1")
        api("io.ktor:ktor-server-cors:3.4.1")
        api("io.ktor:ktor-server-call-logging:3.4.1")

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

        // Database
        api("app.cash.sqldelight:runtime:2.2.1")
        api("app.cash.sqldelight:coroutines-extensions:2.2.1")
        api("app.cash.sqldelight:jdbc-driver:2.2.1")
        api("app.cash.sqldelight:sqlite-driver:2.2.1")
        api("app.cash.sqldelight:native-driver:2.2.1")
        api("com.zaxxer:HikariCP:7.0.2")
        api("org.postgresql:postgresql:42.7.4")
        api("com.mysql:mysql-connector-j:9.2.0")
        api("org.xerial:sqlite-jdbc:3.47.1.0")

        // sqlx4k - Native PostgreSQL and MySQL drivers for Kotlin Native
        api("io.github.smyrgeorge:sqlx4k-postgres:0.71.0")
        api("io.github.smyrgeorge:sqlx4k-mysql:0.71.0")
        api("io.github.smyrgeorge:sqlx4k-sqldelight:0.71.0")


        // Keycloak SPI
        api("org.keycloak:keycloak-core:26.0.0")
        api("org.keycloak:keycloak-server-spi:26.0.0")
        api("org.keycloak:keycloak-server-spi-private:26.0.0")
        api("org.keycloak:keycloak-services:26.0.0")
        api("org.keycloak:keycloak-admin-client:26.0.0")

        // JBoss Logging (provided by Keycloak at runtime)
        api("org.jboss.logging:jboss-logging:3.6.1.Final")

        // Testing
        api("io.mockk:mockk:1.14.6")
        api("app.cash.turbine:turbine:1.2.0")
        api("org.junit.jupiter:junit-jupiter-engine:5.11.0")
        api("org.slf4j:slf4j-simple:2.0.16")

        // sqlx4k - Native PostgreSQL and MySQL drivers for Kotlin Native
        api("io.github.smyrgeorge:sqlx4k-postgres:0.71.0")
        api("io.github.smyrgeorge:sqlx4k-mysql:0.71.0")
        api("io.github.smyrgeorge:sqlx4k-sqldelight:0.71.0")

        // TestContainers
        api("org.testcontainers:testcontainers:1.21.0")
        api("org.testcontainers:postgresql:1.21.0")
        api("org.testcontainers:mysql:1.21.0")
        api("org.testcontainers:junit-jupiter:1.21.0")

        // Docker-java (for Testcontainers)
        api("com.github.docker-java:docker-java-core:3.4.1")
        api("com.github.docker-java:docker-java-transport-httpclient5:3.4.1")
        api("com.github.docker-java:docker-java-api:3.4.1")

        // Spring
        api("org.springframework.boot:spring-boot-starter-web:3.5.8")
        api("org.springframework.boot:spring-boot-starter-test:3.5.8")

        //Jakarta
        api("jakarta.validation:jakarta.validation-api:3.1.1")

        // Reactive
        api("org.reactivestreams:reactive-streams:1.0.4")
        api("io.projectreactor:reactor-core:3.7.11")

        // gRPC
        api("io.grpc:grpc-netty-shaded:1.70.0")
        api("io.grpc:grpc-protobuf:1.70.0")
        api("io.grpc:grpc-stub:1.70.0")
        api("io.grpc:grpc-kotlin-stub:1.4.1")
        api("io.grpc:grpc-services:1.70.0")
        api("io.grpc:grpc-api:1.70.0")
        api("com.google.protobuf:protobuf-kotlin:4.29.3")

        // XML / Trust
        api("io.github.pdvrieze.xmlutil:core:0.90.1")
        api("io.github.pdvrieze.xmlutil:serialization:0.90.1")

        // Crypto (Bouncy Castle)
        api("org.bouncycastle:bcprov-jdk18on:1.79")
        api("org.bouncycastle:bcpkix-jdk18on:1.79")
        api("org.bouncycastle:bcutil-jdk18on:1.79")

        // Utility
        api("io.github.g0dkar:qrcode-kotlin:4.5.0")

        // Code generation
        api("com.squareup:kotlinpoet:2.0.0")
        api("com.squareup:kotlinpoet-ksp:2.0.0")

        // Logging
        api("ch.qos.logback:logback-classic:1.5.16")

        // Email
        api("org.simplejavamail:simple-java-mail:8.12.6")

        // Template
        api("com.github.spullara.mustache.java:compiler:0.9.14")

        // AndroidX Test
        api("androidx.test:core:1.6.1")
        api("androidx.test:rules:1.6.1")

        // Compile Testing
        api("com.github.tschuchortdev:kotlin-compile-testing-ksp:1.6.0")
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
