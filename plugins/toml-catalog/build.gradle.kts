plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
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
