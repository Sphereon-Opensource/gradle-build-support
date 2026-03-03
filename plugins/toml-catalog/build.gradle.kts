plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())
}

gradlePlugin {
    plugins {
        create("tomlCatalog") {
            id = "com.sphereon.gradle.toml-catalog"
            implementationClass = "com.sphereon.gradle.buildsupport.TomlCatalogPlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            name = "sphereon-opensource"
            val snapshotsUrl = "https://nexus.sphereon.com/repository/sphereon-opensource-snapshots/"
            val releasesUrl = "https://nexus.sphereon.com/repository/sphereon-opensource-releases/"
            url = uri(if (version.toString().contains("SNAPSHOT")) snapshotsUrl else releasesUrl)
            credentials {
                username = System.getenv("NEXUS_USERNAME")
                password = System.getenv("NEXUS_PASSWORD")
            }
        }
    }
}
